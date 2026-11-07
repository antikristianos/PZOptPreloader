package antikristianos.pzoptpreloader;
import me.zed_0xff.zombie_buddy.Patch;

public final class PatchHooks {
    @Patch(className="zombie.ZomboidFileSystem", methodName="loadMods")
    public static final class MountMedia {
        @Patch.OnExit public static void after(@Patch.AllArguments Object[] args) { Resources.mount(); }
    }
    @Patch(className="zombie.Lua.LuaManager", methodName="LoadDirBase", strictMatch=true)
    public static final class MountLua {
        @Patch.OnEnter public static void before(@Patch.Argument(0) String sub, @Patch.Argument(1) boolean checksum) {
            ServerLua.beforeLoad(sub, checksum);
            Resources.beforeLua(sub);
        }
        @Patch.OnExit public static void after(@Patch.Argument(0) String sub, @Patch.Argument(1) boolean checksum) {
            ServerLua.afterLoad(sub, checksum);
        }
    }
    @Patch(className="zombie.network.NetChecksum$Checksummer", methodName="addFile", strictMatch=true)
    public static final class PayloadLuaChecksum {
        @Patch.OnEnter(skipOn=true) public static boolean before(@Patch.Argument(0) String relative, @Patch.Argument(1) String absolute) { return Resources.ownLua(relative, absolute); }
    }
}
