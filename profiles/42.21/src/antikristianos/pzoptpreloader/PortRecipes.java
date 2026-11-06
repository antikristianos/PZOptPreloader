package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.jar.asm.*;
import org.json.*;

/** Replays structural edits against separately installed inputs. Contains no vendor instructions or literals. */
public final class PortRecipes {
    private final JSONObject plan;
    private final Map<String,InputCode> inputs=new HashMap<>();
    private final Map<String,byte[]> output;
    private final JarFile game;
    public PortRecipes(JSONObject plan,Map<String,byte[]> source,JarFile game)throws IOException {
        this.plan=plan;this.game=game;output=new TreeMap<>(source);
        if(plan.getInt("format")!=1)throw new IOException("Unsupported adaptation format");
        JSONObject required=plan.getJSONObject("inputs");
        for(String id:required.keySet()) {
            String name=id.substring(2);byte[] bytes;
            if(id.startsWith("p:"))bytes=source.get(name);
            else if(id.startsWith("g:")){
                JarEntry entry=game.getJarEntry(name+".class");
                if(entry==null)bytes=null;else try(InputStream in=game.getInputStream(entry)){bytes=in.readAllBytes();}
            }else throw new IOException("Unknown adaptation input: "+id);
            if(bytes==null || !Compatibility.hash(bytes).equals(required.getString(id)))
                throw new IOException("Adaptation input changed: "+id);
            inputs.put(id,new InputCode(bytes));
        }
    }
    public Map<String,byte[]> apply()throws IOException {
        JSONObject classes=plan.getJSONObject("classes");
        Hierarchy hierarchy=new Hierarchy(output,classes,game);
        for(String name:new TreeSet<>(classes.keySet())) {
            JSONObject recipe=classes.getJSONObject(name);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
                @Override protected String getCommonSuperClass(String a,String b){return hierarchy.common(a,b);}
            };
            writer.visit(recipe.getInt("version"),recipe.getInt("access"),name,nullable(recipe,"signature"),recipe.getString("parent"),strings(recipe.getJSONArray("interfaces")));
            String nest=nullable(recipe,"nestHost");if(nest!=null)writer.visitNestHost(nest);
            for(Object member:recipe.getJSONArray("nestMembers"))writer.visitNestMember((String)member);
            for(Object raw:recipe.getJSONArray("innerClasses")){
                JSONArray inner=(JSONArray)raw;
                writer.visitInnerClass(inner.getString(0),text(inner,1),text(inner,2),inner.getInt(3));
            }
            // Metadata comes from an installed input too; annotations are not reconstructed from literals.
            copyClassMetadata(input(recipe.getString("metadata")),writer);
            for(Object raw:recipe.getJSONArray("fields")){
                JSONObject f=(JSONObject)raw;
                Object value=f.has("value")?constant(f.get("value")):null;
                FieldVisitor visitor=writer.visitField(f.getInt("access"),f.getString("name"),f.getString("descriptor"),nullable(f,"signature"),value);
                if(f.has("metadata"))copyFieldMetadata(f.getJSONArray("metadata"),visitor);
                visitor.visitEnd();
            }
            for(Object raw:recipe.getJSONArray("methods")){
                JSONObject m=(JSONObject)raw;
                MethodVisitor visitor=writer.visitMethod(m.getInt("access"),m.getString("name"),m.getString("descriptor"),nullable(m,"signature"),strings(m.getJSONArray("exceptions")));
                if(m.has("metadata"))copyMethodMetadata(m.getJSONArray("metadata"),visitor);
                if(m.getBoolean("code")) {
                    List<InputCode.Instruction> instructions=new ArrayList<>();
                    for(Object piece:m.getJSONArray("pieces")) {
                        JSONArray segment=(JSONArray)piece;
                        InputCode.Method from=method(segment.getString(0),segment.getString(1));
                        int start=segment.getInt(2),count=segment.getInt(3);
                        Map<Integer,JSONObject> edits=new HashMap<>();
                        if(segment.length()>4)for(Object edit:segment.getJSONArray(4)){
                            JSONArray e=(JSONArray)edit;edits.put(e.getInt(0),e.getJSONObject(1));
                        }
                        for(int i=0;i<count;i++) {
                            InputCode.Instruction original=from.instructions.get(start+i);
                            instructions.add(adapt(original,edits.get(i)));
                        }
                    }
                    List<InputCode.Handler> handlers=new ArrayList<>();
                    for(Object handler:m.getJSONArray("handlers")){
                        JSONArray h=(JSONArray)handler;handlers.add(new InputCode.Handler(h.getInt(0),h.getInt(1),h.getInt(2),text(h,3)));
                    }
                    if(!fingerprint(instructions,handlers).equals(m.getString("fingerprint")))
                        throw new IOException("Adaptation result differs from verified method: "+name+"."+m.getString("name"));
                    Label[] labels=new Label[instructions.size()+1];Arrays.setAll(labels,i->new Label());
                    visitor.visitCode();
                    for(var h:handlers)visitor.visitTryCatchBlock(labels[h.start()],labels[h.end()],labels[h.target()],h.type());
                    for(int i=0;i<instructions.size();i++){visitor.visitLabel(labels[i]);instructions.get(i).emit(visitor,labels);}
                    visitor.visitLabel(labels[instructions.size()]);visitor.visitMaxs(0,0);
                }
                visitor.visitEnd();
            }
            writer.visitEnd();output.put(name,writer.toByteArray());
        }
        return output;
    }
    private InputCode input(String id){return Objects.requireNonNull(inputs.get(id),"Unbound input "+id);}
    private InputCode.Method method(String id,String name){return Objects.requireNonNull(input(id).methods.get(name),"Unbound input method "+name);}
    private static String nullable(JSONObject o,String key){return o.isNull(key)?null:o.getString(key);}
    private static String text(JSONArray a,int i){return a.isNull(i)?null:a.getString(i);}
    private static String[] strings(JSONArray a){String[] r=new String[a.length()];for(int i=0;i<r.length;i++)r[i]=a.getString(i);return r;}
    private static int[] ints(JSONArray a){int[] r=new int[a.length()];for(int i=0;i<r.length;i++)r[i]=a.getInt(i);return r;}
    /** Canonical output check: only the digest is shipped, not the input instruction text. */
    public static String fingerprint(List<InputCode.Instruction> code,List<InputCode.Handler> handlers){
        StringBuilder text=new StringBuilder();
        for(var i:code)text.append(i.kind).append(':').append(i.opcode).append(':').append(Arrays.deepToString(i.arguments)).append('\n');
        for(var h:handlers)text.append(h).append('\n');
        return Compatibility.hash(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private InputCode.Instruction adapt(InputCode.Instruction original,JSONObject patch){
        if(patch==null)return original;
        Object[] args=original.arguments.clone();
        if(patch.has("symbols")){
            JSONArray symbols=patch.getJSONArray("symbols");
            for(int i=0;i<symbols.length();i++)args[i]=symbols.get(i);
        }
        if(patch.has("local"))args[0]=patch.getInt("local");
        if(patch.has("value"))args[original.kind.equals("iinc")?1:0]=constant(patch.get("value"));
        if(patch.has("target"))args[0]=patch.getInt("target");
        if(patch.has("switch")){
            JSONObject sw=patch.getJSONObject("switch");
            if(original.kind.equals("table")){
                args[0]=constant(sw.get("min"));args[1]=constant(sw.get("max"));args[2]=sw.getInt("default");args[3]=ints(sw.getJSONArray("labels"));
            }else{
                args[0]=sw.getInt("default");args[2]=ints(sw.getJSONArray("labels"));
                JSONArray keys=sw.getJSONArray("keys");int[] values=new int[keys.length()];
                for(int i=0;i<values.length;i++)values[i]=((Number)constant(keys.get(i))).intValue();args[1]=values;
            }
        }
        if(patch.has("indy")){
            JSONObject indy=patch.getJSONObject("indy");args[0]=indy.getString("name");args[1]=indy.getString("descriptor");
            args[2]=constant(indy.get("bootstrap"));JSONArray values=indy.getJSONArray("arguments");
            Object[] arguments=new Object[values.length()];for(int i=0;i<arguments.length;i++)arguments[i]=constant(values.get(i));args[3]=arguments;
        }
        return new InputCode.Instruction(original.kind,original.opcode,args);
    }
    /** Values are read from installed fields/instructions; only type/member symbols are inline. */
    private Object constant(Object raw){
        JSONObject expression=(JSONObject)raw;
        if(expression.has("type"))return Type.getType(expression.getString("type"));
        if(expression.has("handle")){
            JSONArray h=expression.getJSONArray("handle");return new Handle(h.getInt(0),h.getString(1),h.getString(2),h.getString(3),h.getBoolean(4));
        }
        if(expression.has("dynamic")){
            JSONObject d=expression.getJSONObject("dynamic");JSONArray args=d.getJSONArray("arguments");Object[] values=new Object[args.length()];
            for(int i=0;i<values.length;i++)values[i]=constant(args.get(i));
            return new ConstantDynamic(d.getString("name"),d.getString("descriptor"),(Handle)constant(d.get("bootstrap")),values);
        }
        JSONArray ref=expression.getJSONArray("read");InputCode source=input(ref.getString(0));Object value;
        if(ref.getString(1).equals("field"))value=source.fields.get(ref.getInt(2)).value();
        else {
            InputCode.Instruction instruction=method(ref.getString(0),ref.getString(1)).instructions.get(ref.getInt(2));
            value=instruction.arguments[ref.getInt(3)];
            if(ref.length()>4){int offset=ref.getInt(4);value=value instanceof int[] a?a[offset]:((Object[])value)[offset];}
        }
        String transform=expression.optString("transform","");
        if(transform.equals("lower"))return ((String)value).toLowerCase(Locale.ROOT);
        if(transform.equals("lowerHash"))return ((String)value).toLowerCase(Locale.ROOT).hashCode();
        if(!transform.isEmpty())throw new IllegalArgumentException("Unknown constant adaptation");
        return value;
    }
    private void copyFieldMetadata(JSONArray origin,FieldVisitor visitor){
        InputCode c=input(origin.getString(0));String name=origin.getString(1);
        new ClassReader(c.bytes).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){
                if(!(n+d).equals(name))return null;
                return new FieldVisitor(Opcodes.ASM9,visitor){@Override public void visitEnd(){}};
            }
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    }
    private void copyMethodMetadata(JSONArray origin,MethodVisitor visitor){
        new ClassReader(input(origin.getString(0)).bytes).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(!(n+d).equals(origin.getString(1)))return null;
                return new MethodVisitor(Opcodes.ASM9,visitor){@Override public void visitEnd(){}};
            }
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    }
    private static void copyClassMetadata(InputCode source,ClassVisitor visitor){
        new ClassReader(source.bytes).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible){return visitor.visitAnnotation(desc,visible);}
            @Override public AnnotationVisitor visitTypeAnnotation(int ref,TypePath path,String desc,boolean visible){return visitor.visitTypeAnnotation(ref,path,desc,visible);}
            @Override public void visitOuterClass(String owner,String name,String desc){visitor.visitOuterClass(owner,name,desc);}
            @Override public void visitPermittedSubclass(String name){visitor.visitPermittedSubclass(name);}
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
    }
    private static final class Hierarchy {
        record Header(String parent,List<String> interfaces,boolean itf){}
        final Map<String,byte[]> source;final JSONObject prepared;final JarFile jar;final Map<String,Header> headers=new HashMap<>();
        Hierarchy(Map<String,byte[]> source,JSONObject prepared,JarFile jar){this.source=source;this.prepared=prepared;this.jar=jar;}
        Header header(String name){
            return headers.computeIfAbsent(name,n->{
                JSONObject p=prepared.optJSONObject(n);
                if(p!=null)return new Header(nullable(p,"parent"),Arrays.asList(strings(p.getJSONArray("interfaces"))),(p.getInt("access")&Opcodes.ACC_INTERFACE)!=0);
                byte[] bytes=source.get(n);
                try{
                    if(bytes==null){JarEntry e=jar.getJarEntry(n+".class");if(e!=null)try(InputStream in=jar.getInputStream(e)){bytes=in.readAllBytes();}}
                    if(bytes==null)try(InputStream in=PortRecipes.class.getClassLoader().getResourceAsStream(n+".class")){if(in!=null)bytes=in.readAllBytes();}
                }catch(IOException error){throw new UncheckedIOException(error);}
                if(bytes==null)throw new IllegalStateException("Cannot resolve frame type without loading game classes: "+n);
                ClassReader r=new ClassReader(bytes);return new Header(r.getSuperName(),Arrays.asList(r.getInterfaces()),(r.getAccess()&Opcodes.ACC_INTERFACE)!=0);
            });
        }
        boolean assignable(String target,String value){return assignable(target,value,new HashSet<>());}
        boolean assignable(String target,String value,Set<String> seen){
            if(target.equals(value)||target.equals("java/lang/Object"))return true;
            if(value.startsWith("[")){
                if(target.equals("java/lang/Cloneable")||target.equals("java/io/Serializable"))return true;
                if(!target.startsWith("["))return false;
                String a=element(target),b=element(value);
                if(primitive(a)||primitive(b))return a.equals(b);
                return assignable(a,b,seen);
            }
            if(target.startsWith("[")||!seen.add(value))return false;
            Header h=header(value);
            if(h.parent!=null && assignable(target,h.parent,seen))return true;
            for(String itf:h.interfaces)if(assignable(target,itf,seen))return true;
            return false;
        }
        static boolean primitive(String name){return name.length()==1;}
        static String element(String array){String c=array.substring(1);return c.startsWith("L")?c.substring(1,c.length()-1):c;}
        static String array(String element){return element.startsWith("[")?"["+element:"[L"+element+";";}
        String common(String a,String b){
            if(assignable(a,b))return a;if(assignable(b,a))return b;
            if(a.startsWith("[")&&b.startsWith("[")){
                String x=element(a),y=element(b);
                return primitive(x)||primitive(y)?"java/lang/Object":array(common(x,y));
            }
            if(a.startsWith("[")||b.startsWith("[")||header(a).itf||header(b).itf)return "java/lang/Object";
            String p=header(a).parent;
            while(p!=null){if(assignable(p,b))return p;p=header(p).parent;}
            return "java/lang/Object";
        }
    }
}