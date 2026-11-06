package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Final pinned adapter: original AO is cached; static sunlight belongs to separate receiver layers. */
public final class WorldSunLayerRepair {
    private static final String AO="pzopt/ChunkAo",GL=AO+"$Gl",SHADERS="antikristianos/pzoptpreloader/WorldSunLayerShaders",STATE="antikristianos/pzoptpreloader/WorldSunLayerState";
    private static final Map<String,Map<String,Set<String>>> PINS=Map.of(
        AO,Map.of("<clinit>()V",Set.of("72976132ffcba319ba59a7d4959f6da4d4a6810b00188799adafcd62f5c0c8d5","5f96c3622dd58fb857066efb32609fc30a5910abc1e156fd06b248e788f108ec"),
            "applyStaged(IZ)V",Set.of("f64eb83eed5aa3c1a8b09c47fcef940cf1ab9c555d93f4dd5efebd059c38eb3d")),
        "pzopt/SunShadow",Map.of("stepApplied([FF)V",Set.of("47c6c60f7b1491ff59341fed86c6e839bd623fdf6add415d50567c7861a1060a"),"updateShown()V",Set.of("494bc50dd9f09ec5145d7882e6b5be22e631821a9737be5447483a7ab4456630"),"update()Z",Set.of("fea2d17c2d0c47f114a1652490b0c3b3b7d3756084dacfdffc0719c38a99bf90")),
        GL,Map.of(
            "compute(Lpzopt/ChunkAo$Job;)V",Set.of("39afbf566ff9c6c3dc9be47ad2c820cddd74e52e0e6a42e54fdc7244494c466d","3a902ecac4b7bfcf31135004214de160c6ea072a677d5becc23eba29e3f866df"),
            "applyStaged(Lpzopt/ChunkAo$Job;)V",Set.of("de34c2a5e0372a5b8b83cb17e99bbe24ddbfb74d400ece80190045b2b6805524","c6663551884990ce1e596dae6777e87180867ac9dc9d793cf18b2cb81896fcab"),
            "drawMultiply(Lpzopt/ChunkAo$Job;Lpzopt/ChunkAo$Entry;FII)V",Set.of("459cec2ae6e6d7672ad29b1eef9bc3953fec7e0c971091a6f6c3d0c5d86d7313","1f266bd7872309550b2d7c40853efedda5a4c81befe713fdf676956ee5489d9e"),
            "quietTexture(II)I",Set.of("a86cfd677f4537b148510bec3aa1fc7dc235ebb36ea5f89cd626ece4014d65e1"),
            "keptTexture(II)I",Set.of("d3afac3c1ecc7643c5a651ced94dd512e45fad9bdada2185f947bbe746fda714"),
            "ensureScratch(II)Z",Set.of("6f1acaf8ada327ed95adf64936e555d6cbec266bbd6fd82ce52280424cb2b13e"),
            "keepForFade(Lpzopt/ChunkAo$Entry;IIJ)V",Set.of("571298f44099b3b9afd1ce4941f8deda89698a7f90c148ad44a2cfbb37d2d29f"),
            "clear()V",Set.of("b348b90e5866c1ea879c80cf5d624af06d3f2db7f762c8c9c0ebfa554c1753f2"),
            "init()Z",Set.of("6382a2120340cb4ccca53d011ab33fa2629ba5fe463483f94b1a9ef12024e5ef","e8478c17c6418d31b45ae57778adf487ff49ceb2e12b1af055dac4ac44374d48")),
        "zombie/core/opengl/ShaderUnit",Map.of("compile()Z",Set.of("77024dad622fe5c701359d39a846d828e34ceaf0a3f07bb65239d7267eaacddd","8b32aafa6c5d9d2233187cebde1747b664b6b60a93306349da5da8f415ecfc8f")),
        "pzopt/Sway",Map.of("afterComposite()V",Set.of("ac3e1379ddbac21c976de353e3966d33b0ef1394d7922fa1c6bbb0f2fe1eb456")),
        "pzopt/SpriteMips",Map.of("pass(III)V",Set.of("19ce5da634b83ceda357f8fbf3877d79a1784609ce75cf543e0553407349e80d")),
        "zombie/core/SpriteRenderer$RingBuffer",Map.of("drawElements(IIII)V",Set.of("471a5954a059f05aa68f5458c92fc4cb5293d503671791c8ccb215c3e4805005")),
        "zombie/iso/fboRenderChunk/FBORenderCell",Map.of("pzoptPreloaderFinishShoreReflections()V",Set.of("603d0aea435c9b740d99d1afa12c5d7cac21c4b15f16a6ae0e2428e6497e59e5")));
    private WorldSunLayerRepair(){ }
    public static boolean nativeMipReady(){return WorldSunLayerShaders.enabled()&&!RuntimeState.server&&RuntimeState.payload!=null&&NativeMip.PASS;}
    private static final class NativeMip {
        static final boolean PASS=verify();
        static boolean verify(){try(java.util.jar.JarFile game=new java.util.jar.JarFile(Compatibility.gameJar().toFile())){
            try(java.io.InputStream in=game.getInputStream(game.getJarEntry("zombie/iso/fboRenderChunk/FBORenderChunk.class"))){return "27886ac752c6a136b7f8887e16d0e2fe2d87cffb174df6ed39a55d608eec3dac".equals(Compatibility.methods(in.readAllBytes()).get("endRenderThread()V"));}
        }catch(Exception error){throw new IllegalStateException("Native world sunlight mip boundary",error);}}
    }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,Set<String>> pins=PINS.get(name);if(pins==null)return bytes;
        Map<String,String> actual=Compatibility.methods(bytes);for(var pin:pins.entrySet())if(!pin.getValue().contains(actual.get(pin.getKey())))throw new IOException("Changed sun layer input: "+name+"."+pin.getKey());
        ClassReader r=new ClassReader(bytes);ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);int[] hits=new int[4];
        r.accept(new ClassVisitor(Opcodes.ASM9,w){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] errors){
                MethodVisitor out=super.visitMethod(access,method,desc,signature,errors);if(!pins.containsKey(method+desc))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitCode(){super.visitCode();if(name.endsWith("/FBORenderCell"))super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/WorldSunLayerCapture","queueApply","()V",false);if(name.equals("pzopt/SunShadow")){
                        if(method.equals("update")){super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"consumeShown","()V",false);Label normal=new Label();super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/WorldSunLayerSources","allowUpdate","()Z",false);super.visitJumpInsn(Opcodes.IFNE,normal);super.visitInsn(Opcodes.ICONST_0);super.visitInsn(Opcodes.IRETURN);super.visitLabel(normal);super.visitFrame(Opcodes.F_SAME,0,null,0,null);return;}
                        if(method.equals("updateShown"))super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"consumeShown","()V",false);
                        else {Label normal=new Label();super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"allowShown","()Z",false);super.visitJumpInsn(Opcodes.IFNE,normal);super.visitInsn(Opcodes.RETURN);super.visitLabel(normal);super.visitFrame(Opcodes.F_SAME,0,null,0,null);}
                    }}
                    @Override public void visitFieldInsn(int op,String owner,String field,String d){
                        if(name.equals(GL)&&method.equals("applyStaged")&&op==Opcodes.GETFIELD&&owner.equals(AO+"$Entry")&&field.equals("sFbo")){
                            super.visitInsn(Opcodes.DUP);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"ratioEntry","(Ljava/lang/Object;)V",false);
                        }
                        if(name.equals(AO)&&op==Opcodes.PUTSTATIC&&owner.equals(AO)&&d.equals("Ljava/lang/String;")){
                            String adapter=switch(field){case "BLUR_FRAG"->"blur";case "MUL_FRAG"->"multiply";case "COPY_FRAG"->"copy";case "RATIO_FRAG"->"ratio";default->null;};
                            if(adapter!=null){super.visitMethodInsn(Opcodes.INVOKESTATIC,SHADERS,adapter,"(Ljava/lang/String;)Ljava/lang/String;",false);hits[0]++;}
                        }super.visitFieldInsn(op,owner,field,d);
                        if(name.equals(GL)&&method.equals("init")&&op==Opcodes.GETSTATIC&&owner.equals(AO)&&field.equals("BLUR_FRAG"))super.visitMethodInsn(Opcodes.INVOKESTATIC,SHADERS,"linkBlur","(Ljava/lang/String;)Ljava/lang/String;",false);
                    }
                    @Override public void visitLdcInsn(Object value){
                        if(name.equals(GL)&&value instanceof Integer i&&i==GL30.GL_RG8){value=GL11.GL_RGBA8;hits[1]++;}
                        super.visitLdcInsn(value);
                    }
                    @Override public void visitMethodInsn(int op,String owner,String m,String d,boolean itf){
                        if(name.equals(GL)&&owner.equals("antikristianos/pzoptpreloader/WallReflectionLighting")&&m.equals("drawArrays")&&d.equals("(IIILjava/lang/Object;Ljava/lang/Object;)V")){
                            super.visitMethodInsn(op,"antikristianos/pzoptpreloader/WorldSunLayerSources","drawArrays",d,itf);return;
                        }
                        if(name.endsWith("SpriteRenderer$RingBuffer")&&owner.equals("antikristianos/pzoptpreloader/WallReflectionSource")&&m.equals("drawElements")&&d.equals("(IIIIIJ)V")){
                            super.visitMethodInsn(op,"antikristianos/pzoptpreloader/WorldSunLayerCapture",m,d,itf);hits[2]++;return;
                        }
                        if(name.equals(GL)&&method.equals("applyStaged")&&owner.equals("org/lwjgl/opengl/GL11")&&m.equals("glDrawArrays")&&d.equals("(III)V")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"ratioDraw","(IIILjava/lang/Object;)V",false);return;
                        }
                        if(name.equals(AO)&&method.equals("applyStaged")&&owner.equals("zombie/core/SpriteRenderer")&&m.equals("drawGeneric")){
                            super.visitInsn(Opcodes.DUP);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"queued","(Ljava/lang/Object;)V",false);
                        }
                        if(name.endsWith("/ShaderUnit")&&owner.equals("antikristianos/pzoptpreloader/OffscreenReflectionShaders")&&m.equals("filter")&&d.equals("(Ljava/lang/CharSequence;Ljava/lang/String;)Ljava/lang/CharSequence;")){
                            super.visitMethodInsn(op,SHADERS,"shaderAndOffscreen",d,itf);hits[2]++;return;
                        }
                        if(name.equals(GL)&&method.equals("keepForFade")&&owner.equals("org/lwjgl/opengl/GL11")&&m.equals("glColorMask")&&d.equals("(ZZZZ)V")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SHADERS,"fadeMask",d,false);return;
                        }
                        super.visitMethodInsn(op,owner,m,d,itf);
                        if(name.equals("pzopt/SunShadow")&&method.equals("stepApplied")&&owner.equals("pzopt/CloudShadow")&&m.equals("stepFadeSupported"))super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"fadeSupported","(Z)Z",false);
                    }
                    @Override public void visitInsn(int op){
                        if(name.equals("pzopt/SunShadow")&&method.equals("update")&&op==Opcodes.IRETURN)super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/WorldSunLayerSources","admitted","(Z)Z",false);
                        if(name.equals("pzopt/SpriteMips")&&op==Opcodes.RETURN){super.visitVarInsn(Opcodes.ILOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/WorldSunLayerSources","sharp","(I)V",false);}
                        if(name.equals("pzopt/Sway")&&op==Opcodes.RETURN){super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/WorldSunLayerCapture","queueApply","()V",false);hits[3]++;}
                        if(name.equals(GL)&&method.equals("init")&&op==Opcodes.IRETURN&&actual.get("init()Z").equals("6382a2120340cb4ccca53d011ab33fa2629ba5fe463483f94b1a9ef12024e5ef")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"repairLegacyUniform","(ZLjava/lang/Object;)Z",false);
                        }
                        if(name.equals(GL)&&op==Opcodes.RETURN){
                            if(method.equals("compute")||method.equals("applyStaged")){super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"changed","(Ljava/lang/Object;Ljava/lang/Object;)V",false);hits[3]++;}
                            else if(method.equals("clear")){super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"clear","()V",false);hits[3]++;}
                        }super.visitInsn(op);
                    }
                };
            }
        },0);
        if(name.equals(AO)&&hits[0]!=4||name.equals(GL)&&(hits[1]!=3||hits[3]<3)||name.endsWith("/ShaderUnit")&&hits[2]!=1)throw new IOException("Sun adapter shape: "+name+" "+Arrays.toString(hits));
        return w.toByteArray();
    }
}
