package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** Route only Viewpoint-owned model work through the verified upstream serial finish. */
public final class ViewpointAnimationRepair {
    public static final String TARGET="zombie/characters/IsoGameCharacter";
    public static final String WORKER_PIN="3692b3eba04859c643a8ed7d4441a05e27a5a89937ecd36159adce0cd04c9755";
    public static final String FINISH_PIN="e7aad7cb2e4e67add2f6785cb469e0a1bca0d744f31fc6a6d8e63be391edfd7e";
    private static final String CAPTURE="pzopt/AnimCapture";
    private static final String PLAYER="zombie/core/skinnedmodel/animation/AnimationPlayer";
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointAnimationCompatibility";
    private ViewpointAnimationRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGET.equals(name))return bytes;
        var methods=Compatibility.methods(bytes);
        if(!WORKER_PIN.equals(methods.get("pzoptRestWorker()V"))||!FINISH_PIN.equals(methods.get("pzoptRestFinish(Lpzopt/AnimCapture;)V")))
            throw new IOException("Unsupported Viewpoint animation worker/finish boundary");
        ClassReader reader=new ClassReader(bytes);
        if(!TARGET.equals(reader.getClassName()))throw new IOException("Viewpoint animation class identity changed");
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] checks={0,0,0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(a,method,descriptor,signature,exceptions);
                if(!method.equals("pzoptRestWorker")||!descriptor.equals("()V"))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    final Label serialFinish=new Label();
                    Label beforeDeltas,currentLabel;
                    boolean ragdollTest,gatePending,modelCalled,finishLabel;
                    @Override public void visitMethodInsn(int opcode,String owner,String called,String desc,boolean itf){
                        super.visitMethodInsn(opcode,owner,called,desc,itf);
                        if(owner.equals("zombie/core/skinnedmodel/animation/AnimationMultiTrack")&&called.equals("containsAnyRagdollTracks")&&desc.equals("()Z"))ragdollTest=true;
                        if(owner.equals(TARGET)&&called.equals("applyDeltas")&&desc.equals("(L"+PLAYER+";)V"))checks[1]++;
                        if(owner.equals("zombie/core/skinnedmodel/ModelManager$ModelSlot")&&called.equals("Update")&&desc.equals("(F)V")){modelCalled=true;checks[2]++;}
                    }
                    @Override public void visitJumpInsn(int opcode,Label label){
                        if(ragdollTest){if(opcode!=Opcodes.IFEQ)throw new IllegalStateException("Viewpoint ragdoll boundary changed");beforeDeltas=label;ragdollTest=false;}
                        super.visitJumpInsn(opcode,label);
                    }
                    @Override public void visitLabel(Label label){
                        // This is the existing common finally path immediately after model work.
                        // The original frame at the same bytecode position covers this new label.
                        if(modelCalled&&!finishLabel){super.visitLabel(serialFinish);finishLabel=true;}
                        super.visitLabel(label);currentLabel=label;
                        if(label==beforeDeltas)gatePending=true;
                    }
                    @Override public void visitFrame(int type,int nLocal,Object[] local,int nStack,Object[] stack){
                        super.visitFrame(type,nLocal,local,nStack,stack);
                        if(!gatePending)return;
                        if(currentLabel!=beforeDeltas)throw new IllegalStateException("Viewpoint worker frame boundary changed");
                        gatePending=false;checks[0]++;
                        Label nativePath=new Label();
                        super.visitVarInsn(Opcodes.ALOAD,0);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"serialModelUpdate","(L"+TARGET+";)Z",false);
                        super.visitJumpInsn(Opcodes.IFEQ,nativePath);
                        super.visitVarInsn(Opcodes.ALOAD,1);
                        super.visitInsn(Opcodes.ICONST_1);
                        super.visitFieldInsn(Opcodes.PUTFIELD,CAPTURE,"serial","Z");
                        // Before applyDeltas: the original serial finish must apply it exactly once.
                        super.visitJumpInsn(Opcodes.GOTO,serialFinish);
                        super.visitLabel(nativePath);
                        super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                    }
                    @Override public void visitEnd(){
                        if(!finishLabel)throw new IllegalStateException("Viewpoint common finish label missing");
                        super.visitEnd();
                    }
                };
            }
        },0);
        if(checks[0]!=1||checks[1]!=1||checks[2]!=1)throw new IOException("Viewpoint animation instruction shape changed");
        return writer.toByteArray();
    }
}
