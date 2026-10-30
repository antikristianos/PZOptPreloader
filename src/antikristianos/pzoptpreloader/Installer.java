package antikristianos.pzoptpreloader;

import java.io.*;
import java.net.URI;
import javax.net.ssl.HttpsURLConnection;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;
import org.json.*;

/** Downloads upstream data only. Never executes its installers or writes to the game directory. */
public final class Installer {
    public static final String RELEASES_URL = "https://github.com/xD3I/PZ_Optimization/releases";
    private static final String API = "https://api.github.com/repos/xD3I/PZ_Optimization/releases?per_page=30&page=";
    private static final long MAX_DOWNLOAD = 256L * 1024 * 1024;
    private static final long MAX_EXPANDED = 1024L * 1024 * 1024;
    public record Progress(String phase, long completed, long total, String detail) { }
    record Response(int status, long length, InputStream body) implements AutoCloseable {
        public void close() throws IOException { body.close(); }
    }
    interface Transport { Response get(URI uri) throws Exception; }
    record Release(URI uri, String digest, long size, String tag, String commit, long published) { }
    public static final class Failure extends IOException {
        public final String reason;
        Failure(String reason, String detail) { super(detail); this.reason = reason; }
    }
    private Installer() { }

    public static void install(Path target, String revision, Consumer<Progress> progress, BooleanSupplier cancelled) throws Exception {
        install(target, revision, progress, cancelled, Installer::request);
    }

