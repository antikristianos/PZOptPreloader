package antikristianos.pzoptpreloader;

import me.zed_0xff.zombie_buddy.Patch;

/** Releases per-world receiver targets through the existing native reset queue. */
public final class PatchShoreLifecycle {
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderCell",methodName="Reset",strictMatch=true)
    public static final class WorldReset {
        @Patch.OnExit public static void exit(){
            if(!RuntimeState.server&&RuntimeState.payload!=null&&Compatibility.CURRENT.equals(zombie.GitVersion.REVISION))ShoreReceiverState.reset();
        }
    }
}