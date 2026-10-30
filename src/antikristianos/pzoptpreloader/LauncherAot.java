package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import me.zed_0xff.zombie_buddy.Agent;
import org.json.JSONArray;
import org.json.JSONObject;

/** Own Windows client JSON lifecycle; the upstream installer and its tuning writer stay bypassed. */
public final class LauncherAot {
    public record Result(String state, String setupNotice, Path cache, boolean changed) { }
    public static volatile String status = "not checked";
    private static String setupNotice;
    private static boolean checked;
    private LauncherAot() { }

    public static synchronized void startup() {
        if (checked) return;
        checked = true;
        if (RuntimeState.status.startsWith("FAILED") || RuntimeState.status.startsWith("source unavailable:")) { status = "preloader unavailable; launcher untouched"; return; }
        if (Installation.disabled()
                || !Boolean.parseBoolean(System.getProperty("pzoptpreloader.aotAuto", "true"))) {
            status = "automatic setup disabled"; return;
        }
        if (RuntimeState.detectServer(System.getProperty("sun.java.command", ""))) {
            status = "server profile excluded"; return;
        }
        Path launcher = activeLauncher();
        if (launcher == null) { status = "manual/custom launch; no active ProjectZomboid64.json"; return; }
        try {
            Payload payload = RuntimeState.payload != null ? RuntimeState.payload : new Payload(Payload.discover());
            Result result = prepare(launcher, Agent.configDir().resolve("PZOptPreloader/aot"),
                    ManagementFactory.getRuntimeMXBean().getInputArguments(), payload.build.getProperty("commit", "unknown"));
            status = result.state(); setupNotice = result.setupNotice();
        } catch (Exception error) {
            status = "automatic setup failed: " + error.getMessage();
            System.err.println("[PZOptPreloader] AOT: " + status);
            return;
        }
        System.out.println("[PZOptPreloader] AOT launcher: " + status);
    }

