package antikristianos.pzpeek42;

import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.asm.Advice;
import pzmod.peekaview.PeekAViewMod;
import zombie.iso.objects.IsoTree;

public final class PatchPerformance {
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderCell",methodName="pzoptTreeNeedsPerFrame")
    public static class EarlyNeeds {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.This zombie.iso.fboRenderChunk.FBORenderCell cell,
                @Patch.Argument(0) IsoTree tree,@Patch.Local("needs") int needs){needs=TreeCache.earlyNeeds(cell,tree);return needs!=0;}
        @Patch.OnExit public static void exit(@Advice.Enter boolean skipped,@Patch.Local("needs") int needs,
                @Patch.Return(readOnly=false) boolean value){if(skipped)value=needs==3;}
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="readTreeCutawayAlpha")
    public static class ReadAlpha {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree,
                @Patch.Local("alpha") float alpha){
            if(!Main.active || tree==null)return false;alpha=TreeDrawing.alpha(tree);return true;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean read,@Patch.Local("alpha") float alpha,
                @Patch.Return(readOnly=false) float value){if(read)value=alpha;}
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="writeTreeCutawayAlpha")
    public static class WriteAlpha {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree,@Patch.Argument(1) float value){
            if(!Main.active || tree==null)return false;TreeDrawing.alpha(tree,value);return true;
        }
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="treeBox")
    public static class Geometry {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) IsoTree tree){return TreeGeometry.prepare(tree);}
        @Patch.OnExit public static void exit(@Advice.Enter boolean prepared,@Patch.Return(readOnly=false) boolean value){if(prepared)value=true;}
    }
    @Patch(className="pzopt.TreeShade",methodName="shade")
    public static class Shade {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) float u,@Patch.Argument(1) float h,
                @Patch.Argument(2) float height,@Patch.Argument(3) float radius,@Patch.Local("shade") long shade){shade=ShadeCache.lookup(u,h,height,radius);return shade!=0;}
        @Patch.OnExit public static void exit(@Advice.Enter boolean hit,@Patch.Argument(0) float u,@Patch.Argument(1) float h,
                @Patch.Argument(2) float height,@Patch.Argument(3) float radius,@Patch.Local("shade") long shade,@Patch.Return(readOnly=false) float value){
            if(hit)value=Float.intBitsToFloat((int)shade);else ShadeCache.remember(u,h,height,radius,value);
        }
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="jumboLayerDepthOffset")
    public static class Depth {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) Object draw,@Patch.Argument(1) boolean second,
                @Patch.Local("offset") float offset){
            int part=FastPeek.depthPart(draw);
            if(part<0)return false;
            offset=FastPeek.depth(part,second);return true;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean cached,@Patch.Local("offset") float offset,
                @Patch.Return(readOnly=false) float value){if(cached)value=offset;}
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="isPeekAViewActive")
    public static class OwnMod {
        @Patch.OnEnter(skipOn=true) public static boolean enter(){return Main.active && FastPeek.mods();}
        @Patch.OnExit public static void exit(@Advice.Enter boolean cached,@Patch.Return(readOnly=false) boolean value){if(cached)value=FastPeek.own();}
    }
    @Patch(className="pzmod.peekaview.PeekAViewMod",methodName="isExternalStairFeatureActive")
    public static class StairMod {
        @Patch.OnEnter(skipOn=true) public static boolean enter(){return Main.active && FastPeek.mods();}
        @Patch.OnExit public static void exit(@Advice.Enter boolean cached,@Patch.Return(readOnly=false) boolean value){if(cached)value=FastPeek.stairs();}
    }
    @Patch(className="zombie.ZomboidFileSystem",methodName="loadMods")
    public static class ModReload {
        @Patch.OnExit public static void exit(){FastPeek.invalidateMods();TreeCache.clear();}
    }

    @Patch(className="pzmod.peekaview.FakeWindow",methodName="writeFakePos")
    public static class FakePosition {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) zombie.characters.IsoGameCharacter character,
                @Patch.Argument(1) float x,@Patch.Argument(2) float y,@Patch.Argument(3) float z){
            return Main.active && WindowAccess.write(character,x,y,z);
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean written,@Patch.Return(readOnly=false) boolean value){if(written)value=true;}
    }
    @Patch(className="pzmod.peekaview.FakeWindow",methodName="writeRealPos")
    public static class RealPosition {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) zombie.characters.IsoGameCharacter character,
                @Patch.Argument(1) float x,@Patch.Argument(2) float y,@Patch.Argument(3) float z){
            return Main.active && WindowAccess.write(character,x,y,z);
        }
    }
    @Patch(className="pzmod.peekaview.FakeWindow",methodName="readCurrentField")
    public static class CurrentSquare {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) zombie.iso.IsoMovingObject object,
                @Patch.Local("square") zombie.iso.IsoGridSquare square){
            if(!Main.active)return false;square=WindowAccess.current(object);return true;
        }
        @Patch.OnExit public static void exit(@Advice.Enter boolean read,@Patch.Local("square") zombie.iso.IsoGridSquare square,
                @Patch.Return(readOnly=false) zombie.iso.IsoGridSquare value){if(read)value=square;}
    }

    @Patch(className="zombie.iso.fboRenderChunk.FBORenderCell",methodName="renderInternal")
    public static class FrameFinished {
        @Patch.OnExit(onThrowable=Throwable.class) public static void exit(){TreeCache.finishFrame();}
    }
    @Patch(className="zombie.iso.IsoWorld",methodName="KillCell")
    public static class WorldUnload {
        @Patch.OnExit public static void exit(){TreeCache.clear();FastPeek.invalidateMods();}
    }
    @Patch(className="zombie.iso.fboRenderChunk.FBORenderTrees",methodName="renderTree")
    public static class HiddenDraw {
        @Patch.OnEnter(skipOn=true) public static boolean enter(@Patch.Argument(0) Object draw){
            boolean hidden=DrawAccess.hidden(draw);
            if(Diagnostics.ENABLED && hidden)TreeOpacity.hiddenDraws++;
            return hidden;
        }
    }
}
