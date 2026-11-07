package antikristianos.pzoptpreloader;

import java.security.MessageDigest;
import java.util.HexFormat;
import net.bytebuddy.jar.asm.*;

/** Keep render-time self-positioning authoritative when another UI element moves the root. */
public final class UiMoveReplayRepair {
    public static final String TARGET = "pzopt/UiRetained";
    public static final String METHOD = "(Lzombie/ui/UIElementInterface;Lpzopt/UiRetained$Rec;Z)Z";
    // Normalized needsFresh code from the installed d1bbdcf payload, reproduced on 2026-10-24.
    public static final String AFFECTED_METHOD = "dca210abc6854fd68655982c36b49b34d5990f3a318e00a75e262c72fd38c7de";
    private UiMoveReplayRepair() { }

    public static String fingerprint(byte[] bytes) {
        ClassWriter method = new ClassWriter(0);
        method.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "MethodFingerprint", null, "java/lang/Object", null);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return n.equals("needsFresh") && d.equals(METHOD) ? method.visitMethod(a,n,d,s,e) : null;
            }
        }, ClassReader.SKIP_DEBUG);
        method.visitEnd();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(method.toByteArray())); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static byte[] patch(String name, byte[] bytes) {
        if (!TARGET.equals(name)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        String hash = fingerprint(bytes);
        if (!reader.getClassName().equals(TARGET) || !AFFECTED_METHOD.equals(hash)) {
            System.out.println("[PZOptPreloader] UI move replay: no matching affected method; upstream retained ("+hash+")");
            return bytes;
        }
        int[] shape = new int[3];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                boolean target = n.equals("needsFresh") && d.equals(METHOD);
                if (target && (a & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) shape[0]++;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitFieldInsn(int op, String owner, String field, String desc) {
                        if (target && op == Opcodes.GETSTATIC && owner.equals("pzopt/Config") && field.equals("UI_MOVE_REPLAY") && desc.equals("Z")) shape[1]++;
                    }
                    @Override public void visitMethodInsn(int op, String owner, String method, String desc, boolean itf) {
                        if (op == Opcodes.INVOKEVIRTUAL && owner.equals("pzopt/UiDeps$Log") && method.equals("selfPositioned") && desc.equals("()Z")) shape[2]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (shape[0] != 1 || shape[1] != 1 || shape[2] != 1) {
            System.out.println("[PZOptPreloader] UI move replay: unsupported dependency/decision shape; upstream retained");
            return bytes;
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                MethodVisitor base = super.visitMethod(a,n,d,s,e);
                if (!n.equals("needsFresh") || !d.equals(METHOD)) return base;
                return new MethodVisitor(Opcodes.ASM9, base) {
                    @Override public void visitFieldInsn(int op, String owner, String field, String desc) {
                        super.visitFieldInsn(op,owner,field,desc);
                        if (op != Opcodes.GETSTATIC || !owner.equals("pzopt/Config") || !field.equals("UI_MOVE_REPLAY") || !desc.equals("Z")) return;
                        // Move replay needs tracked dependencies and no render-time self-positioning.
                        super.visitFieldInsn(Opcodes.GETSTATIC,"pzopt/Config","UI_VERIFY","Z");
                        super.visitInsn(Opcodes.IAND);
                        super.visitVarInsn(Opcodes.ALOAD,1);
                        super.visitFieldInsn(Opcodes.GETFIELD,"pzopt/UiRetained$Rec","deps","Lpzopt/UiDeps$Log;");
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"pzopt/UiDeps$Log","selfPositioned","()Z",false);
                        super.visitInsn(Opcodes.ICONST_1);
                        super.visitInsn(Opcodes.IXOR);
                        super.visitInsn(Opcodes.IAND);
                    }
                };
            }
        },0);
        System.out.println("[PZOptPreloader] UI move replay: self-positioning guarded; untracked moves render fresh");
        return writer.toByteArray();
    }
}
