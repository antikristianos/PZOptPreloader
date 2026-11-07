package antikristianos.pzoptpreloader;

import java.lang.reflect.Method;

/** Starts the downloaded compiler cache at the real server's first executing shared-Lua pass. */
public final class ServerLua {
    public static volatile String status = "not started";
    public static volatile int starts;
    private static boolean checked;
    private static boolean reported;
    private static Method statistics;
    private ServerLua() { }

    public static synchronized void beforeLoad(String sub, boolean checksum) {
        if (checked || checksum || !"shared".equals(sub) || !RuntimeState.server
                || RuntimeState.payload == null || !RuntimeState.status.startsWith("active ")) return;
        checked = true;
        try {
            Class<?> compiler = Class.forName("pzopt.LuaPrecompiler");
            if (!(Boolean) compiler.getMethod("enabled").invoke(null)) {
                status = "disabled by luaPrecompile";
            } else {
                statistics = compiler.getMethod("stats");
                compiler.getMethod("start").invoke(null);
                starts++;
                status = "started for shared/server Lua and map scripts; client Lua excluded";
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            status = "precompile unavailable: " + error;
            System.err.println("[PZOptPreloader] server Lua: " + status + "; normal compiler fallback remains available");
            return;
        }
        System.out.println("[PZOptPreloader] server Lua: " + status);
    }

    public static synchronized void afterLoad(String sub, boolean checksum) {
        if (reported || checksum || !"server".equals(sub) || !RuntimeState.server || starts == 0) return;
        reported = true;
        try {
            System.out.println("[PZOptPreloader] server Lua precompile: " + statistics.invoke(null));
        } catch (ReflectiveOperationException error) {
            System.err.println("[PZOptPreloader] server Lua precompile statistics unavailable: " + error);
        }
    }

    /** Called from the downloaded helper: checksum-only client scripts do not need compilation on a server. */
    public static boolean includeDirectory(String directory) {
        return !"media/lua/client".equals(directory);
    }
}
