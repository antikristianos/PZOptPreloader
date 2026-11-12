package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.jar.asm.*;
import org.json.*;

/** Strict final layer over both already-verified source ports, with native hook preflight. */
public final class OffscreenReflectionRepair {
    private static final String STATE="antikristianos/pzoptpreloader/OffscreenReflectionState";
    private static JSONObject pins; private static volatile boolean nativeVerified;
    private OffscreenReflectionRepair(){ }
    private static synchronized JSONObject pins() throws IOException {
        if(pins==null)try(InputStream in=OffscreenReflectionRepair.class.getResourceAsStream("offscreen-repair-42.22.json")){
            if(in==null)throw new IOException("Offscreen source profile missing");pins=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }return pins;
    }
    public static synchronized void verifyNative() throws IOException {
        if(nativeVerified)return;
        JSONObject classes=pins().getJSONObject("native");
        try(JarFile game=new JarFile(Compatibility.gameJar().toFile())){
            for(String name:classes.keySet()){
                var entry=game.getJarEntry(name+".class");if(entry==null)throw new IOException("Missing native source hook: "+name);
                Map<String,String> methods;try(InputStream in=game.getInputStream(entry)){methods=Compatibility.methods(in.readAllBytes());}
                JSONObject expected=classes.getJSONObject(name);for(String key:expected.keySet())if(!RepairProfiles.matches(expected.getString(key),methods.get(key)))throw new IOException("Native source hook changed: "+name+"."+key);
            }
        }nativeVerified=true;
    }
    public static void prepareNativeHooks(){
        if(!nativeReady())return;
        try{ClassLoader loader=OffscreenReflectionRepair.class.getClassLoader();for(String target:List.of("zombie.core.ShaderHelper","zombie.core.opengl.CharacterModelCamera","zombie.vehicles.VehicleModelCamera"))Class.forName(target,false,loader);}
        catch(ClassNotFoundException error){throw new IllegalStateException("Native offscreen source target missing",error);}
    }
    public static boolean nativeReady(){return nativeVerified&&Compatibility.developmentRuntime();}
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        JSONObject all=pins().getJSONObject("payload");if(!all.has(name))return bytes;verifyNative();
        JSONObject expected=all.getJSONObject(name);Map<String,String> actual=Compatibility.methods(bytes);
        for(String key:expected.keySet())if(expected.getJSONArray(key).toList().stream().noneMatch(previous->RepairProfiles.matches((String)previous,actual.get(key))))throw new IOException("Unsupported offscreen source method: "+name+"."+key);
        ClassReader reader=new ClassReader(bytes);if(!reader.getClassName().equals(name))throw new IOException("Offscreen source class identity");
        int[] hits={0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,method,desc,sig,exceptions);if(!expected.has(method+desc))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    void call(String m,String d){super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,m,d,false);hits[0]++;}
                    @Override public void visitCode(){super.visitCode();
                        if(name.endsWith("/FBORenderCell")&&(method.equals("renderPuddles")||method.equals("renderWater"))){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ILOAD,1);call("queueSources","(Ljava/lang/Object;I)V");}

                    }
                    @Override public void visitLdcInsn(Object value){
                        super.visitLdcInsn(value);
                        if(name.endsWith("/IsoWorld")&&value instanceof Float f&&f==-100.0f)call("cullLower","(F)F");
                    }
                    @Override public void visitFieldInsn(int op,String owner,String field,String d){
                        if(name.endsWith("/IsoWorld")&&op==Opcodes.GETFIELD&&field.equals("current")&&d.equals("Lzombie/iso/IsoGridSquare;")){
                            super.visitInsn(Opcodes.DUP);call("trackModel","(Ljava/lang/Object;)V");
                        }super.visitFieldInsn(op,owner,field,d);
                    }
                    @Override public void visitMethodInsn(int op,String owner,String m,String d,boolean itf){
                        if(owner.equals(STATE))throw new IllegalStateException("Duplicate offscreen source adapter");
                        boolean visibility=m.equals("isOnScreen")&&d.equals("()Z")&&op==Opcodes.INVOKEVIRTUAL;
                        if(visibility)super.visitInsn(Opcodes.DUP);
                        if(name.endsWith("/ShaderUnit")&&owner.equals("org/lwjgl/opengl/GL20")&&m.equals("glShaderSource")&&d.equals("(ILjava/lang/CharSequence;)V")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,name,"fileName","Ljava/lang/String;");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/OffscreenReflectionShaders","filter","(Ljava/lang/CharSequence;Ljava/lang/String;)Ljava/lang/CharSequence;",false);hits[0]++;
                        }
                        super.visitMethodInsn(op,owner,m,d,itf);if(visibility)call("movingVisible","(Ljava/lang/Object;Z)Z");
                        if(name.endsWith("/IsoWorld")&&owner.equals("zombie/core/Core")&&m.equals("getOffscreenWidth")&&d.equals("(I)I"))call("cullWidth","(I)I");
                    }
                    @Override public void visitInsn(int op){
                        if(op==Opcodes.RETURN&&name.equals("pzopt/Ssr")){
                            if(method.equals("beforeComposite")){super.visitVarInsn(Opcodes.ILOAD,0);call("queueBegin","(I)V");}
                            else if(method.equals("chunkDraw")){super.visitVarInsn(Opcodes.ALOAD,0);call("chunkDraw","(Lzombie/core/textures/TextureDraw;)V");}
                            else call("surfaceUniforms","()V");
                        }super.visitInsn(op);
                    }
                };
            }
        },0);
        if(hits[0]==0)throw new IOException("Offscreen source call shape missing: "+name);return writer.toByteArray();
    }
}
