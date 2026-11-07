package antikristianos.pzoptpreloader;

import java.io.File;
import java.net.URI;
import java.nio.file.*;

/** Resolves upstream direct reads while retaining stock paths absent from the download. */
public final class PayloadFile extends File {
    private static final long serialVersionUID = 1L;
    public PayloadFile(String path) { super(resolve(path)); }
    public PayloadFile(String parent, String child) { this(parent == null ? child : new File(parent, child).getPath()); }
    public PayloadFile(File parent, String child) { this(parent == null ? child : new File(parent, child).getPath()); }
    public PayloadFile(URI uri) { this(new File(uri).getPath()); }
    public static String resolve(String value) {
        Payload payload = RuntimeState.payload;
        if (payload == null || RuntimeState.server) return value;
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            Path gameRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path absolute = path.normalize();
            if (!absolute.startsWith(gameRoot)) return value;
            path = gameRoot.relativize(absolute);
        }
        String relative = path.toString().replace(File.separatorChar, '/');
        if (!relative.startsWith("media/") && !relative.startsWith("natives/") && !relative.equals("natives")) return value;
        Path media = payload.media.get(relative.toLowerCase(java.util.Locale.ROOT));
        if (media != null) return media.toString();
        Path candidate = payload.root.resolve(path).normalize();
        if (candidate.startsWith(payload.root) && (Files.isRegularFile(candidate)
                || relative.equals("natives") && Files.isDirectory(candidate))) return candidate.toString();
        return value;
    }
}
