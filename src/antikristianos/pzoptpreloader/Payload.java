package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Reads the subscribed original; no foreign implementation is shipped in this mod. */
public final class Payload {
    public final Path root;
    public final Properties build;
    public final Set<String> targets;
    public final Map<String, Path> classes = new TreeMap<>();
    public final Map<String, Path> media = new TreeMap<>();
    public final Map<String, Path> resources = new TreeMap<>();
    final Map<String,String> originalClassHashes=new TreeMap<>();
    private byte[] serverVehicleZones;
    private Map<String,byte[]> prepared;
    private Properties effectiveBuild;
    private boolean shoreLighting;
    private static final ThreadLocal<Payload> VERIFIED = new ThreadLocal<>();
    public static Payload discovered()throws Exception {
        Path root=discover();Payload cached=VERIFIED.get();VERIFIED.remove();
        return cached!=null&&cached.root.equals(root)?cached:new Payload(root);
    }
    public byte[] baseClassBytes(String name)throws IOException {
        byte[] bytes=prepared==null?Files.readAllBytes(classes.get(name)):prepared.get(name);
        if(bytes==null)throw new IOException("Missing prepared definition: "+name);return bytes.clone();
    }
    public synchronized void prepare(String actual)throws IOException {
        if(prepared!=null)return;
        Map<String,byte[]> candidate=Compatibility.prepare(this,actual);
        Properties metadata=actual.equals(build.getProperty("revision"))?null:Compatibility.effectiveBuild(this,actual);
        prepared=candidate;effectiveBuild=metadata;shoreLighting="e013dbb529".equals(actual);
    }
    public String originalClassHash(String name){return originalClassHashes.get(name);}
    public Set<String> classNames(){return Collections.unmodifiableSet(prepared==null?classes.keySet():prepared.keySet());}
    public boolean hasClass(String name){return prepared==null?classes.containsKey(name):prepared.containsKey(name);}
    public static final Set<String> SERVER_TARGETS = Set.of(
        "zombie/scripting/ScriptParser", "zombie/iso/FishSchoolManager",
        "zombie/scripting/objects/Item", "zombie/MapCollisionData", "zombie/iso/IsoMetaCell",
        "zombie/iso/worldgen/WorldGenUtils", "se/krka/kahlua/luaj/compiler/LuaCompiler",
        "se/krka/kahlua/j2se/KahluaTableImpl");

