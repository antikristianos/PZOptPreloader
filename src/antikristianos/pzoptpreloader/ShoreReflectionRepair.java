package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** B42.22 client: defer the installed next-frame scatter until all late shoreline surfaces have read this frame. */
public final class ShoreReflectionRepair {
    public static final String TARGET="zombie/iso/fboRenderChunk/FBORenderCell";
    public static final String DESCRIPTOR="(Lzombie/iso/IsoCell$PerPlayerRender;IJ)V";
    private static final String STATE="antikristianos/pzoptpreloader/ShoreReflectionState";
    private static final String NAME="performRenderTiles",ORIGINAL="pzoptPreloaderRenderTilesBeforeReflectionFinish",FINISH="pzoptPreloaderFinishShoreReflections";
    private static final String HASH="b2425759ca540d918331aaadfc2cce36d8522f41d7f4dd63da7a0acdaefda713";
    private ShoreReflectionRepair() { }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        if(!TARGET.equals(reader.getClassName())||!RepairProfiles.matches(HASH,Compatibility.methods(bytes).get(NAME+DESCRIPTOR)))
            throw new IOException("Unsupported late-shore reflection boundary: "+name);
        int[] hits=new int[4];
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                if(n.equals(ORIGINAL)||n.equals(FINISH))throw new IllegalStateException("Late-shore scope already present");
                if(!n.equals(NAME)||!d.equals(DESCRIPTOR))return super.visitMethod(a,n,d,s,e);
                wrapper(super.visitMethod(a,n,d,s,e));
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,ORIGINAL,d,s,e)){
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                        if(op==Opcodes.INVOKESTATIC&&owner.equals("pzopt/Ssr")&&method.equals("afterWater")&&desc.equals("()V")){
                            hits[0]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"defer","()V",false);return;
                        }
                        if(owner.equals("pzopt/CapsuleShadow")&&method.equals("beforeFog")&&desc.equals("(I)V")){
                            hits[3]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,TARGET,FINISH,"()V",false);
                        }
                        if(owner.equals(TARGET)&&method.equals("renderWaterShore")&&desc.equals("(I)V"))hits[1]++;
                        if(owner.equals(TARGET)&&method.equals("renderPuddlesTranslucentFloorsOnly")&&desc.equals("(II)V"))hits[2]++;
                        super.visitMethodInsn(op,owner,method,desc,itf);
                    }
                };
            }
            @Override public void visitEnd(){
                MethodVisitor m=super.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,FINISH,"()V",null,null);
                Label done=new Label();m.visitCode();m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"consume","()Z",false);
                m.visitJumpInsn(Opcodes.IFEQ,done);m.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/Ssr","afterWater","()V",false);
                m.visitLabel(done);m.visitFrame(Opcodes.F_SAME,0,null,0,null);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
                super.visitEnd();
            }
        },0);
        if(hits[0]!=1||hits[1]!=1||hits[2]!=1||hits[3]!=1)throw new IOException("Unsupported early-water/late-shore shape");
        return writer.toByteArray();
    }
    private static void wrapper(MethodVisitor m){
        Label start=new Label(),end=new Label(),handler=new Label();
        m.visitCode();m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"begin","()V",false);
        m.visitTryCatchBlock(start,end,handler,"java/lang/Throwable");m.visitLabel(start);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);m.visitVarInsn(Opcodes.ILOAD,2);m.visitVarInsn(Opcodes.LLOAD,3);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,TARGET,ORIGINAL,DESCRIPTOR,false);m.visitLabel(end);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,TARGET,FINISH,"()V",false);m.visitInsn(Opcodes.RETURN);
        m.visitLabel(handler);m.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"java/lang/Throwable"});m.visitVarInsn(Opcodes.ASTORE,5);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,STATE,"abort","()V",false);m.visitVarInsn(Opcodes.ALOAD,5);m.visitInsn(Opcodes.ATHROW);
        m.visitMaxs(0,0);m.visitEnd();
    }
}
