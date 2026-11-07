package antikristianos.pzpeek42;

import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.asm.Advice;
import zombie.iso.fboRenderChunk.FBORenderTrees;

@Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees",methodName="addTree")
public final class PatchTrees {
    @Patch.OnEnter(skipOn=true) public static boolean before(@Patch.Argument(10) boolean stencil,
            @Patch.Argument(12) boolean transparent,@Patch.Argument(13) float cutaway){
        return TreeOpacity.hiddenSubmission(stencil,transparent,cutaway);
    }
    @Patch.OnExit public static void after(@Advice.Enter boolean hidden,@Patch.This FBORenderTrees batch){
        if(!hidden)TreeOpacity.after(batch);
    }
}