    public Payload(Path directory) throws IOException {
        root = directory.toRealPath();
        build = new Properties();
        try (InputStream input = Files.newInputStream(root.resolve("pzopt/build-info.properties"))) { build.load(input); }
        String list = build.getProperty("overrides", "");
        if (list.isBlank()) throw new IOException("PZ Optimization build information has no override list");
        targets = Set.copyOf(Arrays.asList(list.split(",")));
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path actual = file.toRealPath();
                if (!actual.startsWith(root)) throw new IOException("Payload file leaves its source directory: " + file);
                String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                if (relative.endsWith(".class")) classes.put(relative.substring(0, relative.length() - 6), file);
                else if (relative.startsWith("media/")) media.put(relative.toLowerCase(Locale.ROOT), file);
                else if (relative.startsWith("pzopt/")) resources.put(relative, file);
            }
        }
        for (String target : targets) if (!classes.containsKey(target)) throw new IOException("Missing override: " + target);
        Path manifest = root.resolve("pzopt-files.txt");
        if (Files.isRegularFile(manifest)) {
            for (String line : Files.readAllLines(manifest)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                Path required = root.resolve(line.replace('\\', '/')).normalize();
                if (!required.startsWith(root) || !Files.isRegularFile(required)) throw new IOException("Missing or unsafe manifest file: " + line);
            }
        }
    }

    public boolean selected(String name, boolean server) {
        if (server && name.equals(VehicleZoneRepair.TARGET)) return serverVehicleZones != null;
        Set<String> selected = server ? SERVER_TARGETS : targets;
        return selected.stream().anyMatch(target -> name.equals(target) || name.startsWith(target + "$"));
    }

    public void prepareServerVehicleZones() {
        serverVehicleZones = null;
        try (InputStream stock = Payload.class.getClassLoader().getResourceAsStream(VehicleZoneRepair.TARGET + ".class")) {
            Path downloaded = classes.get(VehicleZoneRepair.TARGET);
            if (stock != null && downloaded != null)
                serverVehicleZones = VehicleZoneRepair.serverDefinition(Files.readAllBytes(downloaded), stock.readAllBytes());
        } catch (Exception error) {
            System.err.println("[PZOptPreloader] server vehicle-zone repair unavailable: " + error);
        }
        System.out.println("[PZOptPreloader] server vehicle zones: " + (serverVehicleZones == null
            ? "Vanilla retained; no verified repaired method" : "verified repair enabled; remaining IsoMetaGrid methods stay Vanilla"));
    }

    public int serverFamilyCount() { return SERVER_TARGETS.size() + (serverVehicleZones == null ? 0 : 1); }

    public byte[] classBytes(String name, boolean server) throws IOException {
        if (server && name.equals(VehicleZoneRepair.TARGET)) {
            if (serverVehicleZones == null) throw new IOException("Unverified server vehicle-zone override refused");
            return serverVehicleZones.clone();
        }
        byte[] source=baseClassBytes(name);
        if(source==null)throw new IOException("Missing prepared definition: "+name);
        byte[] bytes = VehicleZoneRepair.patch(name,source);
        if(server)return bytes;
        if(shoreLighting)bytes=ShoreFloorRepair.patch(name,bytes);
        return UiMoveReplayRepair.patch(name, RainTileRepair.patch(name, RainRippleRepair.patch(name, bytes)));
    }

    public Path cacheJar(Path configDirectory, boolean server) throws IOException {
        Path directory = configDirectory.resolve("PZOptPreloader").resolve(Long.toString(ProcessHandle.current().pid()))
            .resolve(server ? "server" : "client");
        Files.createDirectories(directory);
        // Upstream ignores resources from a file named pzopt.jar when checking stock classes.
        Path jar = directory.resolve("pzopt.jar");
        VisualExclusions.prepareMedia(this,directory);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : classNames()) {
                if (server && !name.startsWith("pzopt/") && !selected(name, true)) continue;
                output.putNextEntry(new JarEntry(name + ".class"));
                output.write(classBytes(name, server)); output.closeEntry();
            }
            for (var entry : resources.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                if(entry.getKey().equals("pzopt/build-info.properties")&&effectiveBuild!=null)
                    effectiveBuild.store(output,"Derived process cache; separately installed source is unchanged");
                else Files.copy(entry.getValue(), output);
                output.closeEntry();
            }
        }
        return jar;
    }

    public static Path discover() throws Exception {
        String revision = String.valueOf(Class.forName("zombie.GitVersion").getField("REVISION").get(null));
        String explicit = System.getProperty("pzoptpreloader.source");
        if(explicit!=null&&!explicit.isBlank()){
            try{return verifiedSource(Path.of(explicit),revision);}
            catch(SourceUnavailable failure){
                Path retained=SourceHistory.selected(SourcePaths.configDirectory(),revision,List.of(failure));
                if(retained!=null)return retained;throw failure;
            }
        }
        LinkedHashSet<Path> anchors = new LinkedHashSet<>();
        anchors.add(Path.of(System.getProperty("user.dir")));
        anchors.add(Path.of(Payload.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) anchors.add(Path.of(entry).toAbsolutePath());
        }
        return discover(SourcePaths.workshopCandidates(anchors), SourcePaths.installationDirectory(), revision);
    }

    public static Path discover(Collection<Path> workshopCandidates, Path configSource, String revision) throws IOException {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>(workshopCandidates);
        candidates.add(configSource);
        SourceUnavailable failure = new SourceUnavailable("missing", "No PZ Optimization payload found; install the matching GitHub release in " + configSource);
        List<SourceUnavailable> rejected=new ArrayList<>();
        for (Path candidate : candidates) {
            try { return verifiedSource(candidate, revision); }
            catch (SourceUnavailable error) {
                rejected.add(error);
                if (!error.reason.equals("missing")) failure = error;
            }
        }
        if(failure.candidate==null)failure=new SourceUnavailable(failure.reason,failure.getMessage(),configSource);
        Path retained=SourceHistory.selected(configSource.toAbsolutePath().normalize().getParent(),revision,rejected);
        if(retained!=null)return retained;
        throw failure;
    }

    public static Path verifiedSource(Path candidate, String revision) throws SourceUnavailable {
        Path root;
        try { root = normalizeSource(candidate); }
        catch (IOException error) { throw new SourceUnavailable("missing", error.getMessage(),candidate); }
        try {
            Payload payload = new Payload(root);
            if (!Compatibility.supports(payload,revision))
                throw new SourceUnavailable("incompatible", "PZ Optimization targets revision " + payload.build.getProperty("revision") + "; installed game is " + revision + "; source=" + root);
            if (!payload.classes.containsKey("pzopt/Overrides")) throw new IOException("Missing pzopt/Overrides.class");
            payload.prepare(revision); VERIFIED.set(payload);
            return root;
        } catch (SourceUnavailable error) { throw new SourceUnavailable(error.reason,error.getMessage(),root); }
        catch (IOException | RuntimeException error) { throw new SourceUnavailable("invalid", error.toString(),root); }
    }

    public static final class SourceUnavailable extends IOException {
        public final String reason;
        public final Path candidate;
        public SourceUnavailable(String reason,String message){this(reason,message,null);}
        public SourceUnavailable(String reason,String message,Path candidate){super(message);this.reason=reason;this.candidate=candidate;}
    }

    private static Path normalizeSource(Path candidate) throws IOException {
        for (Path path : List.of(candidate, candidate.resolve("pzopt-classes"),
                candidate.resolve("mods/PZ_Optimization/42/pzopt-classes"),
                candidate.resolve("mods/PZ_Optimization/42.21/pzopt-classes"))) {
            if (Files.isRegularFile(path.resolve("pzopt/build-info.properties"))) return path.toRealPath();
        }
        throw new IOException("No PZ Optimization payload at " + candidate);
    }
}
