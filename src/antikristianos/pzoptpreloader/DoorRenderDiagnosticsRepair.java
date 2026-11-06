package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Observation only. Pins apply to the prepared original; shapes apply after the existing repair chain. */
public final class DoorRenderDiagnosticsRepair {
    public static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell", SHADER="zombie/viewCone/ChunkRenderShader", RING="zombie/core/SpriteRenderer$RingBuffer";
    private static final String STATE="antikristianos/pzoptpreloader/DoorRenderDiagnostics";
    private static final String LEVEL="(Lzombie/iso/IsoChunk;ILzombie/iso/IsoCell$PerPlayerRender;IJLzombie/core/opengl/Shader;Lzombie/core/opengl/Shader;)V";
    private static final Map<String,Map<String,Set<String>>> PINS=Map.of(
        CELL,Map.of("calculateObjectRenderInfo(ILzombie/iso/IsoGridSquare;Lzombie/iso/IsoObject;)V",Set.of("8adef0e2d76dff3816f3fce36dad1980c61e522a3a886eab722d730f0cd453bb"),
            "renderMinusFloor_DoorOrWall(Lzombie/iso/IsoObject;)V",Set.of("bebeadaa4263143db5f50e98e1196d2493d7a06ca03556fecc0a6e2bdc4c63a8"),
            "renderOneLevel"+LEVEL,Set.of("45fdd97b5d63f4600a676cb219e2b2c9e8df27a884e1587971af6eb9d4954a96","6c456a1e73ecd969415bd948cc27b478979080ce641ec402d6f53f13f2e926a8"),
            "Reset()V",Set.of("2df3e4f8f0c0765c5d07b4f254b8dfe1bc3478ed80f5ab2bf6d7a32dfd784ec7")),
        SHADER,Map.of("startRenderThread(Lzombie/core/textures/TextureDraw;)V",Set.of("65f608b00cdd2e232a6849b8516a888ab40cd2402da0c8622cbedfd98c4270de")),
        RING,Map.of("drawElements(IIII)V",Set.of("cd05cdeb11766593a902dabf6af1a021e18bbd124df888c8dac16ed1cb13a7b2")));
    private DoorRenderDiagnosticsRepair() { }
    public static byte[] patch(String name,byte[] bytes,byte[] original)throws IOException {
        var pins=PINS.get(name);if(pins==null)return bytes;
        if(!new ClassReader(original).getClassName().equals(name)||!new ClassReader(bytes).getClassName().equals(name))throw new IOException("Door diagnostic class identity");
        var hashes=Compatibility.methods(original);for(var pin:pins.entrySet()){String hash=hashes.get(pin.getKey());if(hash==null||!pin.getValue().contains(hash))throw new IOException("Unsupported door diagnostic boundary: "+name+"."+pin.getKey());}
        int[] hits=new int[7];boolean[] repeated={false};ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
                @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                    if(owner.equals(STATE))repeated[0]=true;
                    if(name.equals(CELL)&&n.equals("renderOneLevel")&&d.equals(LEVEL)&&owner.equals("zombie/iso/fboRenderChunk/FBORenderChunkManager")&&method.equals("endRenderChunkLevel")&&desc.equals("(Lzombie/iso/IsoChunk;IFZ)V")){
                        super.visitInsn(Opcodes.DUP);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ILOAD,2);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"baked","(ZLzombie/iso/IsoChunk;I)V",false);hits[2]++;
                    }
                    super.visitMethodInsn(op,owner,method,desc,itf);
                    if(name.equals(CELL)&&n.equals("renderOneLevel")&&d.equals(LEVEL)&&owner.equals("zombie/iso/fboRenderChunk/FBORenderChunkManager")&&method.equals("beginRenderChunkLevel")&&desc.equals("(Lzombie/iso/IsoChunk;IFZZ)Z")){
                        super.visitInsn(Opcodes.DUP);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ILOAD,2);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"begin","(ZLzombie/iso/IsoChunk;I)V",false);hits[1]++;
                    }
                    if(name.equals(CELL)&&n.equals("renderMinusFloor_DoorOrWall")&&owner.equals("zombie/iso/IsoGridSquare")&&Set.of("DoWallLightingN","DoWallLightingW","DoWallLightingNW").contains(method)){
                        super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"drawn","(Lzombie/iso/IsoObject;)V",false);hits[3]++;
                    }
                    if(name.equals(SHADER)&&n.equals("startRenderThread")&&owner.equals("pzopt/Ssr")&&method.equals("chunkDraw")&&desc.equals("(Lzombie/core/textures/TextureDraw;)V")){
                        hits[4]++;
                    }
                    if(name.equals(RING)&&n.equals("drawElements")&&d.equals("(IIII)V")&&method.equals("drawElements")&&owner.equals("antikristianos/pzoptpreloader/WorldSunLayerCapture")&&desc.equals("(IIIIIJ)V")){
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"gpuDraw","()V",false);hits[5]++;
                    }
                }
                @Override public void visitInsn(int op){
                    if(name.equals(SHADER)&&n.equals("startRenderThread")&&d.equals("(Lzombie/core/textures/TextureDraw;)V")&&op==Opcodes.RETURN){
                        // Sway/Relief/Cloud may select a different program after Ssr.chunkDraw.
                        super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"composite","(Lzombie/core/textures/TextureDraw;)V",false);hits[6]++;
                    }
                    if(name.equals(CELL)&&op==Opcodes.RETURN&&n.equals("calculateObjectRenderInfo")&&d.equals("(ILzombie/iso/IsoGridSquare;Lzombie/iso/IsoObject;)V")){
                        super.visitVarInsn(Opcodes.ALOAD,3);super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"decision","(Lzombie/iso/IsoObject;)V",false);hits[0]++;
                    }
                    if(name.equals(CELL)&&op==Opcodes.RETURN&&n.equals("Reset")&&d.equals("()V"))super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"clear","()V",false);
                    super.visitInsn(op);
                }
            };}
        },0);
        if(repeated[0]||(name.equals(CELL)?hits[0]!=1||hits[1]!=1||hits[2]<2||hits[3]!=4:name.equals(SHADER)?hits[4]!=1||hits[6]!=1:hits[5]!=1))throw new IOException("Unsupported/repeated door diagnostic call shape: "+name+" "+Arrays.toString(hits));
        return writer.toByteArray();
    }
}
