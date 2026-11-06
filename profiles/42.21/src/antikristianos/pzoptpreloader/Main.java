package antikristianos.pzoptpreloader;

import java.lang.reflect.*;
import java.util.*;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        System.out.println("[PZOptPreloader] " + RuntimeState.status);
        Installation.startup();
        if (Installation.disabled()) return;
        // Buddy registers a verified, trusted signed preloader before invoking Main.
        // Prioritize immediately; first registration must not depend on a later game-init callback.
        prioritizeNextBoot();
        LauncherAot.startup();
    }
    @SuppressWarnings("unchecked")
    public static void prioritizeNextBoot() {
        if (Installation.disabled()) return;
        try {
            Class<?> loader = Class.forName("me.zed_0xff.zombie_buddy.Loader");
            Field field = loader.getDeclaredField("g_config"); field.setAccessible(true);
            Object config = field.get(null); Class<?> cls = config.getClass();
            Method preload = cls.getDeclaredMethod("preload_mods"); preload.setAccessible(true);
            Map<String, Object> current = (Map<String, Object>) preload.invoke(config);
            // Release and WIP have different Mod IDs but share this signed implementation.
            // Match Buddy's registered canonical JAR path rather than an edition's fixed ID.
            Path ownJar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            String ownId = null;
            for (Map.Entry<String, Object> entry : current.entrySet()) {
                if (!entry.getKey().equals("PZOptPreloader") && !entry.getKey().equals("PZOptPreloader_WIP")) continue;
                Method jarPath = entry.getValue().getClass().getDeclaredMethod("jarPath"); jarPath.setAccessible(true);
                if (ownJar.equals(((Path) jarPath.invoke(entry.getValue())).toRealPath())) { ownId = entry.getKey(); break; }
            }
            if (ownId == null || current.keySet().iterator().next().equals(ownId)) return;
            LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
            ordered.put(ownId, current.get(ownId)); ordered.putAll(current);
            RecordComponent[] components = cls.getRecordComponents(); Object[] values = new Object[components.length]; Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                Method accessor = components[i].getAccessor(); accessor.setAccessible(true);
                values[i] = components[i].getName().equals("preload_mods") ? ordered : accessor.invoke(config);
                types[i] = components[i].getType();
            }
            Constructor<?> constructor = cls.getDeclaredConstructor(types); constructor.setAccessible(true);
            config = constructor.newInstance(values); field.set(null, config);
            Method save = cls.getDeclaredMethod("save", cls); save.setAccessible(true); save.invoke(null, config);
            System.out.println("[PZOptPreloader] registered first in ZombieBuddy's next-start preload list; full restart required");
        } catch (ReflectiveOperationException | java.io.IOException | java.net.URISyntaxException error) {
            System.err.println("[PZOptPreloader] cannot prioritize next-start preload list: " + error);
        }
    }
}
