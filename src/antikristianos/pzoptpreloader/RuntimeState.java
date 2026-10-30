package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import me.zed_0xff.zombie_buddy.Agent;
import net.bytebuddy.jar.asm.*;

public final class RuntimeState {
    public static volatile Payload payload;
    public static volatile boolean server;
    public static volatile String status = "not preloaded; first activation requires a full restart";
    public static final Map<String, byte[]> definitions = new ConcurrentHashMap<>();
    private static Instrumentation instrumentation;
    private static JarFile helperJar;
    private static Installer.SourceUse sourceUse;
    private static boolean installed;

    public static synchronized void install() throws Exception {
        if (installed) return;
        if (Installation.disabled()) {
            status = "disabled by user setting"; return;
        }
        server = detectServer(System.getProperty("sun.java.command", ""));
        if (!server) AotBridge.validate();
        String actual = String.valueOf(Class.forName("zombie.GitVersion").getField("REVISION").get(null));
        if (!server) {
            try { Installer.activatePending(SourcePaths.configDirectory(), actual); }
            catch (IOException error) { System.err.println("[PZOptPreloader] pending update retained: " + error); }
        }
        if (Files.isDirectory(SourcePaths.installationDirectory())) sourceUse = Installer.useSource(SourcePaths.configDirectory());
        Payload candidate = new Payload(Payload.discover());
        if (sourceUse != null && !candidate.root.equals(SourcePaths.installationDirectory().toRealPath())) {
            sourceUse.close(); sourceUse = null;
        }
        if (!actual.equals(candidate.build.getProperty("revision"))) {
            throw new IllegalStateException("PZ Optimization targets revision " + candidate.build.getProperty("revision") + ", installed game is " + actual);
        }
        if (server) candidate.prepareServerVehicleZones();
        Field field = Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");
        field.setAccessible(true); instrumentation = (Instrumentation) field.get(null);
        if (instrumentation == null) throw new IllegalStateException("ZombieBuddy instrumentation is unavailable");
        Map<Class<?>, LambdaAdapter.Prepared> pending = new LinkedHashMap<>();
        for (Class<?> cls : instrumentation.getAllLoadedClasses()) {
            String name = cls.getName().replace('.', '/');
            if (!candidate.selected(name, server)) continue;
            try (InputStream input = cls.getClassLoader().getResourceAsStream(name + ".class")) {
                if (input == null) throw new IOException("Cannot read the already loaded schema: " + name);
                pending.put(cls, LambdaAdapter.prepare(name, input.readAllBytes(), candidate.classBytes(name, server)));
            }
        }
        helperJar = new JarFile(candidate.cacheJar(Agent.configDir(), server).toFile());
        payload = candidate;
        instrumentation.appendToSystemClassLoaderSearch(helperJar);
        instrumentation.addTransformer(new Transformer(), false);
        // Define every member of the chosen families before other transformers resolve their types.
        // JVM instrumentation suppresses recursive transformation during a transformer callback.
        Set<String> buddyTargets = Set.of("zombie/GameWindow", "zombie/gameStates/GameLoadingState", "org/lwjglx/opengl/Display");
        List<String> eager = candidate.classes.keySet().stream().filter(name -> candidate.selected(name, server))
            .sorted(Comparator.comparing((String name) -> buddyTargets.stream().anyMatch(target -> name.equals(target) || name.startsWith(target + "$")))
                .thenComparing(name -> !name.contains("$"))
                .thenComparing(name -> name)).toList();
        for (String name : eager) if (pending.keySet().stream().noneMatch(cls -> cls.getName().replace('.', '/').equals(name))) {
            Class.forName(name.replace('/', '.'), false, RuntimeState.class.getClassLoader());
        }
        for (var entry : pending.entrySet()) {
            LambdaAdapter.defineCompanion(entry.getKey(), entry.getValue());
            byte[] bytes = entry.getValue().definition();
            instrumentation.redefineClasses(new ClassDefinition(entry.getKey(), bytes));
            definitions.put(entry.getKey().getName().replace('.', '/'), bytes);
        }
        installed = true;
        for (String name : eager) if (!definitions.containsKey(name)) {
            throw new IllegalStateException("Selected class escaped early injection: " + name);
        }
        status = "active " + (server ? "server (" + candidate.serverFamilyCount() + " selected override families; " + eager.size() + " class definitions)"
            : "client (all " + payload.targets.size() + " override families; " + eager.size() + " class definitions including nested)")
            + "; source=" + payload.root + "; commit=" + payload.build.getProperty("commit");
        System.out.println("[PZOptPreloader] " + status);
    }

