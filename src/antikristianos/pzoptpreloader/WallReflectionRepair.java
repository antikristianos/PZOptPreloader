package antikristianos.pzoptpreloader;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import net.bytebuddy.jar.asm.*;

/** Narrow native render/installed-SSR composition; B42.22 client routing is owned by Payload. */
public final class WallReflectionRepair {
    public static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell",RING="zombie/core/SpriteRenderer$RingBuffer",SSR="pzopt/Ssr",AO="pzopt/ChunkAo$Gl";
    private static final String SOURCE="antikristianos/pzoptpreloader/WallReflectionSource";
    private static final String LEVEL="(Lzombie/iso/IsoChunk;ILzombie/iso/IsoCell$PerPlayerRender;IJLzombie/core/opengl/Shader;Lzombie/core/opengl/Shader;)V";
    private static final Map<String,Map<String,String>> PINS=Map.of(
        CELL,Map.of("renderOneLevel"+LEVEL,"45fdd97b5d63f4600a676cb219e2b2c9e8df27a884e1587971af6eb9d4954a96",
            "renderMinusFloor_DoorOrWall(Lzombie/iso/IsoObject;)V","bebeadaa4263143db5f50e98e1196d2493d7a06ca03556fecc0a6e2bdc4c63a8",
            "renderMinusFloor_NotDoorOrWall(Lzombie/iso/IsoObject;)V","31bb3e38e2db496ccc68488ed95b4c71405540a8e5b567a70220573774b208a1",
            "isTranslucentTree(Lzombie/iso/IsoObject;)Z","0fa4d47a5bbeb7862894605fef846e48ea47365e6fa0566593393402a98a27c8",
            "Reset()V","2df3e4f8f0c0765c5d07b4f254b8dfe1bc3478ed80f5ab2bf6d7a32dfd784ec7"),
        RING,Map.of("drawElements(IIII)V","cd05cdeb11766593a902dabf6af1a021e18bbd124df888c8dac16ed1cb13a7b2"),
        SSR,Map.of("chunkDraw(Lzombie/core/textures/TextureDraw;)V","2c0b5c9716924657030e8e605196d52caaacaf5ed9494477980568b3b697d6ce"),
        AO,Map.of("compute(Lpzopt/ChunkAo$Job;)V","dfa40fb5880c83bc9deb018e500d5f458fd9e002d2daf681692aeba50d1bfd40",
            "drawMultiply(Lpzopt/ChunkAo$Job;Lpzopt/ChunkAo$Entry;FII)V","17b07104bc722055f7c93ac9c8d21c49c9c54612263d0443374deb314f62af78"));
    private WallReflectionRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,String> pins=PINS.get(name);if(pins==null)return bytes;
        ClassReader reader=new ClassReader(bytes);if(!reader.getClassName().equals(name))throw new IOException("Physical wall class identity: "+name);
        Map<String,String> actual=Compatibility.methods(bytes);
        for(var pin:pins.entrySet())if(!RepairProfiles.matches(pin.getValue(),actual.get(pin.getKey())))throw new IOException("Unsupported physical-wall boundary: "+name+"."+pin.getKey());
        int[] hits=new int[6];ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
                    @Override public void visitCode(){
                        super.visitCode();if(name.equals(CELL)&&n.equals("renderOneLevel")&&d.equals(LEVEL)){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SOURCE,"drainRebuilds","()V",false);
                        }
                    }
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                        if(name.equals(AO)&&(n.equals("compute")||n.equals("drawMultiply"))&&owner.equals("org/lwjgl/opengl/GL11")&&method.equals("glDrawArrays")&&desc.equals("(III)V")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/WallReflectionLighting","drawArrays","(IIILjava/lang/Object;Ljava/lang/Object;)V",false);hits[4]++;return;
                        }
                        if(name.equals(CELL)&&n.equals("renderOneLevel")&&d.equals(LEVEL)&&owner.equals("zombie/iso/fboRenderChunk/FBORenderChunkManager")&&method.equals("endRenderChunkLevel")&&desc.equals("(Lzombie/iso/IsoChunk;IFZ)V")){
                            // Keep the native receiver and every argument on the operand stack.
                            super.visitInsn(Opcodes.DUP);super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ILOAD,2);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SOURCE,"captureLevel","(ZLjava/lang/Object;Lzombie/iso/IsoChunk;I)V",false);hits[0]++;
                        }
                        if(name.equals(RING)&&n.equals("drawElements")&&d.equals("(IIII)V")&&owner.equals("org/lwjgl/opengl/GL12")&&method.equals("glDrawRangeElements")&&desc.equals("(IIIIIJ)V")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SOURCE,"drawElements",desc,false);hits[1]++;return;
                        }
                        super.visitMethodInsn(op,owner,method,desc,itf);
                        if(name.equals(CELL)&&n.equals("renderMinusFloor_DoorOrWall")&&owner.equals("zombie/iso/IsoGridSquare")&&method.equals("getPlayerCutawayFlag")&&desc.equals("(IJ)I")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SOURCE,"cutaway","(I)I",false);hits[2]++;
                        }
                    }
                    @Override public void visitInsn(int op){
                        if(op==Opcodes.IRETURN&&name.equals(CELL)&&n.equals("isTranslucentTree"))super.visitMethodInsn(Opcodes.INVOKESTATIC,SOURCE,"translucentTree","(Z)Z",false);
                        if(op==Opcodes.RETURN&&name.equals(AO)&&(n.equals("compute")||n.equals("drawMultiply"))){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/WallReflectionLighting","after","(Ljava/lang/Object;Ljava/lang/Object;)V",false);hits[5]++;
                        }
                        if(op==Opcodes.RETURN&&name.equals(CELL)&&n.equals("Reset")&&d.equals("()V")){
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,SOURCE,"clear","()V",false);hits[3]++;
                        }
                        if(op==Opcodes.RETURN&&name.equals(SSR)&&n.equals("chunkDraw")&&d.equals("(Lzombie/core/textures/TextureDraw;)V")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,SOURCE,"composite",d,false);hits[3]++;
                        }
                        super.visitInsn(op);
                    }
                };
            }
        },0);
        if(name.equals(CELL)?hits[0]<1||hits[2]!=5||hits[3]!=1:name.equals(RING)?hits[1]!=1:name.equals(AO)?hits[4]<1||hits[5]<2:hits[3]<1)
            throw new IOException("Unsupported physical-wall call shape: "+name+" "+Arrays.toString(hits));
        return writer.toByteArray();
    }
    /** Buddy snapshots loaded targets before installing transformers; avoid loading Levels reentrantly inside Chunk advice. */
    public static void prepareNativeHooks(){
        if(!nativeReady())return;
        try{
            ClassLoader loader=WallReflectionRepair.class.getClassLoader();
            Class.forName("zombie.iso.fboRenderChunk.FBORenderLevels",false,loader);
            Class.forName("zombie.iso.fboRenderChunk.FBORenderChunk",false,loader);
        }catch(ClassNotFoundException error){throw new IllegalStateException("Native wall ownership target unavailable",error);}
    }
    public static void nativeBegin(Object owner,boolean clear){if(nativeReady())WallReflectionSource.beginBake((zombie.iso.fboRenderChunk.FBORenderChunk)owner,clear);}
    public static void nativeFree(Object owner){if(nativeReady())WallReflectionSource.free((zombie.iso.fboRenderChunk.FBORenderChunk)owner);}
    public static boolean nativeReady(){return !RuntimeState.server&&RuntimeState.payload!=null&&Compatibility.currentGame()&&NativePins.VALID;}
    private static final class NativePins {
        static final boolean VALID=check();
        static boolean check(){
            try(ZipFile game=new ZipFile(Compatibility.gameJar().toFile())){
                Map<String,Map<String,String>> pins=Map.of(
                    "zombie/iso/fboRenderChunk/FBORenderChunk",Map.of("beginRenderThread(Z)V","1844409b5a7b63b95eadaa8b35d07239e64907ba26c7203bc6ce571e9d7be6a4"),
                    "zombie/iso/fboRenderChunk/FBORenderLevels",Map.of("freeFBO(Lzombie/iso/fboRenderChunk/FBORenderChunk;)V","a8a94bf75833c5d597c97ce20aa1b4e2289e7d66bcae4245792a4eeb9bb0dbf4",
                        "clearCache(Lzombie/iso/fboRenderChunk/FBORenderChunk;)V","19794c089fabe0d0cc6bd4c13f7bd74f4df4ca1b37829e753936234b755c651d"));
                for(var target:pins.entrySet())try(InputStream in=game.getInputStream(game.getEntry(target.getKey()+".class"))){
                    Map<String,String> actual=Compatibility.methods(in.readAllBytes());for(var method:target.getValue().entrySet())if(!method.getValue().equals(actual.get(method.getKey())))return false;
                }
                return true;
            }catch(Exception error){System.err.println("[PZOptPreloader] Native physical-wall hooks unavailable: "+error);return false;}
        }
    }
}
