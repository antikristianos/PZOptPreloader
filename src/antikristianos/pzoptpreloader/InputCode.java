package antikristianos.pzoptpreloader;

import java.util.*;
import net.bytebuddy.jar.asm.*;

/** In-memory view of code read from installed inputs. No input instruction/constant is serialized here. */
public final class InputCode {
    public final byte[] bytes;
    public String name, signature, parent;
    public String[] interfaces;
    public int version, access;
    public final List<Field> fields = new ArrayList<>();
    public final Map<String,Method> methods = new LinkedHashMap<>();
    public final List<String> nestMembers = new ArrayList<>();
    public final List<Object[]> innerClasses = new ArrayList<>();
    public String nestHost;
    public record Field(int access, String name, String descriptor, String signature, Object value) { }
    public record Handler(int start, int end, int target, String type) { }
    public static final class Method {
        public final int access;
        public final String name, descriptor, signature;
        public final String[] exceptions;
        public boolean code;
        public int maxStack, maxLocals;
        public final List<Instruction> instructions = new ArrayList<>();
        public final List<Handler> handlers = new ArrayList<>();
        private final Map<Label,Integer> labels = new IdentityHashMap<>();
        private final List<Object[]> unresolvedHandlers = new ArrayList<>();
        Method(int access, String name, String descriptor, String signature, String[] exceptions) {
            this.access=access; this.name=name; this.descriptor=descriptor; this.signature=signature;
            this.exceptions=exceptions == null ? new String[0] : exceptions;
        }
        public String key() { return name+descriptor; }
        private int position(Label label) {
            Integer value=labels.get(label);
            if (value == null) throw new IllegalStateException("Unresolved input label in "+key());
            return value;
        }
        private int[] positions(Label[] values) { return Arrays.stream(values).mapToInt(this::position).toArray(); }
        private void finish() {
            for (Instruction instruction:instructions) {
                Object[] a=instruction.arguments;
                switch (instruction.kind) {
                    case "jump" -> a[0]=position((Label)a[0]);
                    case "table" -> { a[2]=position((Label)a[2]); a[3]=positions((Label[])a[3]); }
                    case "lookup" -> { a[0]=position((Label)a[0]); a[2]=positions((Label[])a[2]); }
                    default -> { }
                }
            }
            for (Object[] h:unresolvedHandlers) handlers.add(new Handler(position((Label)h[0]),position((Label)h[1]),position((Label)h[2]),(String)h[3]));
        }
    }
    public static final class Instruction {
        public final String kind;
        public final int opcode;
        public final Object[] arguments;
        Instruction(String kind,int opcode,Object... arguments) { this.kind=kind; this.opcode=opcode; this.arguments=arguments; }
        private static String symbol(String value,boolean relaxed) {
            if (!relaxed) return value;
            return value.replace("zombie/util/list/PZArrayList","java/util/List")
                .replace("java/util/ArrayList","java/util/List")
                .replace("zombie/entity/util/TimSort","antikristianos/pzoptpreloader/LegacySort")
                .replaceAll("(lambda\\$[^()]*?)\\$[0-9]+(?=\\(|$)","$1");
        }
        private static String valueKey(Object value,boolean relaxed) {
            if (value instanceof String text) return "string:"+Compatibility.hash(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (value instanceof Type type) return "type:"+symbol(type.getDescriptor(),relaxed);
            if (value instanceof Handle handle) return "handle:"+handle.getTag()+":"+symbol(handle.getOwner(),relaxed)+":"
                +symbol(handle.getName()+handle.getDesc(),relaxed)+":"+handle.isInterface();
            return value == null ? "null" : value.getClass().getName()+":"+value;
        }
        /** Structural lookup only; branch targets and local slots are supplied by the own edit plan. */
        public String key(boolean relaxed) {
            String prefix=kind+":"+opcode+":";
            return prefix+switch(kind) {
                case "op","var","jump","table","lookup" -> "";
                case "int" -> relaxed ? "integer" : arguments[0].toString();
                case "iinc" -> relaxed ? "increment" : arguments[1].toString();
                case "type" -> symbol((String)arguments[0],relaxed);
                case "field","method" -> symbol((String)arguments[0],relaxed)+":"+symbol((String)arguments[1]+arguments[2],relaxed)
                    +(kind.equals("method")?":"+arguments[3]:"");
                case "ldc" -> relaxed && arguments[0] instanceof Number n ? "number:"+n.getClass().getName() : valueKey(arguments[0],relaxed);
                case "multi" -> symbol((String)arguments[0],relaxed)+":"+arguments[1];
                case "indy" -> {
                    Handle bootstrap=(Handle)arguments[2];
                    StringBuilder key=new StringBuilder(arguments[0]+":"+bootstrap.getOwner()+":"+bootstrap.getName());
                    if (!relaxed) key.append(':').append(arguments[1]);
                    for(Object value:(Object[])arguments[3]) if(value instanceof String) key.append(':').append(valueKey(value,false));
                    yield key.toString();
                }
                default -> throw new IllegalArgumentException(kind);
            };
        }
        public void emit(MethodVisitor visitor,Label[] labels) {
            Object[] a=arguments;
            switch(kind) {
                case "op" -> visitor.visitInsn(opcode);
                case "int" -> visitor.visitIntInsn(opcode,(Integer)a[0]);
                case "var" -> visitor.visitVarInsn(opcode,(Integer)a[0]);
                case "type" -> visitor.visitTypeInsn(opcode,(String)a[0]);
                case "field" -> visitor.visitFieldInsn(opcode,(String)a[0],(String)a[1],(String)a[2]);
                case "method" -> visitor.visitMethodInsn(opcode,(String)a[0],(String)a[1],(String)a[2],(Boolean)a[3]);
                case "ldc" -> visitor.visitLdcInsn(a[0]);
                case "iinc" -> visitor.visitIincInsn((Integer)a[0],(Integer)a[1]);
                case "jump" -> visitor.visitJumpInsn(opcode,labels[(Integer)a[0]]);
                case "table" -> visitor.visitTableSwitchInsn((Integer)a[0],(Integer)a[1],labels[(Integer)a[2]],Arrays.stream((int[])a[3]).mapToObj(i->labels[i]).toArray(Label[]::new));
                case "lookup" -> visitor.visitLookupSwitchInsn(labels[(Integer)a[0]],(int[])a[1],Arrays.stream((int[])a[2]).mapToObj(i->labels[i]).toArray(Label[]::new));
                case "multi" -> visitor.visitMultiANewArrayInsn((String)a[0],(Integer)a[1]);
                case "indy" -> visitor.visitInvokeDynamicInsn((String)a[0],(String)a[1],(Handle)a[2],(Object[])a[3]);
                default -> throw new IllegalArgumentException(kind);
            }
        }
    }
    public InputCode(byte[] bytes) {
        this.bytes=bytes;
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int v,int a,String n,String s,String p,String[] i) { version=v;access=a;name=n;signature=s;parent=p;interfaces=i; }
            @Override public void visitNestHost(String host) { nestHost=host; }
            @Override public void visitNestMember(String member) { nestMembers.add(member); }
            @Override public void visitInnerClass(String n,String outer,String inner,int a) { innerClasses.add(new Object[]{n,outer,inner,a}); }
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object value) { fields.add(new Field(a,n,d,s,value));return null; }
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] exceptions) {
                Method method=new Method(a,n,d,s,exceptions);methods.put(method.key(),method);
                return new MethodVisitor(Opcodes.ASM9) {
                    void add(String kind,int opcode,Object... args) { method.instructions.add(new Instruction(kind,opcode,args)); }
                    @Override public void visitCode() { method.code=true; }
                    @Override public void visitLabel(Label label) { method.labels.put(label,method.instructions.size()); }
                    @Override public void visitInsn(int opcode) { add("op",opcode); }
                    @Override public void visitIntInsn(int opcode,int operand) { add("int",opcode,operand); }
                    @Override public void visitVarInsn(int opcode,int local) { add("var",opcode,local); }
                    @Override public void visitTypeInsn(int opcode,String type) { add("type",opcode,type); }
                    @Override public void visitFieldInsn(int opcode,String owner,String name,String desc) { add("field",opcode,owner,name,desc); }
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf) { add("method",opcode,owner,name,desc,itf); }
                    @Override public void visitLdcInsn(Object value) { add("ldc",Opcodes.LDC,value); }
                    @Override public void visitIincInsn(int local,int increment) { add("iinc",Opcodes.IINC,local,increment); }
                    @Override public void visitJumpInsn(int opcode,Label target) { add("jump",opcode,target); }
                    @Override public void visitTableSwitchInsn(int min,int max,Label fallback,Label... targets) { add("table",Opcodes.TABLESWITCH,min,max,fallback,targets); }
                    @Override public void visitLookupSwitchInsn(Label fallback,int[] keys,Label[] targets) { add("lookup",Opcodes.LOOKUPSWITCH,fallback,keys,targets); }
                    @Override public void visitMultiANewArrayInsn(String desc,int dimensions) { add("multi",Opcodes.MULTIANEWARRAY,desc,dimensions); }
                    @Override public void visitInvokeDynamicInsn(String name,String desc,Handle bootstrap,Object... arguments) { add("indy",Opcodes.INVOKEDYNAMIC,name,desc,bootstrap,arguments); }
                    @Override public void visitTryCatchBlock(Label start,Label end,Label handler,String type) { method.unresolvedHandlers.add(new Object[]{start,end,handler,type}); }
                    @Override public void visitMaxs(int stack,int locals) { method.maxStack=stack;method.maxLocals=locals; }
                    @Override public void visitEnd() { method.finish(); }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    }
}