    private static Response request(URI uri) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !Set.of("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com").contains(uri.getHost()))
                throw new IOException("Unexpected download redirect origin");
            HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(45000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "PZOptPreloader");
            connection.setRequestProperty("Accept", "api.github.com".equals(uri.getHost()) ? "application/vnd.github+json" : "application/octet-stream");
            try {
                int status = connection.getResponseCode();
                if (Set.of(301, 302, 303, 307, 308).contains(status)) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Missing download redirect location");
                    uri = uri.resolve(location); connection.disconnect(); continue;
                }
                InputStream body = status == 200 ? connection.getInputStream() : connection.getErrorStream();
                if (body == null) body = InputStream.nullInputStream();
                return new Response(status, connection.getContentLengthLong(), new FilterInputStream(body) {
                    @Override public void close() throws IOException {
                        try { super.close(); } finally { connection.disconnect(); }
                    }
                });
            } catch (Exception error) { connection.disconnect(); throw error; }
        }
        throw new IOException("Too many download redirects");
    }

    static void install(Path target, String revision, Consumer<Progress> progress, BooleanSupplier cancelled, Transport transport) throws Exception {
        install(target, revision, progress, cancelled, transport, null);
    }

    static void install(Path target, String revision, Consumer<Progress> progress, BooleanSupplier cancelled, Transport transport, Release offered) throws Exception {
        target = target.toAbsolutePath().normalize();
        Path parent = target.getParent();
        Files.createDirectories(parent.resolve("PZOptPreloader"));
        Path lockPath = parent.resolve("PZOptPreloader/install.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException error) { throw new Failure("busy", "Another installation is already running"); }
            if (lock == null) throw new Failure("busy", "Another process is installing PZ Optimization");
            try (lock) {
                progress.accept(new Progress("checking", 0, 0, revision));
                Release release = offered != null ? offered : findRelease(revision, transport, cancelled);
                Path work = Files.createTempDirectory(parent.resolve("PZOptPreloader"), "install-");
                try {
                    Path archive = work.resolve("payload.zip");
                    download(release, archive, transport, progress, cancelled);
                    Path extracted = Files.createDirectory(work.resolve("payload"));
                    extract(archive, extracted, progress, cancelled);
                    Payload verified = new Payload(Payload.verifiedSource(extracted, revision));
                    String commit = verified.build.getProperty("commit", "").toLowerCase(Locale.ROOT);
                    if (!release.commit.isBlank() && !(commit.startsWith(release.commit) || release.commit.startsWith(commit)) || commit.isBlank())
                        throw new IOException("Archive build commit differs from the selected release");
                    Properties receipt = new Properties();
                    receipt.setProperty("tag", release.tag); receipt.setProperty("sha256", release.digest);
                    receipt.setProperty("published", Long.toString(release.published));
                    try (OutputStream output = Files.newOutputStream(extracted.resolve("pzoptpreloader-release.properties"), StandardOpenOption.CREATE_NEW)) {
                        receipt.store(output, "PZOptPreloader download identity; upstream files are unchanged");
                    }
                    checkCancelled(cancelled);
                    progress.accept(new Progress("publish", 0, 0, target.toString()));
                    publish(extracted, target);
                    progress.accept(new Progress("complete", 1, 1, target.toString()));
                } finally { removeWork(work); }
            }
        }
    }

    static Release findRelease(String revision, Transport transport, BooleanSupplier cancelled) throws Exception {
        String expected = "pzopt-" + revision + "-classes.zip";
        for (int page = 1; page <= 10; page++) {
            checkCancelled(cancelled);
            try (Response response = transport.get(URI.create(API + page))) {
                requireSuccess(response);
                byte[] bytes = response.body.readNBytes(8 * 1024 * 1024 + 1);
                if (bytes.length > 8 * 1024 * 1024) throw new IOException("Release metadata exceeds limit");
                JSONArray releases = new JSONArray(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                for (int index = 0; index < releases.length(); index++) {
                    JSONObject release = releases.getJSONObject(index);
                    if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue;
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets == null) continue;
                    for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                        JSONObject asset = assets.getJSONObject(assetIndex);
                        if (!expected.equals(asset.optString("name"))) continue;
                        URI uri = URI.create(asset.getString("browser_download_url"));
                        if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                                || !uri.getPath().startsWith("/xD3I/PZ_Optimization/releases/download/")
                                || !uri.getPath().endsWith("/" + expected) || uri.getUserInfo() != null)
                            throw new IOException("Unexpected release download URL");
                        String digest = asset.optString("digest", "");
                        if (!digest.matches("sha256:[a-fA-F0-9]{64}")) throw new Failure("checksum", "Release has no SHA-256 digest");
                        long size = asset.optLong("size", -1);
                        if (size <= 0 || size > MAX_DOWNLOAD) throw new IOException("Unexpected archive size: " + size);
                        String tag = release.optString("tag_name", "");
                        var suffix = java.util.regex.Pattern.compile("-([a-fA-F0-9]{7,40})$").matcher(tag);
                        String commit = suffix.find() ? suffix.group(1).toLowerCase(Locale.ROOT) : "";
                        String published = release.optString("published_at", "");
                        long epoch = published.isBlank() ? 0 : Instant.parse(published).getEpochSecond();
                        return new Release(uri, digest.substring(7).toLowerCase(Locale.ROOT), size, tag, commit, epoch);
                    }
                }
                if (releases.length() < 30) break;
            }
        }
        throw new Failure("noRelease", "No compatible release archive found for game revision " + revision);
    }

    static Release checkUpdate(Payload installed) throws Exception {
        return findRelease(installed.build.getProperty("revision"), Installer::request, () -> false);
    }

    static boolean isNewer(Payload installed, Release release) throws IOException {
        Properties receipt = receipt(installed);
        if (!receipt.isEmpty()) {
            if (release.digest.equals(receipt.getProperty("sha256"))) return false;
            try {
                long published = Long.parseLong(receipt.getProperty("published", "0"));
                if (published > 0 && release.published > 0)
                    return release.published > published || (release.tag.equals(receipt.getProperty("tag")) && release.published == published);
            } catch (NumberFormatException error) { throw new IOException("Invalid installed release timestamp", error); }
        }
        String commit = installed.build.getProperty("commit", "").toLowerCase(Locale.ROOT);
        if (commit.isBlank() || release.commit.isBlank()) throw new IOException("Cannot compare the installed commit with release " + release.tag);
        if (commit.startsWith(release.commit) || release.commit.startsWith(commit)) return false;
        long built;
        try { built = Long.parseLong(installed.build.getProperty("built", "0")); }
        catch (NumberFormatException error) { throw new IOException("Invalid installed build timestamp", error); }
        if (built <= 0 || release.published <= 0) throw new IOException("Cannot compare release build timestamps");
        return release.published > built;
    }

    private static Properties receipt(Payload installed) throws IOException {
        Properties result = new Properties();
        Path path = installed.root.resolve("pzoptpreloader-release.properties");
        if (Files.isRegularFile(path)) try (InputStream input = Files.newInputStream(path)) { result.load(input); }
        return result;
    }

    static String installedLabel(Payload installed) throws IOException {
        return receipt(installed).getProperty("tag", installed.build.getProperty("commit", "unknown"));
    }

    static void installUpdate(Path target, String revision, Release release, Consumer<Progress> progress, BooleanSupplier cancelled) throws Exception {
        install(target, revision, progress, cancelled, Installer::request, release);
    }

    /** Keep active media paths stable in every client/Coop/server JVM until it exits. */
    static final class SourceUse implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        SourceUse(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }

    static SourceUse useSource(Path config) throws IOException {
        Path directory = config.resolve("PZOptPreloader"); Files.createDirectories(directory);
        FileChannel channel = FileChannel.open(directory.resolve("source.lock"), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock(0, Long.MAX_VALUE, true);
            if (lock == null) throw new IOException("Another process is activating a PZ Optimization update");
            return new SourceUse(channel, lock);
        } catch (IOException | RuntimeException error) { channel.close(); throw error; }
    }

    static boolean activatePending(Path config, String revision) throws IOException {
        Path pending = config.resolve("PZ_Optimization.pending");
        if (!Files.isDirectory(pending)) return false;
        Path directory = config.resolve("PZOptPreloader"); Files.createDirectories(directory);
        try (FileChannel installer = FileChannel.open(directory.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileChannel source = FileChannel.open(directory.resolve("source.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock installLock = null, sourceLock = null;
            try {
                installLock = installer.tryLock();
                if (installLock == null) return false;
                sourceLock = source.tryLock();
                if (sourceLock == null) return false;
                if (!Files.isDirectory(pending)) return false;
                Payload.verifiedSource(pending, revision);
                publish(pending, config.resolve("PZ_Optimization"));
                System.out.println("[PZOptPreloader] verified pending update activated before class injection");
                return true;
            } catch (OverlappingFileLockException busy) { return false; }
            finally {
                if (sourceLock != null) sourceLock.release();
                if (installLock != null) installLock.release();
            }
        }
    }

    private static void requireSuccess(Response response) throws IOException {
        if (response.status == 403 || response.status == 429) throw new Failure("rateLimit", "GitHub HTTP " + response.status + "; retry later");
        if (response.status != 200) throw new IOException("GitHub HTTP " + response.status);
    }

    static void download(Release release, Path archive, Transport transport, Consumer<Progress> progress, BooleanSupplier cancelled) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long received = 0;
        progress.accept(new Progress("download", 0, release.size, release.uri.toString()));
        try (Response response = transport.get(release.uri);
                OutputStream output = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)) {
            requireSuccess(response);
            if (response.length > MAX_DOWNLOAD || (response.length >= 0 && response.length != release.size))
                throw new IOException("Release archive length differs from metadata");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = response.body.read(buffer)) != -1) {
                checkCancelled(cancelled);
                received += count;
                if (received > release.size || received > MAX_DOWNLOAD) throw new IOException("Release archive exceeds limit");
                digest.update(buffer, 0, count); output.write(buffer, 0, count);
                progress.accept(new Progress("download", received, release.size, release.uri.toString()));
            }
        }
        progress.accept(new Progress("verify", received, release.size, "SHA-256"));
        if (received != release.size || !HexFormat.of().formatHex(digest.digest()).equals(release.digest))
            throw new Failure("checksum", "Release archive is incomplete or SHA-256 does not match");
    }

    static void extract(Path archive, Path staging, Consumer<Progress> progress, BooleanSupplier cancelled) throws Exception {
        Set<String> names = new HashSet<>();
        long expanded = 0;
        int completed = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            if (zip.size() > 20000) throw new IOException("Too many archive entries");
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                checkCancelled(cancelled);
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                Path destination = staging.resolve(name).normalize();
                if (name.startsWith("/") || name.contains(":") || name.indexOf('\0') >= 0 || !destination.startsWith(staging)
                        || destination.equals(staging) || !names.add(destination.toString().toLowerCase(Locale.ROOT)))
                    throw new IOException("Unsafe or duplicate archive entry: " + name);
                if (entry.isDirectory()) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    try (InputStream input = zip.getInputStream(entry);
                            OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                        byte[] buffer = new byte[65536];
                        int count;
                        long fileSize = 0;
                        while ((count = input.read(buffer)) != -1) {
                            checkCancelled(cancelled);
                            expanded += count; fileSize += count;
                            if (expanded > MAX_EXPANDED || fileSize > 128L * 1024 * 1024) throw new IOException("Expanded archive exceeds limit");
                            output.write(buffer, 0, count);
                        }
                    }
                }
                progress.accept(new Progress("extract", ++completed, zip.size(), destination.toString()));
            }
        }
    }

    private static void publish(Path staging, Path target) throws IOException {
        if (Files.isSymbolicLink(target)) throw new IOException("Installation target is a symbolic link");
        Path backup = null;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            backup = target.resolveSibling(target.getFileName() + ".backup-" + UUID.randomUUID());
            Files.move(target, backup);
        }
        try { Files.move(staging, target); }
        catch (IOException failure) {
            if (backup != null) {
                try { Files.move(backup, target); }
                catch (IOException restore) { failure.addSuppressed(restore); }
            }
            throw failure;
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Installation cancelled");
    }

    private static void removeWork(Path work) {
        try (var files = Files.walk(work)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch (IOException error) { System.err.println("[PZOptPreloader] temporary install cleanup: " + error); }
    }
}
