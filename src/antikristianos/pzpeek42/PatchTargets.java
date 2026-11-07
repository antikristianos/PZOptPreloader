package antikristianos.pzpeek42;

import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.asm.Advice;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoTree;

/** Cache only geometric answers: all caller side effects and native animation remain original. */
public final class PatchTargets {
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="inCharReveal",strictMatch=true)
    public static class Query0 {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Local("cached") int cached){
            cached=RenderTargets.lookup(0,tree,held,0);return cached!=0;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean hit,@Patch.Local("cached") int cached,
                @Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Return(readOnly=false) boolean value){
            if(hit)value=(cached&2)!=0;else RenderTargets.remember(0,tree,held,0,value);
        }
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="inVehicleCover",strictMatch=true)
    public static class Query1 {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Local("cached") int cached){
            cached=RenderTargets.lookup(1,tree,held,0);return cached!=0;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean hit,@Patch.Local("cached") int cached,
                @Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Return(readOnly=false) boolean value){
            if(hit)value=(cached&2)!=0;else RenderTargets.remember(1,tree,held,0,value);
        }
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="inRoadView",strictMatch=true)
    public static class Query2 {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Local("cached") int cached){
            cached=RenderTargets.lookup(2,tree,held,0);return cached!=0;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean hit,@Patch.Local("cached") int cached,
                @Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Return(readOnly=false) boolean value){
            if(hit)value=(cached&2)!=0;else RenderTargets.remember(2,tree,held,0,value);
        }
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="inAimReveal",strictMatch=true)
    public static class Query3 {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Local("cached") int cached){
            cached=RenderTargets.lookup(3,tree,held,0);return cached!=0;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean hit,@Patch.Local("cached") int cached,
                @Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Return(readOnly=false) boolean value){
            if(hit)value=(cached&2)!=0;else RenderTargets.remember(3,tree,held,0,value);
        }
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="inSeCorridor",strictMatch=true)
    public static class Query4 {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Argument(2) int extent,@Patch.Local("cached") int cached){
            cached=RenderTargets.lookup(4,tree,held,extent);return cached!=0;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean hit,@Patch.Local("cached") int cached,
                @Patch.Argument(0) IsoTree tree,@Patch.Argument(1) boolean held,@Patch.Argument(2) int extent,@Patch.Return(readOnly=false) boolean value){
            if(hit)value=(cached&2)!=0;else RenderTargets.remember(4,tree,held,extent,value);
        }
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="cameraPlayerDotTo",strictMatch=true)
    public static class Dot {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoGridSquare square,@Patch.Local("cached") long cached){
            cached=RenderTargets.dot(square);return cached!=0;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean hit,@Patch.Local("cached") long cached,
                @Patch.Argument(0) IsoGridSquare square,@Patch.Return(readOnly=false) float value){
            if(hit)value=Float.intBitsToFloat((int)cached);else RenderTargets.rememberDot(square,value);
        }
    }
}
