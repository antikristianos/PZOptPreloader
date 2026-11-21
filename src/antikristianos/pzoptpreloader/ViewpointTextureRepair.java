package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** Refresh only the frame-owned pacing flags when Viewpoint reuses a render snapshot. */
public final class ViewpointTextureRepair {
    public static final String NATIVE="zombie/core/skinnedmodel/model/ModelSlotRenderData";
    public static final String SNAPSHOT="viewpoint/render/CharacterTextures$Snapshot";
    public static final String SNAPSHOT_RESOURCE="eb75394f407a41c0248307154f5c88fa731cb1753371ae5d496bf85604b859fe";
    public static final String READY="5302c298d3ac538c4183f77aebd655efe5ebef3fe6f9f880e862c6990f8a37b5";
    private static final String HELPER="antikristianos/pzoptpreloader/ViewpointTextureCompatibility";
    private ViewpointTextureRepair(){ }
    public static void verifySnapshot(byte[] bytes)throws IOException {
        if(!SNAPSHOT.equals(new ClassReader(bytes).getClassName())||!READY.equals(Compatibility.methods(bytes).get("ready()Z")))throw new IOException("Unsupported Viewpoint texture snapshot");
    }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!NATIVE.equals(name))return bytes;
        var methods=Compatibility.methods(bytes);
        if(!NATIVE.equals(new ClassReader(bytes).getClassName())||!"df5c6ba564c5d4154fbecf742a83488a7554829898f6968b1dde1d0edcda1c34".equals(methods.get("initModel(Lzombie/core/skinnedmodel/ModelManager$ModelSlot;)V"))||!"ba9734f52e403a32f69d4e978ad29582b7961d1efbe5ac04cda0e83497fd55e5".equals(methods.get("checkReady()Z")))throw new IOException("Unsupported native Viewpoint texture readiness boundary");
        ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] sites={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,method,descriptor,signature,exceptions);
                if(!method.equals("checkReady")||!descriptor.equals("()Z"))return out;
                return new MethodVisitor(Opcodes.ASM9,out){@Override public void visitCode(){
                    super.visitCode();sites[0]++;Label original=new Label();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"scoped","()Z",false);super.visitJumpInsn(Opcodes.IFEQ,original);
                    super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,NATIVE,"textureCreator","Lzombie/core/skinnedmodel/model/ModelInstanceTextureCreator;");super.visitJumpInsn(Opcodes.IFNULL,original);
                    for(String[] flag:new String[][]{{"pzoptSkipTexture","deferred"},{"pzoptSightEligible","eligible"}}){
                        super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,NATIVE,"character","Lzombie/characters/IsoGameCharacter;");
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,"pzopt/FirstSight",flag[1],"(Lzombie/characters/IsoGameCharacter;)Z",false);super.visitFieldInsn(Opcodes.PUTFIELD,NATIVE,flag[0],"Z");
                    }
                    super.visitLabel(original);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                }};
            }
        },0);
        if(sites[0]!=1)throw new IOException("Native Viewpoint texture boundary missing");return writer.toByteArray();
    }
    public static byte[] snapshot(byte[] bytes)throws IOException {
        verifySnapshot(bytes);ClassReader reader=new ClassReader(bytes);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){@Override protected ClassLoader getClassLoader(){return ViewpointTextureRepair.class.getClassLoader();}};int[] sites={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,method,descriptor,signature,exceptions);
                if(!method.equals("ready")||!descriptor.equals("()Z"))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    final Label start=new Label(),end=new Label(),handler=new Label();
                    @Override public void visitCode(){super.visitCode();sites[0]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"enter","()I",false);super.visitVarInsn(Opcodes.ISTORE,3);super.visitLabel(start);}
                    @Override public void visitInsn(int opcode){if(opcode==Opcodes.IRETURN){super.visitVarInsn(Opcodes.ILOAD,3);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"exit","(I)V",false);}super.visitInsn(opcode);}
                    @Override public void visitMaxs(int stack,int locals){if(locals!=3)throw new IllegalStateException("Viewpoint snapshot local layout changed");super.visitLabel(end);super.visitTryCatchBlock(start,end,handler,"java/lang/Throwable");super.visitLabel(handler);super.visitVarInsn(Opcodes.ASTORE,4);super.visitVarInsn(Opcodes.ILOAD,3);super.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"exit","(I)V",false);super.visitVarInsn(Opcodes.ALOAD,4);super.visitInsn(Opcodes.ATHROW);super.visitMaxs(stack,locals);}
                };
            }
        },ClassReader.EXPAND_FRAMES);
        if(sites[0]!=1)throw new IOException("Viewpoint snapshot entry missing");return writer.toByteArray();
    }
}