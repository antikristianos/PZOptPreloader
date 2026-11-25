package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** The local fog depth target has a different origin from the player's scene scissor. */
public final class FogDepthCopyRepair {
    private static final String TARGET="pzopt/FogPass$Gl",DESC="(Ljava/nio/ByteBuffer;[FLzombie/core/textures/Texture;)Z";
    // Raw native input and its verified visual-exclusion composition, identical on both profiles.
    private static final java.util.Set<String> PINS=java.util.Set.of(
        "d3a032e7c7c89b30c63d4629578a915178148f5ed3c72a24f446181668806ad2",
        "30c1b6b09e5eba28c3425dfef5b9a4697c0dd4a2a649ffc07c18f317d029b1db");
    private FogDepthCopyRepair(){ }
    public static void blit(int x0,int y0,int x1,int y1,int dx0,int dy0,int dx1,int dy1,int mask,int filter){
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if(scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try{GL30.glBlitFramebuffer(x0,y0,x1,y1,dx0,dy0,dx1,dy1,mask,filter);}
        finally{if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}
    }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!name.equals(reader.getClassName())||!PINS.contains(Compatibility.methods(bytes).get("render"+DESC)))throw new IOException("Unsupported fog depth-copy boundary: "+name);
        int[] sites={0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!n.equals("render")||!d.equals(DESC))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
                        if(op==Opcodes.INVOKESTATIC&&owner.equals("org/lwjgl/opengl/GL30")&&method.equals("glBlitFramebuffer")&&descriptor.equals("(IIIIIIIIII)V")){
                            super.visitMethodInsn(op,"antikristianos/pzoptpreloader/FogDepthCopyRepair","blit",descriptor,false);sites[0]++;return;
                        }
                        super.visitMethodInsn(op,owner,method,descriptor,itf);
                    }
                };
            }
        },0);
        if(sites[0]!=1)throw new IOException("Fog depth-copy inventory differs");return writer.toByteArray();
    }
}
