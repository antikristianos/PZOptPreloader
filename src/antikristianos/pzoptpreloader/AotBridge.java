package antikristianos.pzoptpreloader;

import java.lang.management.ManagementFactory;
import java.util.List;
import me.zed_0xff.zombie_buddy.Exposer;

/** Own launcher setup replaces the upstream installer; explicit manual AOT remains supported. */
@Exposer.LuaClass(name="PZOptPreloader.AOT")
public final class AotBridge {
    public static volatile String status = "not requested; optional manual JVM AOT arguments";
    private static boolean reported;
    private AotBridge() { }
    public static String takeSetupNotice() { return LauncherAot.takeSetupNotice(); }
    public static String launcherStatus() { return LauncherAot.status; }
    public static void validate() {
        if (!configured()) return;
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (!args.contains("-XX:-AOTClassLinking")) {
            throw new IllegalStateException("AOT with PZOptPreloader requires -XX:-AOTClassLinking during recording and use; recreate caches recorded with early AOT linking");
        }
    }
    public static boolean configured() {
        if (RuntimeState.server) return false;
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (args.contains("-XX:AOTMode=off")) return false;
        return args.stream().anyMatch(arg -> arg.startsWith("-XX:AOTCacheOutput=") || arg.startsWith("-XX:AOTCache=")
            || arg.equals("-XX:AOTMode=record"));
    }
    public static Object option(String key, Object original) {
        return "aotCache".equals(key) ? Boolean.valueOf(configured()) : original;
    }
    public static String pinned(String original, String key) {
        return "aotCache".equals(key) ? (Boolean.getBoolean("pzoptpreloader.aotManaged") ? "automatic client launcher (PZOptPreloader)" : "manual Steam/JVM start options (PZOptPreloader)") : original;
    }
    public static synchronized void start() {
        if (reported || RuntimeState.server) return;
        reported = true;
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        String record = args.stream().filter(arg -> arg.startsWith("-XX:AOTCacheOutput=") || arg.equals("-XX:AOTMode=record")).findFirst().orElse(null);
        String cache = args.stream().filter(arg -> arg.startsWith("-XX:AOTCache=")).findFirst().orElse(null);
        String mode = Boolean.getBoolean("pzoptpreloader.aotManaged") ? "automatic" : "manual";
        if (!configured()) status = "not requested; optional manual JVM AOT arguments";
        else if (record != null) status = mode + " recording requested: " + record + "; JVM assembles the cache on exit";
        else status = mode + " cache requested: " + cache + "; confirm actual use in the JVM AOT log";
        System.out.println("[PZOptPreloader] AOT: " + status + "; jar-only classpath and -XX:-AOTClassLinking required; original installer/GC writer bypassed; own launcher status=" + LauncherAot.status);
    }
}