    public static boolean detectServer(String command) {
        String override = System.getProperty("pzoptpreloader.role", "auto");
        if (override.equals("server")) return true;
        if (override.equals("client")) return false;
        if (!override.equals("auto")) throw new IllegalArgumentException("pzoptpreloader.role must be auto, client, or server");
        if (command.matches("(?s).*\\bzombie\\.network\\.GameServer\\b.*")) return true;
        String executable = ProcessHandle.current().info().command().orElse("").toLowerCase(Locale.ROOT);
        if (executable.contains("projectzomboidserver")) return true;
        for (String argument : ProcessHandle.current().info().arguments().orElse(new String[0])) {
            if (argument.toLowerCase(Locale.ROOT).endsWith(".json")) {
                Boolean fromJson = launcherServer(Path.of(argument));
                if (fromJson != null) return fromJson;
            }
        }
        return false;
    }

    public static Boolean launcherServer(Path json) {
        try {
            if (!Files.isRegularFile(json)) return null;
            var matcher = java.util.regex.Pattern.compile("\\\"mainClass\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(Files.readString(json));
            if (matcher.find()) return matcher.group(1).equals("zombie.network.GameServer");
        } catch (IOException ignored) { }
        return null;
    }

    /** Actual preloader input for mods which analyze the running target's methods. */
    public static byte[] getEffectiveClassBytes(String name) {
        byte[] bytes = definitions.get(name.replace('.', '/'));
        return bytes == null ? null : bytes.clone();
    }

    private static byte[] cachedClass(String name) throws IOException {
        try (InputStream input = helperJar.getInputStream(helperJar.getJarEntry(name + ".class"))) { return input.readAllBytes(); }
    }

    public static final class Transformer implements ClassFileTransformer {
        @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefined, ProtectionDomain domain, byte[] input) {
            if (redefined != null || name == null || payload == null) return null;
            try {
                if (payload.selected(name, server) && payload.classes.containsKey(name)) {
                    byte[] bytes = cachedClass(name);
                    definitions.put(name, bytes); return bytes;
                }
                if (name.startsWith("pzopt/") && payload.classes.containsKey(name)) {
                    return helperBridges(name, input);
                }
            } catch (Exception error) {
                status = "FAILED loading " + name + ": " + error;
                System.err.println("[PZOptPreloader] " + status);
                throw new IllegalStateException(status, error);
            }
            return null;
        }
    }
    private static byte[] helperBridges(String className, byte[] input) {
        ClassReader reader = new ClassReader(input); ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!server && className.equals("pzopt/AotCache") && name.equals("start") && descriptor.equals("()V")) {
                    method.visitCode();
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/AotBridge", "start", descriptor, false);
                    method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd();
                    return null;
                }
                if (!server && className.equals("pzopt/AudioLimiter") && name.equals("library") && descriptor.equals("()Ljava/nio/file/Path;")) {
                    method.visitCode();
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/NativeLibraries", "fmodCore", descriptor, false);
                    method.visitInsn(Opcodes.ARETURN); method.visitMaxs(0, 0); method.visitEnd();
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitCode() {
                        super.visitCode();
                        if (server && className.equals("pzopt/LuaPrecompiler") && name.equals("collect")
                                && descriptor.equals("(Ljava/lang/String;Ljava/util/List;)V")) {
                            Label include = new Label();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/ServerLua", "includeDirectory", "(Ljava/lang/String;)Z", false);
                            super.visitJumpInsn(Opcodes.IFNE, include);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(include);
                            super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                        }
                        if (!server && className.equals("pzopt/Config") && name.equals("register") && descriptor.equals("(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                            for (int value : new int[] {1, 2}) {
                                super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, value);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/AotBridge", "option", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", false);
                                super.visitVarInsn(Opcodes.ASTORE, value);
                            }
                        }
                    }
                    @Override public void visitInsn(int opcode) {
                        if (className.equals("pzopt/UserOptions") && name.equals("read") && descriptor.equals("(Ljava/io/File;)Ljava/util/Properties;") && opcode == Opcodes.ARETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/StartPreset", "apply", "(Ljava/util/Properties;Ljava/io/File;)Ljava/util/Properties;", false);
                        }
                        if (!server && className.equals("pzopt/Config") && name.equals("pinnedBy") && descriptor.equals("(Ljava/lang/String;)Ljava/lang/String;") && opcode == Opcodes.ARETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/AotBridge", "pinned", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        }
                        super.visitInsn(opcode);
                    }
                    @Override public void visitTypeInsn(int opcode, String type) {
                        super.visitTypeInsn(opcode, !server && opcode == Opcodes.NEW && type.equals("java/io/File") ? "antikristianos/pzoptpreloader/PayloadFile" : type);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                        if (!server && opcode == Opcodes.INVOKEVIRTUAL && owner.equals("zombie/ZomboidFileSystem") && name.equals("getMediaFile") && descriptor.equals("(Ljava/lang/String;)Ljava/io/File;")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "antikristianos/pzoptpreloader/Resources", "mediaFile", "(Lzombie/ZomboidFileSystem;Ljava/lang/String;)Ljava/io/File;", false);
                            return;
                        }
                        super.visitMethodInsn(opcode, !server && opcode == Opcodes.INVOKESPECIAL && owner.equals("java/io/File") && name.equals("<init>")
                            ? "antikristianos/pzoptpreloader/PayloadFile" : owner, name, descriptor, itf);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
