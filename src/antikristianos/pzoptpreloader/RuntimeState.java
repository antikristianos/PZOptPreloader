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
    private static boolean installationStarted, sourceCommitted, sourcePreparation;
    private static volatile Throwable injectionFailure;
    private static StartupFallback.Attempt startupAttempt;

    public static synchronized void install() throws Exception {
        if (installed) return;
        if(Installation.disabled()){status="disabled by user setting";return;}
        if(installationStarted)throw new IllegalStateException("Injection already attempted; a full game restart is required",injectionFailure);
        installationStarted=true;
        try{installOnce();}
        catch(Exception|LinkageError error){
            if(!server&&(sourceCommitted||StartupFallback.sourceFailure(error)))recordFailure(error);
            if(!sourceCommitted)releaseSource();
            throw error;
        }
    }
    static synchronized void recordFailure(Throwable error){
        if(injectionFailure!=null)return;injectionFailure=error;installed=false;status="FAILED: "+error;
        if(server||StartupFallback.environmentFailure(error))return;
        if(!sourcePreparation&&!StartupFallback.sourceFailure(error))return;
        try{
            StartupFallback.Attempt attempt=startupAttempt;
            if(attempt==null&&error instanceof Payload.SourceUnavailable source&&source.candidate!=null)
                attempt=StartupFallback.capture(SourcePaths.configDirectory(),StartupFallback.revision(),source.candidate);
            if(attempt!=null){StartupFallback.reject(attempt,error);Installation.recoveryNotice();}
        }catch(IOException|RuntimeException recovery){System.err.println("[PZOptPreloader] recovery preparation failed; original failure retained: "+recovery);}
    }
    private static void installOnce() throws Exception {
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
        Payload candidate;
        try{
            candidate=Payload.discovered();
            if(!server)startupAttempt=StartupFallback.capture(SourcePaths.configDirectory(),actual,candidate.root);
            if(sourceUse!=null&&!candidate.root.equals(SourcePaths.installationDirectory().toRealPath()))releaseSource();
            candidate.prepare(actual);
        }catch(Throwable failed){throw failed;}
        if (server) candidate.prepareServerVehicleZones();
        Field field = Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");
        field.setAccessible(true); instrumentation = (Instrumentation) field.get(null);
        if (instrumentation == null) throw new IllegalStateException("ZombieBuddy instrumentation is unavailable");
        PzfbSignedPackages.Prepared signedPzfb=server?null:PzfbSignedPackages.prepare(instrumentation);
        if(!Compatibility.LEGACY.equals(actual))ListStorage.install(instrumentation);
        Map<Class<?>, LambdaAdapter.Prepared> pending = new LinkedHashMap<>();
        sourcePreparation=true;
        try{
        boolean extended=!server&&Compatibility.extendedSource(candidate);
        Map<Class<?>,byte[]> loaded=new LinkedHashMap<>();
        for (Class<?> cls : instrumentation.getAllLoadedClasses()) {
            String name = cls.getName().replace('.', '/');
            if (!candidate.selected(name, server)) continue;
            try (InputStream input = cls.getClassLoader().getResourceAsStream(name + ".class")) {
                if (input == null) throw new IOException("Cannot read the already loaded schema: " + name);
                byte[] original=input.readAllBytes();
                if (extended) loaded.put(cls,original);
                else pending.put(cls, LambdaAdapter.prepare(name, original, candidate.classBytes(name, server)));
            }
        }
        if (extended) {
            Map<String,byte[]> schemas=new TreeMap<>();
            loaded.forEach((cls,bytes)->schemas.put(cls.getName().replace('.','/'),bytes));
            candidate.prepareEarlyEmission(schemas);
            for(Class<?> cls:loaded.keySet()) pending.put(cls,new LambdaAdapter.Prepared(
                candidate.classBytes(cls.getName().replace('.','/'),false),null,null));
        }
        }catch(Exception failed){
            throw new Payload.SourceUnavailable("incompatible","Early class preparation refused: "+failed,candidate.root,
                new StartupFallback.SourceFailure("PZOpt early definitions refused",failed));
        }finally{
            sourcePreparation=false;
        }
        helperJar = new JarFile(candidate.cacheJar(Agent.configDir(), server).toFile());
        payload = candidate;
        sourcePreparation=false;
        if(!server&&candidate.manualAdpPhysics())
            System.out.println("[PZOptPreloader] Verified installed manual ADP owns WorldSimulation; massScaler and complete ADP physics retained. "
                +(candidate.manualAdpOptimizedPhysics()?"Existing optcompat vehicle smoothing/step hooks retained.":"PZOpt vehicle smoothing/step overrides are inactive for this standard ADP process; saved options are unchanged."));
        instrumentation.appendToSystemClassLoaderSearch(helperJar);
        sourceCommitted=true;
        instrumentation.addTransformer(new Transformer(), false);
        if(signedPzfb!=null)PzfbSignedPackages.define(signedPzfb);
        if(!server)ManualAdpCompatibility.defineSignedPhysics(candidate,instrumentation);
        sourcePreparation=true;
        int signedHelpers=SignedPackageHelpers.define(candidate,server,instrumentation,helperJar);
        if(signedHelpers>0)System.out.println("[PZOptPreloader] "+signedHelpers+" installed helpers defined in their native package protection domains");
        // Define every member of the chosen families before other transformers resolve their types.
        // JVM instrumentation suppresses recursive transformation during a transformer callback.
        Set<String> buddyTargets = Set.of("zombie/GameWindow", "zombie/gameStates/GameLoadingState", "org/lwjglx/opengl/Display");
        List<String> eager = candidate.classNames().stream().filter(name -> candidate.selected(name, server))
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
        for (String name : eager) if (!definitions.containsKey(name)) {
            throw new IllegalStateException("Selected class escaped early injection: " + name);
        }
        if(injectionFailure!=null)throw new IllegalStateException("Injection failed before completion",injectionFailure);
        sourcePreparation=false;
        installed = true;
        status = "active " + (server ? "server (" + candidate.serverFamilyCount() + " selected override families; " + eager.size() + " class definitions)"
            : "client (" + (candidate.manualAdpPhysics() ? candidate.targets.stream().filter(name -> candidate.selected(name,false)).count()+" selected" : "all "+payload.targets.size()) + " override families; " + eager.size() + " class definitions including nested)")
            + "; source=" + payload.root + "; commit=" + payload.build.getProperty("commit");
        System.out.println("[PZOptPreloader] " + status);
        if(!server)try{SourceHistory.remember(candidate,actual);}catch(IOException|RuntimeException error){System.err.println("[PZOptPreloader] original-source backup unavailable; existing snapshots retained: "+error);}
        if(!server)try{StartupFallback.active(candidate,actual);}catch(IOException|RuntimeException notice){System.err.println("[PZOptPreloader] recovery notice unavailable: "+notice);}
    }

    private static void releaseSource(){
        if(sourceUse!=null){try{sourceUse.close();}catch(IOException error){System.err.println("[PZOptPreloader] source lock close: "+error);}sourceUse=null;}
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
                if (payload.selected(name, server) && payload.hasClass(name)) {
                    byte[] bytes = cachedClass(name);
                    definitions.put(name, bytes); return bytes;
                }
                if (name.startsWith("pzopt/") && payload.hasClass(name)) {
                    return helperBridges(name, input);
                }
            } catch (Exception error) {
                recordFailure(new StartupFallback.SourceFailure("Loading PZOpt definition "+name+" failed",error));
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
