package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** Shared SSR images use full scene extents because scatter addresses absolute framebuffer pixels. */
public final class SsrHashExtentRepair {
    private static final String TARGET="pzopt/Ssr",METHOD="frameStart",DESC="(Lpzopt/Ssr$Frame;)V";
    private static final String PIN="4a0247f50de9d06c8640da0831e1183905d77ebd119aca28bbfacceba29ec525";
    private SsrHashExtentRepair(){ }
    public static int width(zombie.core.Core core,int player){
        return zombie.characters.IsoPlayer.numPlayers>1?core.getOffscreenTrueWidth():core.getOffscreenWidth(player);
    }
    public static int height(zombie.core.Core core,int player){
        return zombie.characters.IsoPlayer.numPlayers>1?core.getOffscreenTrueHeight():core.getOffscreenHeight(player);
    }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!TARGET.equals(reader.getClassName())||!PIN.equals(Compatibility.methods(bytes).get(METHOD+DESC)))
            throw new IOException("Unsupported split-screen SSR hash extent boundary: "+name);
        int[] hits={0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!METHOD.equals(n)||!DESC.equals(d))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitMethodInsn(int op,String owner,String method,String descriptor,boolean itf){
                        if(op==Opcodes.INVOKEVIRTUAL&&owner.equals("zombie/core/Core")&&descriptor.equals("(I)I")
                            &&(method.equals("getOffscreenWidth")||method.equals("getOffscreenHeight"))){
                            boolean width=method.equals("getOffscreenWidth");hits[width?0:1]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/SsrHashExtentRepair",width?"width":"height","(Lzombie/core/Core;I)I",false);return;
                        }
                        super.visitMethodInsn(op,owner,method,descriptor,itf);
                    }
                };
            }
        },0);
        if(hits[0]!=1||hits[1]!=1)throw new IOException("Split-screen SSR extent call inventory changed");
        return writer.toByteArray();
    }
}
