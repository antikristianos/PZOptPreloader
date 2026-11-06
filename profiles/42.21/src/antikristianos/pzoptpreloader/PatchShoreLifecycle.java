package antikristianos.pzoptpreloader;

import me.zed_0xff.zombie_buddy.Patch;

/** Releases per-world receiver targets through the existing native reset queue. */
public final class PatchShoreLifecycle {
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderLevels",methodName="freeFBO",strictMatch=true)
    public static final class FreeChunkWeather {
        @Patch.OnEnter public static void enter(@Patch.Argument(0) zombie.iso.fboRenderChunk.FBORenderChunk chunk){
            if(!RuntimeState.server&&RuntimeState.payload!=null&&Compatibility.currentGame())ShoreWeatherState.free(chunk);
        }
    }
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderCell",methodName="Reset",strictMatch=true)
    public static final class WorldReset {
        @Patch.OnExit public static void exit(){
            if(!RuntimeState.server&&RuntimeState.payload!=null&&Compatibility.currentGame()){ShoreReceiverState.reset();ShoreWeatherState.reset();}
        }
    }
}
