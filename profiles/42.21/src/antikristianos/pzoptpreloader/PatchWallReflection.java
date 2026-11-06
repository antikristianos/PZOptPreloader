package antikristianos.pzoptpreloader;

import me.zed_0xff.zombie_buddy.Patch;


/** Native-only ownership hooks, gated by the exact installed B42.22 methods. */
public final class PatchWallReflection {
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderChunk",methodName="beginRenderThread",strictMatch=true)
    public static final class BeginBake {
        @Patch.OnExit public static void exit(@Patch.This Object owner,@Patch.Argument(0) boolean clear){
            WallReflectionRepair.nativeBegin(owner,clear);
        }
    }
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderLevels",methodName="freeFBO",strictMatch=true)
    public static final class FreeBake {
        @Patch.OnEnter public static void enter(@Patch.Argument(0) Object owner){
            WallReflectionRepair.nativeFree(owner);
        }
    }
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderLevels",methodName="clearCache",strictMatch=true)
    public static final class ClearBake {
        @Patch.OnEnter public static void enter(@Patch.Argument(0) Object owner){
            WallReflectionRepair.nativeFree(owner);
        }
    }
}