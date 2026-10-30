package antikristianos.pzoptpreloader;

import java.nio.file.Path;
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

    public static Path installationDirectory() { return configDirectory().resolve("PZ_Optimization"); }
    public static Path pendingDirectory() { return configDirectory().resolve("PZ_Optimization.pending"); }
    public static Path disabledMarker() { return configDirectory().resolve("PZOptPreloader/disabled"); }
}
