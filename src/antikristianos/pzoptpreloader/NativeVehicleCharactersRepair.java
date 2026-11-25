package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.util.Map;
import net.bytebuddy.jar.asm.*;

/** Native 20f89d32 only, composed after packet ownership; preserve unknown material paths. */
public final class NativeVehicleCharactersRepair {
    private static final String OCC="pzopt/CarOccupant",GLASS="pzopt/CarGlass",HELPER="antikristianos/pzoptpreloader/NativeVehicleCharacters",MODEL="zombie/core/skinnedmodel/model/Model",SURFACES="antikristianos/pzoptpreloader/NativeVehicleSurfaces";
    private static final String SLOT=MODEL+"SlotRenderData";
    private static final Map<String,Map<String,String>> PINS=Map.of(
        OCC,Map.of("showStock(Lzombie/characters/IsoGameCharacter;)Z","5d2ffa0fe3fa23fb0b78ede70b2b841949d8b713fcecdf27a2af192278c81f54",
            "drawable(Lzombie/characters/IsoGameCharacter;Lzombie/vehicles/BaseVehicle;)Z","9e5ecdde65534ae14030f467ce50e7ce8aecbe97e81ed5bf0fb2dc27c5e6473b"),
        SLOT,Map.of("initModel(Lzombie/core/skinnedmodel/ModelManager$ModelSlot;)V","2d0d08e6d3769a0875d8c53b84cbcccfc2fe8949623a7948af44b8c8c2cf5905"),
        MODEL,Map.of("DrawSolid(Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;)V","73f574770d200935f7b42ab44f9b470bb6ae2eb3c155da46dafc1ec292dc7ede",
            "DrawVehicle(Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;)V","4c3c415736504c03e02d7a8b0daea1837d3a3b4ebfa4a30e46c658265884e746",
            "pzoptDrawGlass(Lzombie/core/skinnedmodel/shader/Shader;Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;Lzombie/core/skinnedmodel/model/VehicleModelInstance;FFFFFFF)V","a5e13e18d7c402a22c72b2c94aa1fd6bea0533dfd44c5dc8d6d2d7911e12665f"),
        GLASS,Map.of("patchShader(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;","d667f566b40c772336adcdc9c6c051ab7a4fb0e5d94fe148b4af83c27dff6995",
            "drawUniforms(Lzombie/core/skinnedmodel/shader/Shader;Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;Lzombie/core/skinnedmodel/model/VehicleModelInstance;FFFFFF)V","92e25aa7f4276d4cc755d59dbe6fa7abeb3d3053ff918800991221cbe3466bcc"));
    private NativeVehicleCharactersRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        Map<String,String> pins=PINS.get(name);if(pins==null)return bytes;
        ClassReader reader=new ClassReader(bytes);if(!name.equals(reader.getClassName()))throw new IOException("Native character material identity differs");
        var methods=Compatibility.methods(bytes);for(var pin:pins.entrySet())if(!pin.getValue().equals(methods.get(pin.getKey())))throw new IOException("Unreviewed native character material boundary: "+name+"."+pin.getKey());
        if(name.equals(MODEL))return model(reader);
        if(name.equals(SLOT))return snapshot(reader);
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
    private static byte[] model(ClassReader reader)throws IOException{
        int[] sites={0,0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String signature,String[] errors){
                MethodVisitor out=super.visitMethod(a,n,d,signature,errors);boolean main=n.equals("DrawVehicle"),glass=n.equals("pzoptDrawGlass");
                if(!PINS.get(MODEL).containsKey(n+d))return out;
                if(n.equals("DrawSolid"))return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean face){
                        if(op==Opcodes.INVOKEVIRTUAL&&owner.equals("zombie/core/skinnedmodel/shader/Shader")&&method.equals("startCharacter")&&desc.equals("(Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;)V")){
                            sites[2]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,SURFACES,"startCharacter","(Lzombie/core/skinnedmodel/shader/Shader;Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;)V",false);
                        }else super.visitMethodInsn(op,owner,method,desc,face);
                    }
                };
                return new MethodVisitor(Opcodes.ASM9,out){
                    final Label start=new Label(),end=new Label(),handler=new Label();
                    @Override public void visitCode(){super.visitCode();super.visitLabel(start);if(glass){sites[1]++;super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,2);ordinary();super.visitMethodInsn(Opcodes.INVOKESTATIC,SURFACES,"glass","(Lzombie/core/skinnedmodel/shader/Shader;Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Z)V",false);}}
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean face){
                        boolean draw=main&&op==Opcodes.INVOKEVIRTUAL&&owner.equals("zombie/core/skinnedmodel/model/ModelMesh")&&method.equals("Draw")&&desc.equals("(Lzombie/core/skinnedmodel/shader/Shader;)V");
                        if(draw){sites[0]++;super.visitInsn(Opcodes.DUP);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,2);ordinary();super.visitMethodInsn(Opcodes.INVOKESTATIC,SURFACES,"body","(Lzombie/core/skinnedmodel/shader/Shader;Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/model/ModelInstanceRenderData;Z)V",false);}
                        super.visitMethodInsn(op,owner,method,desc,face);if(draw)super.visitMethodInsn(Opcodes.INVOKESTATIC,SURFACES,"bodyEnd","()V",false);
                    }
                    @Override public void visitInsn(int op){if(op==Opcodes.RETURN)super.visitMethodInsn(Opcodes.INVOKESTATIC,SURFACES,"end","()V",false);super.visitInsn(op);}
                    @Override public void visitMaxs(int stack,int locals){
                        super.visitLabel(end);super.visitTryCatchBlock(start,end,handler,null);super.visitLabel(handler);
                        Object[] args=main?new Object[]{MODEL,"zombie/core/skinnedmodel/model/ModelSlotRenderData","zombie/core/skinnedmodel/model/ModelInstanceRenderData"}:new Object[]{MODEL,"zombie/core/skinnedmodel/shader/Shader","zombie/core/skinnedmodel/model/ModelSlotRenderData","zombie/core/skinnedmodel/model/ModelInstanceRenderData","zombie/core/skinnedmodel/model/VehicleModelInstance",Opcodes.FLOAT,Opcodes.FLOAT,Opcodes.FLOAT,Opcodes.FLOAT,Opcodes.FLOAT,Opcodes.FLOAT,Opcodes.FLOAT};
                        super.visitFrame(Opcodes.F_FULL,args.length,args,1,new Object[]{"java/lang/Throwable"});super.visitMethodInsn(Opcodes.INVOKESTATIC,SURFACES,"end","()V",false);super.visitInsn(Opcodes.ATHROW);super.visitMaxs(stack,locals);
                    }
                    private void ordinary(){super.visitMethodInsn(Opcodes.INVOKESTATIC,OCC,"impostor","()Z",false);super.visitInsn(Opcodes.ACONST_NULL);super.visitMethodInsn(Opcodes.INVOKESTATIC,OCC,"showStock","(Lzombie/characters/IsoGameCharacter;)Z",false);super.visitInsn(Opcodes.IOR);}
                };
            }
        },0);
        if(sites[0]!=2||sites[1]!=1||sites[2]!=1)throw new IOException("Native surface draw boundaries differ");return writer.toByteArray();
    }
    private static byte[] snapshot(ClassReader reader)throws IOException{
        int[] sites={0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] errors){
                MethodVisitor out=super.visitMethod(access,name,desc,signature,errors);if(!PINS.get(SLOT).containsKey(name+desc))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitInsn(int opcode){
                        if(opcode==Opcodes.RETURN){sites[0]++;super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,OCC,"impostor","()Z",false);super.visitInsn(Opcodes.ACONST_NULL);super.visitMethodInsn(Opcodes.INVOKESTATIC,OCC,"showStock","(Lzombie/characters/IsoGameCharacter;)Z",false);super.visitInsn(Opcodes.IOR);super.visitMethodInsn(Opcodes.INVOKESTATIC,SURFACES,"snapshot","(Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/core/skinnedmodel/ModelManager$ModelSlot;Z)V",false);}
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);if(sites[0]<1)throw new IOException("Native actor snapshot boundary differs");return writer.toByteArray();
    }
}
