package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.net.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.jar.asm.commons.*;

/** Adapts the removed B42.21 scale field in the installed optional mod, never copying its code. */
public final class PeekNativeApi {
    private static final String PREFIX="pzmod/peekaview/";
    private static boolean installed;
    private PeekNativeApi() { }
    public static synchronized void install() throws Exception {
        if(installed)return;
        if(!Compatibility.CURRENT.equals(zombie.GitVersion.REVISION))throw new IllegalStateException("Peek API adapter requires the verified B42.22 game");
        ClassLoader loader=PeekNativeApi.class.getClassLoader();
        URL resource=loader.getResource(PREFIX+"PeekAViewMod.class");
        if(resource==null || !(resource.openConnection() instanceof JarURLConnection connection))throw new IOException("Selected PeekAView JAR is unavailable");
        Set<String> targets=new HashSet<>();Map<String,byte[]> foreign=new HashMap<>();
        try(JarFile jar=new JarFile(new File(connection.getJarFileURL().toURI()))) {
            for(var entry:jar.stream().filter(e->e.getName().startsWith(PREFIX)&&e.getName().endsWith(".class")).toList()) {
                byte[] bytes;try(InputStream in=jar.getInputStream(entry)){bytes=in.readAllBytes();}
                String name=entry.getName().substring(0,entry.getName().length()-6);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){
                        if(!descriptor.equals("Lme/zed_0xff/zombie_buddy/Patch;"))return null;
                        return new AnnotationVisitor(Opcodes.ASM9){
                            @Override public void visit(String key,Object value){if(key.equals("className")&&value instanceof String s)targets.add(s.replace('.','/'));}
                        };
                    }
                },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                foreign.put(name,adapt(bytes));
            }
        }
        Map<String,byte[]> checked=new HashMap<>();
        for(String name:RuntimeState.payload.classNames())checked.put(name,RuntimeState.payload.baseClassBytes(name));
        checked.putAll(foreign);
        try(JarFile game=new JarFile(Compatibility.gameJar().toFile())){ClassReferences.verify(checked,game);}
        var field=Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");field.setAccessible(true);
        Instrumentation instrumentation=(Instrumentation)field.get(null);
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(ClassLoader cl,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
                return name!=null && (name.startsWith(PREFIX)||targets.contains(name))?adapt(bytes):null;
            }
        },true);
        List<Class<?>> loaded=new ArrayList<>();
        for(Class<?> type:instrumentation.getAllLoadedClasses()) {
            String name=type.getName().replace('.','/');
            if((name.startsWith(PREFIX)||targets.contains(name))&&instrumentation.isModifiableClass(type))loaded.add(type);
        }
        instrumentation.retransformClasses(loaded.toArray(Class<?>[]::new));
        installed=true;
    }
    public static byte[] adapt(byte[] bytes) {
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,0);
        boolean[] changed={false};
        ClassVisitor remapped=new ClassRemapper(writer,new Remapper(Opcodes.ASM9){
            @Override public String map(String type){
                if(type.equals("zombie/util/list/PZArrayList")){changed[0]=true;return "java/util/List";}
                return type;
            }
        });
        reader.accept(new ClassVisitor(Opcodes.ASM9,remapped){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,descriptor,signature,exceptions)){
                    @Override public void visitFieldInsn(int opcode,String owner,String field,String type){
                        if(opcode==Opcodes.GETSTATIC&&owner.equals("zombie/core/Core")&&field.equals("tileScale")&&type.equals("I")) {
                            super.visitInsn(Opcodes.ICONST_2);changed[0]=true;
                        } else super.visitFieldInsn(opcode,owner,field,type);
                    }
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String type,boolean itf){
                        if(owner.equals("zombie/util/list/PZArrayList")&&opcode==Opcodes.INVOKEVIRTUAL){
                            super.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List",method,type,true);changed[0]=true;
                        } else super.visitMethodInsn(opcode,owner,method,type,itf);
                    }
                };
            }
        },0);
        return changed[0]?writer.toByteArray():bytes;
    }
}
