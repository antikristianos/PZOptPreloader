package antikristianos.pzoptpreloader;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Locates the installed platform's FMOD core without copying or replacing native files. */
public final class NativeLibraries {
    private NativeLibraries() { }
    public static Path fmodCore() {
        if (RuntimeState.payload == null || RuntimeState.server) return null;
        Path game = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        LinkedHashSet<Path> directories = new LinkedHashSet<>();
        directories.add(game.resolve("natives"));
        directories.add(game);
        directories.add(RuntimeState.payload.root.resolve("natives"));
        for (String entry : System.getProperty("java.library.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) directories.add(Path.of(entry).toAbsolutePath().normalize());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> names = os.contains("win") ? List.of("fmod.dll", "fmod64.dll")
            : os.contains("mac") ? List.of("libfmod.dylib") : List.of("libfmod.so");
        for (Path directory : directories) for (String name : names) {
            Path candidate = directory.resolve(name);
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        return null;
    }
}
