package antikristianos.pzoptpreloader;

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public final class Resources {
    public static volatile int luaPasses;
    private Resources() { }
    /** Only PZOpt calls use this bridge; stock and unrelated mod reads retain their original File. */
    public static File mediaFile(zombie.ZomboidFileSystem files, String subPath) {
        File stock = files.getMediaFile(subPath);
        String resolved = PayloadFile.resolve(stock.getPath());
        return resolved.equals(stock.getPath()) ? stock : new File(resolved);
    }
    @SuppressWarnings("unchecked")
    public static void mount() {
        Payload payload = RuntimeState.payload;
        if (payload == null || RuntimeState.server) return;
        try {
            Class<?> cls = Class.forName("zombie.ZomboidFileSystem");
            Object instance = cls.getField("instance").get(null);
            Map<String, String> map = (Map<String, String>) cls.getField("activeFileMap").get(instance);
            for (var entry : payload.media.entrySet()) map.put(entry.getKey(), entry.getValue().toString());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot mount PZ Optimization media", error); }
    }
    @SuppressWarnings("unchecked")
    public static void beforeLua(String sub) {
        Payload payload = RuntimeState.payload;
        if (payload == null || RuntimeState.server) return;
        mount();
        luaPasses++;
        try {
            List<String> loadList = (List<String>) Class.forName("zombie.Lua.LuaManager").getField("loadList").get(null);
            String prefix = "media/lua/" + sub + "/";
            for (String name : payload.media.keySet()) if (name.startsWith(prefix) && name.endsWith(".lua") && !loadList.contains(name)) loadList.add(name);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot mount PZ Optimization Lua", error); }
    }
    public static boolean ownLua(String relative, String absolute) {
        Payload payload = RuntimeState.payload;
        if (payload == null || RuntimeState.server || relative == null || absolute == null) return false;
        String normalized = relative.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("media/lua/") || !normalized.endsWith(".lua")) return false;
        Path file = payload.media.get(normalized);
        return file != null && file.toAbsolutePath().normalize().equals(Path.of(absolute).toAbsolutePath().normalize());
    }
}
