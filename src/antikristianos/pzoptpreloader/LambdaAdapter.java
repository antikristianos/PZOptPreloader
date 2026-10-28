package antikristianos.pzoptpreloader;

import java.lang.invoke.MethodHandles;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Keeps an already loaded class's schema, with added lambda bodies in a runtime companion. */
public final class LambdaAdapter {
    public record Prepared(byte[] definition, byte[] companion, String companionName) { }
    private record Member(int access, String descriptor) { }

    public static Prepared prepare(String owner, byte[] original, byte[] optimized) {
        Map<String, Member> stockFields = members(original, false), fields = members(optimized, false);
        Map<String, Member> stockMethods = members(original, true), methods = members(optimized, true);
        for (var entry : stockFields.entrySet()) if (!entry.getValue().equals(fields.get(entry.getKey()))) {
            throw new IllegalStateException(owner + " changes/removes an already loaded field: " + entry.getKey());
        }
        Set<String> addedFields = new HashSet<>(fields.keySet()); addedFields.removeAll(stockFields.keySet());
        for (String key : addedFields) {
            Member field = fields.get(key);
            int kind = Type.getType(field.descriptor).getSort();
            if ((field.access & Opcodes.ACC_PRIVATE) == 0
                    || (field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) != 0
                    || (kind != Type.OBJECT && kind != Type.ARRAY)) {
                throw new IllegalStateException(owner + " adds an unsupported field after class loading: " + key);
            }
        }
        for (var entry : stockMethods.entrySet()) if (!entry.getValue().equals(methods.get(entry.getKey()))) {
            throw new IllegalStateException(owner + " changes an already loaded method signature/access: " + entry.getKey());
        }
        Set<String> added = new HashSet<>(methods.keySet()); added.removeAll(stockMethods.keySet());
        for (String key : added) {
            Member method = methods.get(key);
            if (!key.startsWith("lambda$") || (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC))
                    != (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) {
                throw new IllegalStateException(owner + " adds a non-lambda method after class loading: " + key);
            }
        }
        if (added.isEmpty() && addedFields.isEmpty()) return new Prepared(optimized, null, null);
        String companionName = owner + "$PZOptPreloaderLambdas";
        ClassWriter companion = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        companion.visit(new ClassReader(optimized).readShort(6), Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
            companionName, null, "java/lang/Object", null);
        ClassReader reader = new ClassReader(optimized);
        ClassWriter target = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, target) {
            @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                return addedFields.contains(name + desc) ? null : super.visitField(access, name, desc, signature, value);
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                boolean extra = added.contains(name + desc);
                MethodVisitor visitor = extra
                    ? companion.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                        name, companionDescriptor(owner, desc, access), null, exceptions)
                    : super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, visitor) {
                    @Override public void visitFieldInsn(int opcode, String fieldOwner, String fieldName, String fieldDesc) {
                        if (fieldOwner.equals(owner) && addedFields.contains(fieldName + fieldDesc)) {
                            String key = owner + "/" + fieldName + ":" + fieldDesc;
                            super.visitLdcInsn(key);
                            if (opcode == Opcodes.GETFIELD) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/LoadedFields",
                                    "get", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
                                super.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(fieldDesc).getInternalName());
                            } else if (opcode == Opcodes.PUTFIELD) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/LoadedFields",
                                    "put", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V", false);
                            } else throw new IllegalStateException("Unexpected instance memo field opcode: " + key);
                            return;
                        }
                        if (extra && fieldOwner.equals(owner)) {
                            Member field = stockFields.get(fieldName + fieldDesc);
                            if (field == null || (field.access & Opcodes.ACC_PUBLIC) == 0) throw new IllegalStateException("Lambda accesses non-public field " + fieldName);
                        }
                        super.visitFieldInsn(opcode, fieldOwner, fieldName, fieldDesc);
                    }
                    @Override public void visitMethodInsn(int opcode, String methodOwner, String methodName, String methodDesc, boolean itf) {
                        String key = methodName + methodDesc;
                        if (methodOwner.equals(owner) && added.contains(key)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, companionName, methodName,
                                companionDescriptor(owner, methodDesc, methods.get(key).access), false);
                        } else {
                            if (extra && methodOwner.equals(owner)) {
                                Member method = stockMethods.get(key);
                                if (method == null || (method.access & Opcodes.ACC_PUBLIC) == 0) throw new IllegalStateException("Lambda accesses non-public method " + key);
                            }
                            super.visitMethodInsn(opcode, methodOwner, methodName, methodDesc, itf);
                        }
                    }
                    @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... args) {
                        Object[] rewritten = args.clone();
                        for (int i = 0; i < rewritten.length; i++) if (rewritten[i] instanceof Handle handle
                                && handle.getOwner().equals(owner) && added.contains(handle.getName() + handle.getDesc())) {
                            rewritten[i] = new Handle(Opcodes.H_INVOKESTATIC, companionName, handle.getName(),
                                companionDescriptor(owner, handle.getDesc(), methods.get(handle.getName() + handle.getDesc()).access), false);
                        }
                        super.visitInvokeDynamicInsn(name, descriptor, bootstrap, rewritten);
                    }
                };
            }
        }, 0);
        companion.visitEnd();
        return new Prepared(target.toByteArray(), added.isEmpty() ? null : companion.toByteArray(), added.isEmpty() ? null : companionName);
    }

    public static void defineCompanion(Class<?> target, Prepared prepared) throws IllegalAccessException {
        if (prepared.companion != null) MethodHandles.privateLookupIn(target, MethodHandles.lookup()).defineClass(prepared.companion);
    }
    private static String companionDescriptor(String owner, String descriptor, int access) {
        return (access & Opcodes.ACC_STATIC) == 0 ? "(L" + owner + ";" + descriptor.substring(1) : descriptor;
    }
    private static Map<String, Member> members(byte[] bytes, boolean methods) {
        Map<String, Member> result = new TreeMap<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                if (!methods) result.put(name + desc, new Member(access, desc)); return null;
            }

            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (methods) result.put(name + desc, new Member(access, desc)); return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }
}
