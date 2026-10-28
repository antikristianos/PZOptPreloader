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
    private byte[] serverVehicleZones;
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
        byte[] bytes = VehicleZoneRepair.patch(name, Files.readAllBytes(classes.get(name)));
        return server ? bytes : UiMoveReplayRepair.patch(name, RainTileRepair.patch(name, RainRippleRepair.patch(name, bytes)));
    }

    public Path cacheJar(Path configDirectory, boolean server) throws IOException {
        Path directory = configDirectory.resolve("PZOptPreloader").resolve(Long.toString(ProcessHandle.current().pid()))
            .resolve(server ? "server" : "client");
        Files.createDirectories(directory);
        // Upstream ignores resources from a file named pzopt.jar when checking stock classes.
        Path jar = directory.resolve("pzopt.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (var entry : classes.entrySet()) {
                if (server && !entry.getKey().startsWith("pzopt/") && !selected(entry.getKey(), true)) continue;
                output.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                output.write(classBytes(entry.getKey(), server)); output.closeEntry();
            }
            for (var entry : resources.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                Files.copy(entry.getValue(), output); output.closeEntry();
            }
        }
        return jar;
    }

    public static Path discover() throws Exception {
        String explicit = System.getProperty("pzoptpreloader.source");
        if (explicit != null && !explicit.isBlank()) return normalizeSource(Path.of(explicit));
        LinkedHashSet<Path> anchors = new LinkedHashSet<>();
        anchors.add(Path.of(System.getProperty("user.dir")));
        anchors.add(Path.of(Payload.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) anchors.add(Path.of(entry).toAbsolutePath());
        }
        for (Path anchor : anchors) for (Path path = anchor; path != null; path = path.getParent()) {
            for (Path candidate : List.of(
                path.resolve("workshop/content/108600/3805285544"),
                path.resolve("steamapps/workshop/content/108600/3805285544"),
                path.resolve("content/108600/3805285544"),
                path.resolve("3805285544"))) {
                try { return normalizeSource(candidate); } catch (IOException ignored) { }
            }
        }
        throw new IOException("PZ Optimization Workshop download 3805285544 not found; subscribe/download it, or set pzoptpreloader.source");
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
