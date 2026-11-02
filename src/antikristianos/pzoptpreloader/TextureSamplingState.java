package antikristianos.pzoptpreloader;

import zombie.core.textures.TextureDraw;

/** Render-thread generation for sampler state changed outside TextureID's own binding cache. */
public final class TextureSamplingState {
    public static long generation=1;
    private TextureSamplingState() { }

    public static void after(TextureDraw draw) {
        if(draw==null||draw.type==null)return;
        switch(draw.type) {
            case glTexParameteri, glBuffer, DrawModel, DrawSkyBox, DrawWater, DrawPuddles, DrawParticles,
                 glDoStartFrame, glDoStartFrameText, glDoEndFrame, glDoStartFrameFx, glDoEndFrameFx,
                 glDoStartFrameNoZoom, glDoStartFrameFlipY, FBORenderChunkStart, FBORenderChunkEnd,
                 NewFrame, DrawQueued, RenderQueued, DrawImGui -> generation++;
            default -> { }
        }
    }
}
