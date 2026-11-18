package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** The native mainThreadStep is replaced by VP; restore its budget lifecycle per actual step. */
public final class ViewpointPacingRepair {
    public static final String TARGET="viewpoint/game/FixedTick";
    public static final String RESOURCE="fbec3fbbeb2370df946bcc34e3bcd15cf14e84ae3841f4ed033081fd85ff71ea";
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointPacingCompatibility";
    private ViewpointPacingRepair(){ }
    public static void verify(byte[] bytes)throws IOException{
        var methods=Compatibility.methods(bytes);
        if(!TARGET.equals(new ClassReader(bytes).getClassName())||!"08ff638a320af6a1176c9373fd3a4409020f1d94da87a4401e1f753ee9e0c480".equals(methods.get("step()V"))||!"cb403bd5138c58a1ec34aeb3dc8ff570b932c3af5f9c1428e206aa7ef19263d1".equals(methods.get("mainStep(ZLjava/util/function/LongPredicate;)Z")))throw new IOException("Unreviewed Viewpoint fixed-step boundary");
    }
    public static byte[] patch(byte[] bytes)throws IOException{
        verify(bytes);ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] sites={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!n.equals("step")||!d.equals("()V"))return out;return new MethodVisitor(Opcodes.ASM9,out){
            @Override public void visitCode(){super.visitCode();sites[0]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"begin","()J",false);super.visitVarInsn(Opcodes.LSTORE,1);}
            @Override public void visitFrame(int type,int count,Object[] locals,int stack,Object[] values){if(count>1)throw new IllegalStateException("Viewpoint step frame changed");Object[] extended={count==0?Opcodes.TOP:locals[0],Opcodes.LONG};super.visitFrame(type,2,extended,stack,values);}
            @Override public void visitInsn(int op){if(op==Opcodes.RETURN){super.visitVarInsn(Opcodes.LLOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"end","(J)V",false);}super.visitInsn(op);}
            @Override public void visitMaxs(int stack,int locals){if(locals!=1)throw new IllegalStateException("Viewpoint step locals changed");super.visitMaxs(stack,3);}
        };}},ClassReader.EXPAND_FRAMES);
        if(sites[0]!=1)throw new IOException("Viewpoint step missing");return writer.toByteArray();
    }
}
