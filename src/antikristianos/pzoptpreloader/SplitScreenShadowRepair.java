package antikristianos.pzoptpreloader;

import java.io.IOException;
import net.bytebuddy.jar.asm.*;

/** A shared scene framebuffer does not imply an unchanged player viewport. */
public final class SplitScreenShadowRepair {
    public static final String TARGET="pzopt/CapsuleShadow$Gl";
    private static final String DRAW="(Lpzopt/CapsuleShadow$Frame;)V";
    private static final String PIN="b2c4eb89b266ce790ed72f6aedfced775e3cf6f7bf8b7f7737b5b59c35f316af";
    private static final String NATIVE_PIN="6d061a81be5dced975ba5b15d23ae8ccf2e877ac6dab94eaec04b68f26554bb7";
    private SplitScreenShadowRepair(){ }
    public static byte[] patch(String name,byte[] bytes)throws IOException {
        if(!TARGET.equals(name))return bytes;
        ClassReader reader=new ClassReader(bytes);
        String actual=Compatibility.methods(bytes).get("draw"+DRAW);
        if(!name.equals(reader.getClassName())||!(NATIVE_PIN.equals(actual)||RepairProfiles.matches(PIN,actual)))
            throw new IOException("Unsupported split-screen shadow viewport boundary: "+name);
        int[] sites={0,0,0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                MethodVisitor out=super.visitMethod(a,n,d,s,e);if(!n.equals("draw")||!d.equals(DRAW))return out;
                return new MethodVisitor(Opcodes.ASM9,out){
                    boolean cacheVisited,refreshed;
                    @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                        if(owner.equals("org/lwjgl/opengl/GL11")&&method.equals("glGetIntegerv")&&desc.equals("(I[I)V")){
                            // The reviewed draw has exactly one viewport query of each kind.
                            super.visitInsn(Opcodes.POP2);sites[0]++;return;
                        }
                        if(owner.equals("org/lwjgl/opengl/GL11")&&method.equals("glGetFloatv")&&desc.equals("(I[F)V")){
                            super.visitInsn(Opcodes.POP2);cacheVisited=true;sites[1]++;return;
                        }
                        super.visitMethodInsn(op,owner,method,desc,itf);
                    }
                    @Override public void visitFieldInsn(int op,String owner,String field,String type){
                        if(cacheVisited&&!refreshed&&op==Opcodes.GETFIELD&&owner.equals(TARGET)&&field.equals("lastDepth")){
                            refreshed=true;sites[2]++;
                            // The existing receiver is still on the operand stack. Leave it intact.
                            super.visitIntInsn(Opcodes.SIPUSH,2978);super.visitVarInsn(Opcodes.ALOAD,0);
                            super.visitFieldInsn(Opcodes.GETFIELD,TARGET,"viewport","[I");
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glGetIntegerv","(I[I)V",false);
                            for(int i=0;i<4;i++){
                                super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,TARGET,"viewportF","[F");super.visitInsn(Opcodes.ICONST_0+i);
                                super.visitVarInsn(Opcodes.ALOAD,0);super.visitFieldInsn(Opcodes.GETFIELD,TARGET,"viewport","[I");super.visitInsn(Opcodes.ICONST_0+i);super.visitInsn(Opcodes.IALOAD);
                                super.visitInsn(Opcodes.I2F);super.visitInsn(Opcodes.FASTORE);
                            }
                        }
                        super.visitFieldInsn(op,owner,field,type);
                    }
                };
            }
        },0);
        if(sites[0]!=1||sites[1]!=1||sites[2]!=1)throw new IOException("Unsupported split-screen shadow viewport query inventory");
        return writer.toByteArray();
    }
}
