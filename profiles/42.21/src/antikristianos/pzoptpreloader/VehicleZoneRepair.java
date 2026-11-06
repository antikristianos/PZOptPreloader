package antikristianos.pzoptpreloader;

import java.security.MessageDigest;
import java.util.HexFormat;
import net.bytebuddy.jar.asm.*;
import zombie.iso.zones.Zone;

/** Own narrow call-site repair; downloaded code and its feature gate remain authoritative. */
public final class VehicleZoneRepair {
    public static final String TARGET = "zombie/iso/IsoMetaGrid";
    // Normalized method code from installed PZOpt c20a058c, reviewed against PR #70.
    public static final String AFFECTED_METHOD = "45e930e96d5be46add70f51d512c85acb1ba5878c007bfdc1f1fbbd444ff71dd";
    // Same 174 input instructions and local Zone slot after the verified B42.22 port; recomputed frames differ.
    public static final String PORTED_METHOD="9aada177e120f77e9c68febaea6aa68780f2a9905ec2fb1f4c8fd5592e937df8";
    private static boolean affected(String hash){return AFFECTED_METHOD.equals(hash)||PORTED_METHOD.equals(hash);}
    public record Key(int x, int y, int width, int height) { }
    public static Object key(Zone zone) { return new Key(zone.getX(), zone.getY(), zone.w, zone.h); }

    /** Return only a verified repaired method, composed into the stock server class. */
    public static byte[] serverDefinition(byte[] downloaded, byte[] stock) {
        if (!affected(fingerprint(downloaded))) return null;
        byte[] repaired = patch(TARGET, downloaded);
        if (affected(fingerprint(repaired))) return null;
        ClassReader reader = new ClassReader(stock);
        ClassWriter writer = new ClassWriter(0);
        int[] replaced = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("checkVehiclesZones") || !descriptor.equals("()V"))
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                replaced[0]++;
                new ClassReader(repaired).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                        return n.equals(name) && d.equals(descriptor) ? writer.visitMethod(a, n, d, s, e) : null;
                    }
                }, 0);
                return null;
            }
        }, 0);
        byte[] result = writer.toByteArray();
        if (replaced[0] != 1 || !fingerprint(repaired).equals(fingerprint(result))) return null;
        return result;
    }

    public static String fingerprint(byte[] bytes) {
        ClassWriter method = new ClassWriter(0);
        method.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "MethodFingerprint", null, "java/lang/Object", null);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return name.equals("checkVehiclesZones") && descriptor.equals("()V")
                    ? method.visitMethod(access, name, descriptor, signature, exceptions) : null;
            }
        }, ClassReader.SKIP_DEBUG);
        method.visitEnd();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(method.toByteArray())); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static byte[] patch(String name, byte[] bytes) {
        if (!TARGET.equals(name)) return bytes;
        String fingerprint = fingerprint(bytes);
        if (!affected(fingerprint)) {
            System.out.println("[PZOptPreloader] PR70: no matching affected method; upstream retained (" + fingerprint + ")");
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] calls = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("checkVehiclesZones") || !descriptor.equals("()V")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/Long") && name.equals("valueOf") && descriptor.equals("(J)Ljava/lang/Long;")) {
                            // The pinned method has the current Zone in local 4. Replace only the set key.
                            super.visitInsn(Opcodes.POP2);
                            super.visitVarInsn(Opcodes.ALOAD, 4);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/VehicleZoneRepair", "key", "(Lzombie/iso/zones/Zone;)Ljava/lang/Object;", false);
                            calls[0]++;
                        } else super.visitMethodInsn(opcode, owner, name, descriptor, itf);
                    }
                };
            }
        }, 0);
        if (calls[0] != 1) throw new IllegalStateException("PR70 fingerprint matched without exactly one key call");
        System.out.println("[PZOptPreloader] PR70: repaired affected vehicle-zone key; full coordinates retained");
        return writer.toByteArray();
    }
}
