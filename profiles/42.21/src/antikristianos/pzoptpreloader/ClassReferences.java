package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.jar.asm.*;

/** Checks member/type references using resource bytes only, before any target is injected or initialized. */
final class ClassReferences {
    private record Header(String parent,String[] interfaces,Set<String> methods,Set<String> fields){}
    private final Map<String,byte[]> source;
    private final JarFile game;
    private final Map<String,Header> headers=new HashMap<>();
    private final Set<String> missing=new TreeSet<>();
    private ClassReferences(Map<String,byte[]> source,JarFile game){this.source=source;this.game=game;}
    static void verify(Map<String,byte[]> source,JarFile game)throws IOException{
        ClassReferences check=new ClassReferences(source,game);check.scan();
        if(!check.missing.isEmpty())throw new IOException("Unresolved game API references ("+check.missing.size()+"): "+String.join("; ",check.missing.stream().limit(8).toList()));
    }
    private Header header(String name){
        if(name.startsWith("["))return header("java/lang/Object");
        if(headers.containsKey(name))return headers.get(name);
        byte[] bytes=source.get(name);
        try{
            if(bytes==null){var entry=game.getJarEntry(name+".class");if(entry!=null)try(InputStream in=game.getInputStream(entry)){bytes=in.readAllBytes();}}
            if(bytes==null)try(InputStream in=ClassReferences.class.getClassLoader().getResourceAsStream(name+".class")){if(in!=null)bytes=in.readAllBytes();}
        }catch(IOException error){throw new UncheckedIOException(error);}
        if(bytes==null){headers.put(name,null);return null;}
        Set<String> methods=new HashSet<>(),fields=new HashSet<>();ClassReader reader=new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){methods.add(n+d);return null;}
            @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){fields.add(n+":"+d);return null;}
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        Header result=new Header(reader.getSuperName(),reader.getInterfaces(),methods,fields);headers.put(name,result);return result;
    }
    private boolean member(String owner,String key,boolean field,Set<String> seen){
        if(!seen.add(owner))return false;Header h=header(owner);if(h==null)return false;
        if((field?h.fields:h.methods).contains(key))return true;
        if(!field&&key.startsWith("<init>"))return false;
        if(!field&&owner.equals("java/lang/invoke/MethodHandle")&&(key.startsWith("invoke(")||key.startsWith("invokeExact(")))return true;
        if(!field&&owner.equals("java/lang/invoke/VarHandle"))return true;
        if(h.parent!=null&&member(h.parent,key,field,seen))return true;
        for(String itf:h.interfaces)if(member(itf,key,field,seen))return true;return false;
    }
    private void type(String name,String where){
        if(name==null)return;if(name.startsWith("[")){descriptor(name,where);return;}
        if(header(name)==null)missing.add(where+" -> type "+name);
    }
    private void descriptor(String descriptor,String where){
        Type type=Type.getType(descriptor);
        if(type.getSort()==Type.METHOD){for(Type arg:type.getArgumentTypes())descriptor(arg.getDescriptor(),where);descriptor(type.getReturnType().getDescriptor(),where);}
        else if(type.getSort()==Type.ARRAY)descriptor(type.getElementType().getDescriptor(),where);
        else if(type.getSort()==Type.OBJECT)type(type.getInternalName(),where);
    }
    private void reference(String owner,String name,String descriptor,boolean field,String where){
        type(owner,where);descriptor(descriptor,where);String key=name+(field?":":"")+descriptor;
        if(!member(owner,key,field,new HashSet<>()))missing.add(where+" -> "+owner+"."+key);
    }
    private void value(Object value,String where){
        if(value instanceof Type t)descriptor(t.getDescriptor(),where);
        else if(value instanceof Handle h)reference(h.getOwner(),h.getName(),h.getDesc(),h.getTag()<=Opcodes.H_PUTSTATIC,where);
        else if(value instanceof ConstantDynamic d){descriptor(d.getDescriptor(),where);value(d.getBootstrapMethod(),where);for(int i=0;i<d.getBootstrapMethodArgumentCount();i++)value(d.getBootstrapMethodArgument(i),where);}
    }
    private void scan(){
        for(var entry:source.entrySet()){
            String name=entry.getKey();ClassReader reader=new ClassReader(entry.getValue());type(reader.getSuperName(),name);for(String itf:reader.getInterfaces())type(itf,name);
            reader.accept(new ClassVisitor(Opcodes.ASM9){
                @Override public FieldVisitor visitField(int a,String n,String d,String s,Object v){descriptor(d,name+"."+n);value(v,name+"."+n);return null;}
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] exceptions){
                    String where=name+"."+n+d;descriptor(d,where);if(exceptions!=null)for(String exception:exceptions)type(exception,where);
                    return new MethodVisitor(Opcodes.ASM9){
                        @Override public void visitTypeInsn(int opcode,String type){type(type,where);}
                        @Override public void visitMultiANewArrayInsn(String desc,int dims){descriptor(desc,where);}
                        @Override public void visitFieldInsn(int opcode,String owner,String n,String desc){reference(owner,n,desc,true,where);}
                        @Override public void visitMethodInsn(int opcode,String owner,String n,String desc,boolean itf){reference(owner,n,desc,false,where);}
                        @Override public void visitLdcInsn(Object v){value(v,where);}
                        @Override public void visitInvokeDynamicInsn(String n,String desc,Handle bootstrap,Object... args){descriptor(desc,where);value(bootstrap,where);for(Object arg:args)value(arg,where);}
                        @Override public void visitTryCatchBlock(Label start,Label end,Label handler,String type){type(type,where);}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
    }
}