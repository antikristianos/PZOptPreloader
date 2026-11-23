package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Map;
import net.bytebuddy.jar.asm.*;

/** Native 20f89d32 only, composed after packet ownership; preserve unknown material paths. */
public final class NativeVehicleCharactersRepair {
    private static final String OCC="pzopt/CarOccupant",GLASS="pzopt/CarGlass",HELPER="antikristianos/pzoptpreloader/NativeVehicleCharacters";
    private static final Map<String,Map<String,String>> PINS=Map.of(
        OCC,Map.of("showStock(Lzombie/characters/IsoGameCharacter;)Z","5d2ffa0fe3fa23fb0b78ede70b2b841949d8b713fcecdf27a2af192278c81f54",
            "drawable(Lzombie/characters/IsoGameCharacter;Lzombie/vehicles/BaseVehicle;)Z","9e5ecdde65534ae14030f467ce50e7ce8aecbe97e81ed5bf0fb2dc27c5e6473b"),
        GLASS,Map.of("patchShader(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;","d667f566b40c772336adcdc9c6c051ab7a4fb0e5d94fe148b4af83c27dff6995",
            "drawUniforms(Lzombie/core/skinnedmodel/shader/Shader;Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;Lzombie/core/skinnedmodel/model/VehicleModelInstance;FFFFFF)V","92e25aa7f4276d4cc755d59dbe6fa7abeb3d3053ff918800991221cbe3466bcc"));
    private NativeVehicleCharactersRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,String> pins=PINS.get(name);if(pins==null)return bytes;
        ClassReader reader=new ClassReader(bytes);if(!name.equals(reader.getClassName()))throw new IOException("Native character material identity differs");
        var methods=Compatibility.methods(bytes);for(var pin:pins.entrySet())if(!pin.getValue().equals(methods.get(pin.getKey())))throw new IOException("Unreviewed native character material boundary: "+name+"."+pin.getKey());
        int[] sites={0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!pins.containsKey(n+d))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitInsn(int op) {
                        if(name.equals(OCC)&&n.equals("showStock")&&op==Opcodes.IRETURN){
                            sites[0]++;super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,OCC,"impostor","()Z",false);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"show","(ZLzombie/characters/IsoGameCharacter;Z)Z",false);
                        }else if(name.equals(OCC)&&n.equals("drawable")&&op==Opcodes.IRETURN){
                            sites[1]++;super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"drawable","(ZLzombie/vehicles/BaseVehicle;)Z",false);
                        }else if(name.equals(GLASS)&&n.equals("patchShader")&&op==Opcodes.ARETURN){
                            sites[0]++;super.visitVarInsn(Opcodes.ALOAD,0);super.visitInsn(Opcodes.SWAP);
                            ordinary();
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"shader","(Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/String;",false);
                        }
                        super.visitInsn(op);
                    }
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean face) {
                        super.visitMethodInsn(op,owner,method,desc,face);
                        if(!name.equals(GLASS)||!n.equals("drawUniforms")||op!=Opcodes.INVOKESTATIC||!owner.equals(OCC))return;
                        if(method.equals("tileFor")&&desc.equals("(Lzombie/vehicles/BaseVehicle;JI)Lpzopt/CarOccupant$Tile;")){
                            sites[1]++;object();ordinary();super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"tile","(Ljava/lang/Object;Ljava/lang/Object;Z)Ljava/lang/Object;",false);super.visitTypeInsn(Opcodes.CHECKCAST,OCC+"$Tile");
                        }else if(method.equals("seatProxy")&&desc.equals("()F")){
                            sites[1]++;object();ordinary();super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"proxy","(FLjava/lang/Object;Z)F",false);
                        }
                    }
                    private void object(){super.visitVarInsn(Opcodes.ALOAD,1);super.visitFieldInsn(Opcodes.GETFIELD,"zombie/core/skinnedmodel/model/ModelSlotRenderData","object","Lzombie/iso/IsoMovingObject;");}
                    private void ordinary(){super.visitMethodInsn(Opcodes.INVOKESTATIC,OCC,"impostor","()Z",false);super.visitInsn(Opcodes.ACONST_NULL);super.visitMethodInsn(Opcodes.INVOKESTATIC,OCC,"showStock","(Lzombie/characters/IsoGameCharacter;)Z",false);super.visitInsn(Opcodes.IOR);}
                };
            }
        },0);
        // Return counts and the two material consumers are tied to the reviewed method bodies.
        if(name.equals(OCC)?sites[0]!=1||sites[1]<1:sites[0]<1||sites[1]!=2)throw new IOException("Native character material sites differ");
        return writer.toByteArray();
    }
}
