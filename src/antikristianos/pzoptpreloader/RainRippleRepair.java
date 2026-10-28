package antikristianos.pzoptpreloader;

import net.bytebuddy.jar.asm.*;

/** Retain the native puddle exclusion mask in the downloaded rain slope generator. */
public final class RainRippleRepair {
    public static final String TARGET = "pzopt/RainRipples";
    public static final String BEFORE = "   vec2 g = pzRainSlope(fc) * pzRainP.z;";
    public static final String AFTER = "   vec2 g = pzRainSlope(fc) * pzRainP.z * clamp(puddlesDirNone, 0.0, 1.0);";
    private RainRippleRepair() { }

    public static byte[] patch(String name, byte[] bytes) {
        if (!TARGET.equals(name)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        int[] shape = new int[7];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String n, String d, String s, Object value) {
                if (n.equals("GLSL") && d.equals("Ljava/lang/String;")
                    && (access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) shape[0]++;
                return null;
            }
            @Override public MethodVisitor visitMethod(int access, String n, String d, String s, String[] e) {
                if ((access & Opcodes.ACC_STATIC) == 0) return null;
                if (n.equals("glsl") && d.equals("(Z)Ljava/lang/String;")) shape[1]++;
                if (!n.equals("<clinit>") || !d.equals("()V")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitLdcInsn(Object value) {
                        if (BEFORE.equals(value)) shape[2]++;
                        if (AFTER.equals(value)) shape[3]++;
                        if ("vec3 pzRain(vec2 fc) {".equals(value)) shape[4]++;
                        if ("   pzRainG = vec2(g.x - g.y, g.x + g.y) * 0.7071;".equals(value)) shape[5]++;
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                        if (opcode == Opcodes.PUTSTATIC && owner.equals(TARGET) && field.equals("GLSL")
                            && descriptor.equals("Ljava/lang/String;")) shape[6]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!reader.getClassName().equals(TARGET) || shape[0] != 1 || shape[1] != 1 || shape[2] != 1
            || shape[3] != 0 || shape[4] != 1 || shape[5] != 1 || shape[6] != 1) {
            System.out.println("[PZOptPreloader] rain ripples: no matching unmasked slope generator; upstream retained");
            return bytes;
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String n, String d, String s, String[] e) {
                MethodVisitor target = super.visitMethod(access, n, d, s, e);
                if (!n.equals("<clinit>") || !d.equals("()V")) return target;
                return new MethodVisitor(Opcodes.ASM9, target) {
                    @Override public void visitLdcInsn(Object value) { super.visitLdcInsn(BEFORE.equals(value) ? AFTER : value); }
                };
            }
        }, 0);
        System.out.println("[PZOptPreloader] rain ripples: native puddle exclusion mask restored");
        return writer.toByteArray();
    }
}
