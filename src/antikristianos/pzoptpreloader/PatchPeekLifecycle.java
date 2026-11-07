package antikristianos.pzoptpreloader;

import me.zed_0xff.zombie_buddy.Patch;

/** This source is compiled/packaged only into the B42.22 slice. */
public final class PatchPeekLifecycle {
    @Patch(className="zombie.ZomboidFileSystem",methodName="loadMods")
    public static final class SessionMods {
        @Patch.OnEnter public static void enter(@Patch.AllArguments Object[] arguments){PeekIntegration.beforeModSet(arguments);}
        @Patch.OnExit public static void exit(@Patch.AllArguments Object[] arguments){PeekIntegration.afterModSet(arguments);}
    }
    @Patch(className="zombie.Lua.LuaManager",methodName="init",strictMatch=true)
    public static final class JavaModsReady {
        @Patch.OnEnter public static void enter(){PeekIntegration.afterJavaMods();}
    }
}
