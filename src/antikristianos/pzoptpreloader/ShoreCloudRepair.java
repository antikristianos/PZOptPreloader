package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** The extended source shades shore vertices too; avoid adding that to own per-pixel clouds. */
public final class ShoreCloudRepair {
    private static final String CELL="zombie/iso/fboRenderChunk/FBORenderCell",METHOD="pzoptRenderFloor",DESC="(Lzombie/iso/IsoObject;)V";
    private ShoreCloudRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!name.equals(CELL))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!reader.getClassName().equals(CELL)||!"7257598516e1cf2dd00beaaf5b5104c081b946217996eee67b07790ae8339da7".equals(Compatibility.methods(bytes).get(METHOD+DESC)))throw new IOException("Unsupported native shore vertex cloud boundary");
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
            @Override public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
                if(n.equals(METHOD)&&d.equals(DESC)&&op==Opcodes.INVOKESTATIC&&owner.equals("pzopt/CloudShadow")&&method.equals("shoreLight")&&descriptor.equals("(IFFF)I")){
                    super.visitMethodInsn(op,"antikristianos/pzoptpreloader/ShoreFloorLighting","vertexCloud",descriptor,false);hits[0]++;return;
                }super.visitMethodInsn(op,owner,method,descriptor,itf);
            }
        };}},0);
        if(hits[0]!=4)throw new IOException("Expected four native shore vertex colours, got "+hits[0]);return writer.toByteArray();
    }
}
