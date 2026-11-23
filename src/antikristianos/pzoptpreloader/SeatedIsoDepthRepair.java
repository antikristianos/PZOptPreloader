package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** Exactly the snapshot's initial sorting-height read; later physical seat coordinates remain native. */
public final class SeatedIsoDepthRepair {
    public static final String TARGET="zombie/core/skinnedmodel/model/ModelSlotRenderData";
    private SeatedIsoDepthRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException{
        if(!TARGET.equals(name))return bytes;
        if(!"df5c6ba564c5d4154fbecf742a83488a7554829898f6968b1dde1d0edcda1c34".equals(Compatibility.methods(bytes).get("initModel(Lzombie/core/skinnedmodel/ModelManager$ModelSlot;)V")))throw new IOException("Unreviewed ISO model snapshot height reader");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,0);int[] reads={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,method,descriptor,signature,exceptions);
                if(!method.equals("initModel")||!descriptor.equals("(Lzombie/core/skinnedmodel/ModelManager$ModelSlot;)V"))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean face){
                        if(opcode==Opcodes.INVOKEVIRTUAL&&owner.equals("zombie/iso/IsoMovingObject")&&method.equals("getZ")&&descriptor.equals("()F")){
                            reads[0]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,"antikristianos/pzoptpreloader/SeatedIsoDepth","sortingZ","(Lzombie/iso/IsoMovingObject;)F",false);
                        }else super.visitMethodInsn(opcode,owner,method,descriptor,face);
                    }
                };
            }
        },0);
        if(reads[0]!=1)throw new IOException("ISO model snapshot height read differs");
        return writer.toByteArray();
    }
}
