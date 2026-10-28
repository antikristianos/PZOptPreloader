package antikristianos.pzoptpreloader;

import net.bytebuddy.jar.asm.*;

/** Match the ordinary weather drawer's texture unit before binding a tiled particle texture. */
public final class RainTileRepair {
    public static final String TARGET = "pzopt/RainTiles$Gl";
    public static final String DRAW = "(Ljava/util/ArrayList;Ljava/nio/ByteBuffer;Ljava/util/ArrayList;[Lgnu/trove/list/array/TIntArrayList;)V";
    private RainTileRepair() { }

    public static byte[] patch(String name, byte[] bytes) {
        if (!TARGET.equals(name)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        int[] shape = new int[8];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String n, String d, String s, String[] e) {
                if (!n.equals("draw") || !d.equals(DRAW) || (access & Opcodes.ACC_STATIC) != 0) return null;
                shape[0]++;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String owner, String method, String desc, boolean itf) {
                        if (owner.equals("zombie/core/opengl/VBORenderer") && method.equals("pzoptShaderPositionColorUv")
                            && desc.equals("()Lzombie/core/opengl/VBOLinesShader;")) shape[1]++;
                        if (owner.equals("zombie/core/opengl/ShaderProgram") && method.equals("Start") && desc.equals("()V")) shape[2]++;
                        if (owner.equals("zombie/core/textures/TextureID") && method.equals("bind") && desc.equals("()Z")) shape[3]++;
                        if (owner.equals("pzopt/CoreGl") && method.equals("drawArraysQuads") && desc.equals("(II)V")) shape[4]++;
                        if (method.equals("glActiveTexture") || method.equals("glActiveTextureARB") || method.equals("glBindTextureUnit")) shape[5]++;
                        if (owner.equals("zombie/core/skinnedmodel/model/VertexBufferObject") && method.equals("setModelViewProjection")
                            && desc.equals("(Lzombie/core/opengl/ShaderProgram;)V")) shape[6]++;
                        if (owner.equals("org/lwjgl/opengl/GL15") && method.equals("glBufferData") && desc.equals("(ILjava/nio/ByteBuffer;I)V")) shape[7]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!reader.getClassName().equals(TARGET) || shape[0] != 1 || shape[1] != 1 || shape[2] != 1 || shape[3] != 1
            || shape[4] != 1 || shape[5] != 0 || shape[6] != 1 || shape[7] != 1) {
            System.out.println("[PZOptPreloader] rain tiles: no matching implicit texture-unit path; upstream retained");
            return bytes;
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                MethodVisitor target = super.visitMethod(a,n,d,s,e);
                if (!n.equals("draw") || !d.equals(DRAW)) return target;
                return new MethodVisitor(Opcodes.ASM9,target) {
                    @Override public void visitMethodInsn(int op, String owner, String method, String desc, boolean itf) {
                        super.visitMethodInsn(op,owner,method,desc,itf);
                        if (owner.equals("zombie/core/opengl/ShaderProgram") && method.equals("Start") && desc.equals("()V")) {
                            super.visitLdcInsn(33984); // GL_TEXTURE0, matching VBORenderer.flush().
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL13","glActiveTexture","(I)V",false);
                        }
                    }
                };
            }
        },0);
        System.out.println("[PZOptPreloader] rain tiles: explicit texture unit 0 restored");
        return writer.toByteArray();
    }
}
