package antikristianos.pzoptpreloader;

import net.bytebuddy.jar.asm.*;

/** Retain the native puddle exclusion mask in the downloaded rain slope generator. */
public final class RainRippleRepair {
    public static final String TARGET = "pzopt/RainRipples";
    public static final String BEFORE_HASH = "5320978367e05d69792c820042846edf964cbce0e1c2dbf2c8a08b3f7fd0992e";
    public static final String AFTER_HASH = "06fbf294048ba600c2309fdf110e37aa64ed628afba0445bba6e3b97a02a389e";
    private RainRippleRepair() { }
    public static String literalHash(Object value) {
        return value instanceof String text ? Compatibility.hash(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : "";
    }
    /** Inserts only our new mask expression into the separately installed original line. */
    public static String masked(String original) {
        if(!BEFORE_HASH.equals(literalHash(original)))throw new IllegalArgumentException("Unreviewed rain slope input");
        int end=original.lastIndexOf(';');
        if(end<0)throw new IllegalArgumentException("Missing rain slope statement boundary");
        return original.substring(0,end)+" * clamp(puddlesDirNone, 0.0, 1.0)"+original.substring(end);
    }

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
                        if (BEFORE_HASH.equals(literalHash(value))) shape[2]++;
                        if (AFTER_HASH.equals(literalHash(value))) shape[3]++;
                        if ("dfa35e62f293c9324b5a0d5b3e55edb277ae71af55ad430773982fecf6936980".equals(literalHash(value))) shape[4]++;
                        if ("6e90506d48a00cfb39dfdca1f4c3a48e08f875a8e02ae04850d908aa7b78b3f0".equals(literalHash(value))) shape[5]++;
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
                    @Override public void visitLdcInsn(Object value) { super.visitLdcInsn(BEFORE_HASH.equals(literalHash(value)) ? masked((String)value) : value); }
                };
            }
        }, 0);
        System.out.println("[PZOptPreloader] rain ripples: native puddle exclusion mask restored");
        return writer.toByteArray();
    }
}
