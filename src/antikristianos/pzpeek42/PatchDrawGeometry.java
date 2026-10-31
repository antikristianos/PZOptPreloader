package antikristianos.pzpeek42;

import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.asm.Advice;
import zombie.core.opengl.VBORenderer;
import zombie.core.textures.Texture;
import zombie.iso.fboRenderChunk.FBORenderTrees;

public final class PatchDrawGeometry {
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees",methodName="pzoptShadedQuad")
    public static class Quad {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) VBORenderer vbor,@Patch.Argument(1) Object tree,
                @Patch.Argument(2) Texture texture,@Patch.Argument(3) float xTL,@Patch.Argument(4) float yTL,
                @Patch.Argument(5) float xTR,@Patch.Argument(6) float yTR,@Patch.Argument(7) float xBR,@Patch.Argument(8) float yBR,
                @Patch.Argument(9) float xBL,@Patch.Argument(10) float yBL,@Patch.Argument(11) float top,@Patch.Argument(12) float bottom,
                @Patch.Argument(13) int unitsX,@Patch.Argument(14) int unitsY,@Patch.Local("mode") int mode){
            mode=DrawGeometry.begin(vbor,tree,texture,xTL,yTL,xTR,yTR,xBR,yBR,xBL,yBL,top,bottom,unitsX,unitsY);return mode==2;
        }
        @Patch.OnExit(onThrowable=Throwable.class) public static void exit(@Patch.Local("mode") int mode,@Advice.Thrown Throwable error){DrawGeometry.end(mode,error);}
    }
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees",methodName="renderTree")
    public static class Draw {
        @Patch.OnEnter public static void enter(@Patch.This FBORenderTrees batch,@Patch.Argument(0) Object tree,
                @Patch.Local("depthScope") boolean opened){opened=DrawGeometry.beginDraw(batch,tree);}
        @Patch.OnExit(onThrowable=Throwable.class) public static void exit(@Patch.Local("depthScope") boolean opened){DrawGeometry.endDraw(opened);}
    }
}
