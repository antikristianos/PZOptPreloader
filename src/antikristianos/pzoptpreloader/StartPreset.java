package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** First-use template; existing user option files, including intentionally empty ones, are preserved. */
public final class StartPreset {
    public static volatile String status = "not read";
    private StartPreset() { }
    public static Properties apply(Properties loaded, File file) {
        if (RuntimeState.payload == null) return loaded;
        Path target = file.toPath().toAbsolutePath().normalize();
        if (Files.exists(target) || !loaded.isEmpty()) {
            status = "existing user options preserved: " + target;
            System.out.println("[PZOptPreloader] preset: " + status);
            return loaded;
        }
        String role = RuntimeState.server ? "server" : "client";
        Properties preset = new Properties();
        Path temporary = null;
        try (InputStream input = StartPreset.class.getResourceAsStream("presets/" + role + ".properties")) {
            if (input == null) throw new IOException("Missing packaged " + role + " preset");
            preset.load(input);
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".pzopt-preset-", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                preset.store(output, "PZOptPreloader first-use " + role + " preset; change through Options > PZ Optimization");
            }
            Files.move(temporary, target); // Never replace an existing option file.
            loaded.putAll(preset);
            status = "applied " + role + " first-use preset (" + preset.size() + " values): " + target;
            System.out.println("[PZOptPreloader] preset: " + status);
        } catch (FileAlreadyExistsException concurrent) {
            // A Coop child or another process created it after the first read; consume that winner.
            try (InputStream input = Files.newInputStream(target)) {
                Properties winner = new Properties(); winner.load(input);
                loaded.clear(); loaded.putAll(winner);
                status = "concurrent user options preserved: " + target;
                System.out.println("[PZOptPreloader] preset: " + status);
            } catch (IOException error) { failed(target, error); }
        } catch (IOException error) { failed(target, error); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException error) { System.err.println("[PZOptPreloader] preset temporary file cleanup: " + error); }
        }
        return loaded;
    }
    private static void failed(Path target, IOException error) {
        status = "not applied to " + target + ": " + error;
        System.err.println("[PZOptPreloader] preset: " + status + "; keeping original defaults");
    }
}
