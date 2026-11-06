package antikristianos.pzoptpreloader;

import me.zed_0xff.zombie_buddy.Patch;

/** Mirror only actual current native bake mip generation, in the original render-stream position. */
public final class PatchWorldSunMips {
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderChunk",methodName="endRenderThread",strictMatch=true)
    public static final class End {
        @Patch.OnExit public static void exit(@Patch.This Object owner){if(WorldSunLayerRepair.nativeMipReady())WorldSunLayerSources.mips(owner);}
    }
}
