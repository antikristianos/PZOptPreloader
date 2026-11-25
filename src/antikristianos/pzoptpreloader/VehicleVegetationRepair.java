package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** Update local selection before the native obstruction/cache invalidation stage. */
public final class VehicleVegetationRepair {
    private static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell",METHOD="checkObjectsObscuringPlayer",DESC="(I)V";
    private static final String PIN="cb5c18f50d4bc4b361acdeae78aeaabf80d1437b2128e9ae5a1d14e04f72cf97";
    private static final String SPRITE="zombie/iso/sprite/IsoSprite",DRAW="renderCurrentAnim_FBORender";
    private static final String DRAW_DESC="(Lzombie/iso/sprite/IsoSpriteInstance;Lzombie/iso/IsoObject;FFFLzombie/iso/IsoDirections;FFLzombie/core/textures/ColorInfo;ZLjava/util/function/Consumer;)V";
    private static final java.util.Set<String> DRAW_PINS=java.util.Set.of(
        "d0fde9eba3dbb49b2ab2acf021cec3b35aee88e50a6c89c0f06a2d874fa5cf6f",
        "c9f4ab66bbbd3a0e0bac094f7c109a2915f2f5726932e33267d62fc302f8acb7");
    private VehicleVegetationRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(name.equals(SPRITE))return sprite(bytes);
        if(!name.equals(CELL))return bytes;
        if(!PIN.equals(Compatibility.methods(bytes).get(METHOD+DESC)))throw new IOException("Unreviewed vehicle vegetation/cache boundary");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
            MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!n.equals(METHOD)||!d.equals(DESC))return out;
            return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){super.visitCode();super.visitVarInsn(Opcodes.ILOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/VehicleVegetation","update","(I)V",false);hits[0]++;}};
        }},0);
        if(hits[0]!=1)throw new IOException("Vehicle vegetation/cache shape differs");return writer.toByteArray();
    }
    private static byte[] sprite(byte[] bytes)throws IOException{
        if(!DRAW_PINS.contains(Compatibility.methods(bytes).get(DRAW+DRAW_DESC)))throw new IOException("Unreviewed vehicle vegetation sprite depth boundary");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
            MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!n.equals(DRAW)||!d.equals(DRAW_DESC))return out;
            return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean face){
                if(op==Opcodes.INVOKESTATIC&&owner.equals("zombie/IndieGL")&&method.equals("glDepthFunc")&&desc.equals("(I)V")){
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,CELL,"pzoptTranslucentOnly","()Z",false);super.visitVarInsn(Opcodes.ALOAD,2);super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/VehicleVegetation","depthFunc","(IZLzombie/iso/IsoObject;)I",false);hits[0]++;
                }
                super.visitMethodInsn(op,owner,method,desc,face);
            }};
        }},0);
        if(hits[0]!=3)throw new IOException("Vehicle vegetation sprite depth call inventory differs: "+hits[0]);return writer.toByteArray();
    }
}
