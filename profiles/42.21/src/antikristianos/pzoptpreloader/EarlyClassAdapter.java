package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.lang.invoke.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Own schema-preserving bridge for verified upstream additions to already loaded classes. */
public final class EarlyClassAdapter {
    private static final String SELF = "antikristianos/pzoptpreloader/EarlyClassAdapter";
    private static final String STORAGE = "antikristianos/pzoptpreloader/LoadedFields";
    private static final Handle ACCESS = new Handle(Opcodes.H_INVOKESTATIC, SELF, "access",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false);
    private record Member(int access, String descriptor) { }
    private record Schema(String name, Map<String,Member> fields, Map<String,Member> methods) {
        String companion() { return name + "$PZOptPreloaderMethods"; }
    }
    private final Map<String,Schema> schemas = new TreeMap<>();
    private static volatile EarlyClassAdapter active;

    private static Map<String,Member> members(byte[] bytes, boolean methods) {
        Map<String,Member> result = new TreeMap<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v) {
                if (!methods) result.put(n+d,new Member(a,d)); return null;
            }
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                if (methods) result.put(n+d,new Member(a,d)); return null;
            }
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        return result;
    }
    private static Map<String,Member> additions(String name, Map<String,Member> stock, Map<String,Member> next) {
        for (var e:stock.entrySet()) if (!e.getValue().equals(next.get(e.getKey())))
            throw new IllegalStateException("Loaded schema changed: "+name+"."+e.getKey());
        Map<String,Member> result = new TreeMap<>(next); stock.keySet().forEach(result::remove); return result;
    }
    /** All input definitions have passed the source profile, native port and own repair gates. */
    public static EarlyClassAdapter prepare(Map<String,byte[]> definitions, Map<String,byte[]> loaded) throws IOException {
        EarlyClassAdapter adapter = new EarlyClassAdapter();
        for (var e:loaded.entrySet()) {
            String name=e.getKey(); byte[] next=definitions.get(name);
            if (next==null) throw new IOException("Missing selected loaded definition: "+name);
            Map<String,Member> fields=additions(name,members(e.getValue(),false),members(next,false));
            Map<String,Member> methods=additions(name,members(e.getValue(),true),members(next,true));
            for (var f:fields.entrySet()) if ((f.getValue().access & (Opcodes.ACC_STATIC|Opcodes.ACC_FINAL))!=0)
                throw new IOException("Unsupported loaded static/final field: "+name+"."+f.getKey());
            for (var m:methods.entrySet()) if (m.getKey().startsWith("<")
                    || (m.getValue().access & (Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT|Opcodes.ACC_SYNCHRONIZED))!=0)
                throw new IOException("Unsupported loaded method addition: "+name+"."+m.getKey());
            if (!fields.isEmpty()||!methods.isEmpty()) adapter.schemas.put(name,new Schema(name,fields,methods));
        }
        Map<String,byte[]> output=new TreeMap<>();
        for (var e:definitions.entrySet()) {
            Schema schema=adapter.schemas.get(e.getKey());
            if (schema==null) output.put(e.getKey(),adapter.rewrite(e.getValue()));
            else adapter.split(e.getValue(),schema,output);
        }
        definitions.clear(); definitions.putAll(output); return adapter;
    }
    public void activate() { active=this; }
    private static String descriptor(Schema schema, Member method) {
        return (method.access & Opcodes.ACC_STATIC)!=0 ? method.descriptor : "(L"+schema.name+";"+method.descriptor.substring(1);
    }
    private void split(byte[] bytes, Schema schema, Map<String,byte[]> output) {
        ClassReader reader=new ClassReader(bytes);
        ClassWriter target=new ClassWriter(ClassWriter.COMPUTE_MAXS), companion=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        companion.visit(reader.readUnsignedShort(6),Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL|Opcodes.ACC_SUPER,
            schema.companion(),null,"java/lang/Object",null);
        reader.accept(new ClassVisitor(Opcodes.ASM9,target) {
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v) {
                return schema.fields.containsKey(n+d)?null:super.visitField(a,n,d,s,v);
            }
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                Member extra=schema.methods.get(n+d);
                MethodVisitor out=extra==null?super.visitMethod(a,n,d,s,e)
                    :companion.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,n,descriptor(schema,extra),null,e);
                return visitor(out,extra==null?null:schema.name);
            }
        },0);
        companion.visitEnd(); target.visitEnd(); output.put(schema.name,target.toByteArray());
        if (!schema.methods.isEmpty()) output.put(schema.companion(),companion.toByteArray());
    }
    public byte[] rewrite(byte[] bytes) {
        if (schemas.isEmpty()) return bytes;
        boolean[] changed={false};
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9,writer) {
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                return visitor(super.visitMethod(a,n,d,s,e),null,changed);
            }
        },0);
        return changed[0]?writer.toByteArray():bytes;
    }
    private MethodVisitor visitor(MethodVisitor out,String host) {
        return visitor(out,host,new boolean[1]);
    }
    private MethodVisitor visitor(MethodVisitor out,String host,boolean[] changed) {
        return new MethodVisitor(Opcodes.ASM9,out) {
            @Override public void visitFieldInsn(int op,String owner,String name,String desc) {
                Schema schema=schemas.get(owner);
                if (schema!=null&&schema.fields.containsKey(name+desc)) {
                    changed[0]=true;
                    boolean get=op==Opcodes.GETFIELD;
                    if (!get&&op!=Opcodes.PUTFIELD) throw new IllegalStateException("Unexpected extension field opcode");
                    String key=owner+"/"+name+":"+desc; Type type=Type.getType(desc);
                    super.visitLdcInsn(key);
                    boolean reference=type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY;
                    String value=reference?"Ljava/lang/Object;":desc;
                    String suffix=reference?"":switch(type.getSort()) {
                        case Type.BOOLEAN->"Boolean"; case Type.BYTE->"Byte"; case Type.CHAR->"Char";
                        case Type.SHORT->"Short"; case Type.INT->"Int"; case Type.LONG->"Long";
                        case Type.FLOAT->"Float"; case Type.DOUBLE->"Double";
                        default->throw new IllegalStateException("Invalid extension field type");
                    };
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,STORAGE,(get?"get":"put")+suffix,
                        get?"(Ljava/lang/Object;Ljava/lang/String;)"+value:"(Ljava/lang/Object;"+value+"Ljava/lang/String;)V",false);
                    if (get&&reference) super.visitTypeInsn(Opcodes.CHECKCAST,type.getInternalName());
                    return;
                }
                if (host!=null) {
                    changed[0]=true;
                    String call=switch(op) {
                        case Opcodes.GETFIELD->"(L"+owner+";)"+desc;
                        case Opcodes.PUTFIELD->"(L"+owner+";"+desc+")V";
                        case Opcodes.GETSTATIC->"()"+desc;
                        case Opcodes.PUTSTATIC->"("+desc+")V";
                        default->throw new IllegalStateException("Unexpected field access");
                    };
                    super.visitInvokeDynamicInsn("access",call,ACCESS,op,host,owner,name,desc); return;
                }
                super.visitFieldInsn(op,owner,name,desc);
            }
            @Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf) {
                Schema schema=schemas.get(owner); Member extra=schema==null?null:schema.methods.get(name+desc);
                if (extra!=null) {
                    changed[0]=true;
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,schema.companion(),name,descriptor(schema,extra),false); return;
                }
                if (owner.equals("java/lang/invoke/MethodHandles$Lookup")&&name.equals("defineHiddenClassWithClassData")
                        &&desc.equals("([BLjava/lang/Object;Z[Ljava/lang/invoke/MethodHandles$Lookup$ClassOption;)Ljava/lang/invoke/MethodHandles$Lookup;")) {
                    changed[0]=true;
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,"defineHiddenClassWithClassData",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;"+desc.substring(1),false); return;
                }
                if(owner.equals("java/lang/invoke/MethodHandles$Lookup")&&Set.of("findVirtual","findStatic","findGetter","findSetter","findStaticGetter","findStaticSetter").contains(name)) {
                    changed[0]=true;
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,SELF,name,
                        "(Ljava/lang/invoke/MethodHandles$Lookup;"+desc.substring(1),false); return;
                }
                if (host!=null&&!name.equals("<init>")) {
                    changed[0]=true;
                    String call=op==Opcodes.INVOKESTATIC?desc:"(L"+owner+";"+desc.substring(1);
                    super.visitInvokeDynamicInsn("access",call,ACCESS,op,host,owner,name,desc); return;
                }
                super.visitMethodInsn(op,owner,name,desc,itf);
            }
            @Override public void visitLdcInsn(Object value) { Object next=rewriteConstant(value); changed[0]|=!Objects.equals(value,next); super.visitLdcInsn(next); }
            @Override public void visitInvokeDynamicInsn(String name,String desc,Handle bootstrap,Object... arguments) {
                Object[] values=arguments.clone(); for(int i=0;i<values.length;i++)values[i]=rewriteConstant(values[i]);
                Handle next=(Handle)rewriteConstant(bootstrap);changed[0]|=!Arrays.equals(values,arguments)||!next.equals(bootstrap);
                super.visitInvokeDynamicInsn(name,desc,next,values);
            }
        };
    }
    private Object rewriteConstant(Object value) {
        if (value instanceof Handle handle) {
            Schema schema=schemas.get(handle.getOwner()); Member method=schema==null?null:schema.methods.get(handle.getName()+handle.getDesc());
            if (method!=null) return new Handle(Opcodes.H_INVOKESTATIC,schema.companion(),handle.getName(),descriptor(schema,method),false);
            if (schema!=null&&schema.fields.containsKey(handle.getName()+handle.getDesc()))
                throw new IllegalStateException("Extension field handles require an explicit adapter");
        }
        if (value instanceof ConstantDynamic dynamic) {
            Object[] args=new Object[dynamic.getBootstrapMethodArgumentCount()];
            for(int i=0;i<args.length;i++)args[i]=rewriteConstant(dynamic.getBootstrapMethodArgument(i));
            return new ConstantDynamic(dynamic.getName(),dynamic.getDescriptor(),(Handle)rewriteConstant(dynamic.getBootstrapMethod()),args);
        }
        return value;
    }
    /** Exact typed call sites preserve the original host's access to its existing private members. */
    public static CallSite access(MethodHandles.Lookup caller,String ignored,MethodType call,int opcode,
            String hostName,String ownerName,String member,String descriptor) throws Throwable {
        ClassLoader loader=caller.lookupClass().getClassLoader();
        Class<?> host=Class.forName(hostName.replace('/','.'),false,loader),owner=Class.forName(ownerName.replace('/','.'),false,loader);
        MethodHandles.Lookup lookup=MethodHandles.privateLookupIn(host,MethodHandles.lookup());
        MethodHandle handle;
        if (opcode>=Opcodes.GETSTATIC&&opcode<=Opcodes.PUTFIELD) {
            Class<?> type=MethodType.fromMethodDescriptorString("()"+descriptor,loader).returnType();
            handle=switch(opcode) {
                case Opcodes.GETSTATIC->lookup.findStaticGetter(owner,member,type);
                case Opcodes.PUTSTATIC->lookup.findStaticSetter(owner,member,type);
                case Opcodes.GETFIELD->lookup.findGetter(owner,member,type);
                default->lookup.findSetter(owner,member,type);
            };
        } else {
            MethodType type=MethodType.fromMethodDescriptorString(descriptor,loader);
            handle=switch(opcode) {
                case Opcodes.INVOKESTATIC->lookup.findStatic(owner,member,type);
                case Opcodes.INVOKESPECIAL->lookup.findSpecial(owner,member,type,host);
                default->lookup.findVirtual(owner,member,type);
            };
        }
        return new ConstantCallSite(handle.asType(call));
    }
    /** Hidden JIT classes bypass instrumentation; adapt them immediately before the original define call. */
    public static MethodHandles.Lookup defineHiddenClassWithClassData(MethodHandles.Lookup lookup,byte[] bytes,Object data,
            boolean initialize,MethodHandles.Lookup.ClassOption... options) throws IllegalAccessException {
        EarlyClassAdapter adapter=active;
        return lookup.defineHiddenClassWithClassData(adapter==null?bytes:adapter.rewrite(bytes),data,initialize,options);
    }
    private static Schema schema(Class<?> owner) {
        EarlyClassAdapter adapter=active; return adapter==null?null:adapter.schemas.get(owner.getName().replace('.','/'));
    }
    private static MethodHandle addedMethod(MethodHandles.Lookup lookup,Class<?> owner,String name,MethodType type,boolean statik)
            throws NoSuchMethodException,IllegalAccessException {
        Schema schema=schema(owner); Member extra=schema==null?null:schema.methods.get(name+type.toMethodDescriptorString());
        if(extra==null)return null;
        if(((extra.access&Opcodes.ACC_STATIC)!=0)!=statik)throw new NoSuchMethodException(name);
        Class<?> companion;
        try { companion=Class.forName(schema.companion().replace('/','.'),false,owner.getClassLoader()); }
        catch(ClassNotFoundException error){throw new NoSuchMethodException(error.toString());}
        return lookup.findStatic(companion,name,statik?type:type.insertParameterTypes(0,owner));
    }
    public static MethodHandle findVirtual(MethodHandles.Lookup lookup,Class<?> owner,String name,MethodType type)
            throws NoSuchMethodException,IllegalAccessException {
        MethodHandle added=addedMethod(lookup,owner,name,type,false);return added==null?lookup.findVirtual(owner,name,type):added;
    }
    public static MethodHandle findStatic(MethodHandles.Lookup lookup,Class<?> owner,String name,MethodType type)
            throws NoSuchMethodException,IllegalAccessException {
        MethodHandle added=addedMethod(lookup,owner,name,type,true);return added==null?lookup.findStatic(owner,name,type):added;
    }
    private static MethodHandle addedField(Class<?> owner,String name,Class<?> type,boolean setter) throws IllegalAccessException {
        Schema schema=schema(owner);String desc=type.descriptorString();Member field=schema==null?null:schema.fields.get(name+desc);
        if(field==null)return null;
        String suffix=type==boolean.class?"Boolean":type==byte.class?"Byte":type==short.class?"Short":type==char.class?"Char":type==int.class?"Int":type==long.class?"Long":type==float.class?"Float":type==double.class?"Double":"";
        Class<?> value=type.isPrimitive()?type:Object.class;
        try {
            MethodHandle handle=MethodHandles.lookup().findStatic(LoadedFields.class,(setter?"put":"get")+suffix,
                setter?MethodType.methodType(void.class,Object.class,value,String.class):MethodType.methodType(value,Object.class,String.class));
            handle=MethodHandles.insertArguments(handle,setter?2:1,schema.name+"/"+name+":"+desc);
            return handle.asType(setter?MethodType.methodType(void.class,owner,type):MethodType.methodType(type,owner));
        }catch(NoSuchMethodException error){throw new IllegalStateException(error);}
    }
    public static MethodHandle findGetter(MethodHandles.Lookup lookup,Class<?> owner,String name,Class<?> type)
            throws NoSuchFieldException,IllegalAccessException {
        MethodHandle added=addedField(owner,name,type,false);return added==null?lookup.findGetter(owner,name,type):added;
    }
    public static MethodHandle findSetter(MethodHandles.Lookup lookup,Class<?> owner,String name,Class<?> type)
            throws NoSuchFieldException,IllegalAccessException {
        MethodHandle added=addedField(owner,name,type,true);return added==null?lookup.findSetter(owner,name,type):added;
    }
    public static MethodHandle findStaticGetter(MethodHandles.Lookup lookup,Class<?> owner,String name,Class<?> type)
            throws NoSuchFieldException,IllegalAccessException {return lookup.findStaticGetter(owner,name,type);}
    public static MethodHandle findStaticSetter(MethodHandles.Lookup lookup,Class<?> owner,String name,Class<?> type)
            throws NoSuchFieldException,IllegalAccessException {return lookup.findStaticSetter(owner,name,type);}
}