    static Path activeLauncher() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) return null;
        String explicit = System.getProperty("pzoptpreloader.aotLauncher");
        if (explicit != null) return Path.of(explicit).toAbsolutePath().normalize();
        String command = ProcessHandle.current().info().command().orElse("");
        if (command.isEmpty()) return null;
        Path executable = Path.of(command);
        if (!executable.getFileName().toString().equalsIgnoreCase("ProjectZomboid64.exe")) return null;
        for (String arg : ProcessHandle.current().info().arguments().orElse(new String[0])) {
            if (arg.toLowerCase(Locale.ROOT).endsWith(".json")) return Path.of(arg).toAbsolutePath().normalize();
        }
        Path site = executable.resolveSibling("ProjectZomboid64.site.json");
        return Files.isRegularFile(site) ? site : executable.resolveSibling("ProjectZomboid64.json");
    }

    public static Result prepare(Path launcher, Path cacheDirectory, List<String> processArgs, String payloadCommit) throws Exception {
        if (Runtime.version().feature() < 25) return new Result("JVM 25 required", null, null, false);
        launcher = launcher.toRealPath();
        if (!launcher.getFileName().toString().matches("(?i)ProjectZomboid64(?:\\.site)?\\.json"))
            throw new IOException("Unrecognized client launcher filename");
        byte[] original = Files.readAllBytes(launcher);
        boolean bom = original.length >= 3 && original[0] == (byte) 0xef && original[1] == (byte) 0xbb && original[2] == (byte) 0xbf;
        String text = new String(original, bom ? 3 : 0, original.length - (bom ? 3 : 0), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(text);
        if (json.getString("mainClass").replace('/', '.').equals("zombie.network.GameServer"))
            return new Result("server launcher excluded", null, null, false);
        List<String> vm = strings(json.getJSONArray("vmArgs"));
        boolean owned = vm.contains("-Dpzoptpreloader.aotManaged=true");
        if (vm.contains("-Dpzoptpreloader.aotAuto=false") || vm.contains("-XX:AOTMode=off") || processArgs.contains("-XX:AOTMode=off"))
            return new Result("automatic setup disabled by launcher options", null, null, false);
        if (!owned && (vm.stream().anyMatch(LauncherAot::aotOption) || processArgs.stream().anyMatch(LauncherAot::aotOption)))
            return new Result("existing manual AOT options preserved", null, null, false);

        List<String> jars = new ArrayList<>();
        for (String entry : strings(json.getJSONArray("classpath"))) {
            Path path = launcher.getParent().resolve(entry).normalize();
            if (path.equals(launcher.getParent()) && (entry.equals(".") || entry.equals("./") || entry.equals(".\\"))) continue;
            if (!Files.isRegularFile(path) || !entry.toLowerCase(Locale.ROOT).endsWith(".jar"))
                throw new IOException("AOT requires JAR classpath entries; cannot override " + entry);
            jars.add(path.toAbsolutePath().toString());
        }
        if (jars.isEmpty()) throw new IOException("Launcher has no JAR classpath");
        String classpath = String.join(File.pathSeparator, jars);
        List<String> retained = vm.stream().filter(value -> !managedOption(value)).toList();
        // Explicit classpath choices remain authoritative; never silently discard another directory/resource root.
        for (String value : retained) if (value.startsWith("-Djava.class.path=") && !value.equals("-Djava.class.path=" + classpath))
            throw new IOException("Existing custom java.class.path needs manual AOT setup");
        retained = retained.stream().filter(value -> !value.equals("-Djava.class.path=" + classpath)).toList();
        StringBuilder identity = new StringBuilder(System.getProperty("java.runtime.version")).append('\n').append(payloadCommit)
                .append('\n').append(json.getJSONArray("classpath")).append('\n').append(json.opt("windows"))
                .append('\n').append(retained);
        for (String jar : jars) identity.append('\n').append(jar).append(':').append(Files.size(Path.of(jar)))
                .append(':').append(Files.getLastModifiedTime(Path.of(jar)).toMillis());
        Path ownJar = Path.of(LauncherAot.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        identity.append('\n').append(Files.isRegularFile(ownJar) ? fileHash(ownJar) : ownJar);
        identity.append('\n').append(fileHash(Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI())));
        String profile = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        Files.createDirectories(cacheDirectory);
        Path cache = cacheDirectory.toAbsolutePath().normalize().resolve("client-" + profile + ".aot");
        boolean ready = Files.isRegularFile(cache) && Files.size(cache) > 0;
        List<String> desired = new ArrayList<>(retained);
        if (!desired.contains("-Djava.class.path=" + classpath)) desired.add("-Djava.class.path=" + classpath);
        desired.add("-XX:-AOTClassLinking"); desired.add("-Dpzoptpreloader.aotManaged=true");
        if (desired.stream().noneMatch(value -> value.startsWith("-Xlog:") && value.contains("aot"))) desired.add("-Xlog:aot=info");
        if (ready) { desired.add("-XX:AOTMode=auto"); desired.add("-XX:AOTCache=" + cache); }
        else desired.add("-XX:AOTCacheOutput=" + cache);
        if (desired.equals(vm)) return new Result(ready ? "cache use configured: " + cache : "recording configured; cache is assembled on normal exit: " + cache, null, cache, false);
        int[] span = vmArgsSpan(text);
        String replacement = new JSONArray(desired).toString(2);
        if (text.contains("\r\n")) replacement = replacement.replace("\n", "\r\n");
        String changed = text.substring(0, span[0]) + replacement + text.substring(span[1]);
        byte[] bytes = ((bom ? "\ufeff" : "") + changed).getBytes(StandardCharsets.UTF_8);
        // Preserve one original snapshot; never overwrite a user's later tuning or an existing backup.
        Path backup = cacheDirectory.resolve("launcher-before-aot.json");
        try { Files.write(backup, original, StandardOpenOption.CREATE_NEW); } catch (FileAlreadyExistsException ignored) { }
        Path temporary = Files.createTempFile(launcher.getParent(), ".pzopt-aot-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (!Arrays.equals(original, Files.readAllBytes(launcher))) throw new IOException("Launcher changed concurrently; update cancelled");
            try {
                try { Files.move(temporary, launcher, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, launcher, StandardCopyOption.REPLACE_EXISTING); }
            }
            catch (FileSystemException openByLauncher) {
                // Windows' native launcher retains an open JSON handle without delete sharing.
                // Keep that file identity, lock it and restore its original bytes if writing fails.
                updateOpenLauncher(launcher, original, bytes);
            }
        } finally { Files.deleteIfExists(temporary); }
        return new Result(ready ? "cache use prepared for a future normal start" : "cache recording prepared for a future normal start", owned ? null : "initial", cache, true);
    }

    public static synchronized String takeSetupNotice() { String notice = setupNotice; setupNotice = null; return notice; }
    static boolean aotOption(String value) { return value.startsWith("-XX:AOTCache") || value.startsWith("-XX:AOTMode=") || value.startsWith("-XX:AOTConfiguration=") || value.equals("-XX:+AOTClassLinking"); }
    static boolean managedOption(String value) { return value.startsWith("-XX:AOTCacheOutput=") || value.startsWith("-XX:AOTCache=") || value.startsWith("-XX:AOTMode=") || value.equals("-XX:-AOTClassLinking") || value.equals("-XX:+AOTClassLinking") || value.equals("-Dpzoptpreloader.aotManaged=true") || value.equals("-Xlog:aot=info"); }
    private static void updateOpenLauncher(Path launcher, byte[] original, byte[] changed) throws IOException {
        try (FileChannel channel = FileChannel.open(launcher, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Launcher is locked; update cancelled");
            if (channel.size() != original.length) throw new IOException("Launcher changed concurrently; update cancelled");
            ByteBuffer current = ByteBuffer.allocate(original.length);
            while (current.hasRemaining() && channel.read(current) != -1) { }
            if (!Arrays.equals(current.array(), original)) throw new IOException("Launcher changed concurrently; update cancelled");
            try { writeChannel(channel, changed); }
            catch (IOException error) {
                try { writeChannel(channel, original); } catch (IOException restore) { error.addSuppressed(restore); }
                throw error;
            }
        }
    }
    private static void writeChannel(FileChannel channel, byte[] bytes) throws IOException {
        channel.position(0); ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.truncate(bytes.length); channel.force(true);
    }
    private static List<String> strings(JSONArray array) { List<String> list = new ArrayList<>(); for (int i = 0; i < array.length(); i++) list.add(array.getString(i)); return list; }
    private static String fileHash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    /** Replace only the root vmArgs array; all unrelated JSON text/keys remain byte-identical. */
    static int[] vmArgsSpan(String text) throws IOException {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == '"') {
                int start = i; i = stringEnd(text, i);
                if (depth == 1 && text.substring(start, i + 1).equals("\"vmArgs\"")) {
                    int cursor = i + 1; while (Character.isWhitespace(text.charAt(cursor))) cursor++;
                    if (text.charAt(cursor++) != ':') continue;
                    while (Character.isWhitespace(text.charAt(cursor))) cursor++;
                    if (text.charAt(cursor) != '[') throw new IOException("Root vmArgs is not an array");
                    int begin = cursor; int arrays = 0;
                    for (; cursor < text.length(); cursor++) {
                        char value = text.charAt(cursor);
                        if (value == '"') cursor = stringEnd(text, cursor);
                        else if (value == '[') arrays++;
                        else if (value == ']' && --arrays == 0) return new int[]{begin, cursor + 1};
                    }
                }
            }
        }
        throw new IOException("Root vmArgs array not found");
    }
    private static int stringEnd(String text, int start) throws IOException {
        for (int i = start + 1; i < text.length(); i++) { if (text.charAt(i) == '\\') i++; else if (text.charAt(i) == '"') return i; }
        throw new IOException("Unterminated JSON string");
    }
}
