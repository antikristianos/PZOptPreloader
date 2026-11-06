package antikristianos.pzoptpreloader;

import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Resolve the game cache before loading any game filesystem classes. */
public final class SourcePaths {
    private SourcePaths() { }

    public static Path configDirectory() {
        List<String> arguments = new ArrayList<>(Arrays.asList(ProcessHandle.current().info().arguments().orElse(new String[0])));
        var words = Pattern.compile("(?:[^\\s\"]|\"[^\"]*\")+").matcher(System.getProperty("sun.java.command", ""));
        while (words.find()) arguments.add(words.group().replace("\"", ""));
        return configDirectory(arguments, System.getProperty("user.home"),
            System.getProperty("os.name", ""), System.getProperty("deployment.user.cachedir"));
    }

    public static Path configDirectory(List<String> arguments, String userHome, String os, String deploymentCache) {
        for (String argument : arguments) {
            if (argument.startsWith("-cachedir=") && argument.length() > 10)
                return Path.of(argument.substring(10).replace("\"", "")).toAbsolutePath().normalize();
        }
        String base = !os.startsWith("Win") && deploymentCache != null ? deploymentCache : userHome;
        return Path.of(base, "Zomboid").toAbsolutePath().normalize();
    }

    /** Steam is independent of the running game directory (copied/older game installs included). */
    public static List<Path> workshopCandidates(Collection<Path> anchors) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        if (System.getProperty("os.name", "").startsWith("Win")) {
            Process query = null;
            try {
                query = new ProcessBuilder("reg.exe", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
                    .redirectErrorStream(true).start();
                if (query.waitFor(2, TimeUnit.SECONDS) && query.exitValue() == 0) {
                    String value = new String(query.getInputStream().readAllBytes(),
                        Charset.forName(System.getProperty("native.encoding", "UTF-8")));
                    var match = Pattern.compile("(?m)^\\s*SteamPath\\s+REG_SZ\\s+(.+?)\\s*$").matcher(value);
                    if (match.find()) roots.add(Path.of(match.group(1)));
                }
            } catch (IOException | RuntimeException ignored) {
                // Steam may be absent; normal anchors/config-source fallback must remain usable.
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally { if (query != null && query.isAlive()) query.destroyForcibly(); }
            for (String key : List.of("ProgramFiles(x86)", "ProgramFiles")) {
                String value = System.getenv(key);
                if (value != null && !value.isBlank()) roots.add(Path.of(value).resolve("Steam"));
            }
        } else {
            Path home = Path.of(System.getProperty("user.home"));
            roots.add(home.resolve(".steam/steam"));
            roots.add(home.resolve(".local/share/Steam"));
            roots.add(home.resolve("Library/Application Support/Steam"));
        }
        return workshopCandidates(anchors, roots);
    }

    static List<Path> workshopCandidates(Collection<Path> anchors, Collection<Path> steamRoots) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        for (Path anchor : anchors) for (Path path = anchor.toAbsolutePath().normalize(); path != null; path = path.getParent()) {
            candidates.add(path.resolve("workshop/content/108600/3805285544"));
            candidates.add(path.resolve("steamapps/workshop/content/108600/3805285544"));
            candidates.add(path.resolve("content/108600/3805285544"));
            candidates.add(path.resolve("3805285544"));
            roots.add(path);
        }
        roots.addAll(steamRoots);
        // Read only Steam's own library manifests; never scan whole disks or initialize game APIs.
        for (Path root : new ArrayList<>(roots)) {
            for (String name : List.of("steamapps/libraryfolders.vdf", "config/libraryfolders.vdf")) {
                Path file = root.resolve(name);
                try {
                    if (Files.isRegularFile(file) && Files.size(file) <= 1024 * 1024)
                        roots.addAll(libraryPaths(Files.readString(file)));
                } catch (IOException | RuntimeException ignored) { }
            }
        }
        for (Path root : roots) candidates.add(root.toAbsolutePath().normalize().resolve("steamapps/workshop/content/108600/3805285544"));
        return List.copyOf(candidates);
    }

    static List<Path> libraryPaths(String vdf) {
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        // Both current "path" entries and legacy numeric-library entries, with VDF escapes.
        var entries = Pattern.compile("\"(?:path|[0-9]+)\"\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(vdf);
        while (entries.find()) {
            String value = entries.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
            try {
                Path path = Path.of(value);
                if (path.isAbsolute()) result.add(path.normalize());
            } catch (InvalidPathException ignored) { }
        }
        return List.copyOf(result);
    }
    public static Path installationDirectory() { return configDirectory().resolve("PZ_Optimization"); }
    public static Path pendingDirectory() { return configDirectory().resolve("PZ_Optimization.pending"); }
    public static Path disabledMarker() { return configDirectory().resolve("PZOptPreloader/disabled"); }
}
