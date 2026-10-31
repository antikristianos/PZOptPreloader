package antikristianos.pzpeek42;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import pzmod.peekaview.PeekAViewMod;
import zombie.core.PerformanceSettings;
import zombie.core.textures.Texture;
import zombie.iso.IsoCamera;
import zombie.iso.IsoTreeJumbo;
import zombie.iso.fboRenderChunk.FBORenderCell;
import zombie.iso.objects.IsoTree;
import zombie.iso.sprite.IsoSpriteManager;

/** Called after native animation updates, before invisible material lookup/preparation. */
public final class TreeDrawing {
    private static final VarHandle CUTAWAY;
    public static long crownTextureSkips,ordinarySkips;
    static{
        try{CUTAWAY=MethodHandles.privateLookupIn(IsoTree.class,MethodHandles.lookup()).findVarHandle(IsoTree.class,"cutawayAlpha",float.class);}
        catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    public static float alpha(IsoTree tree){return (float)CUTAWAY.get(tree);}
    public static void alpha(IsoTree tree,float value){CUTAWAY.set(tree,value);}
    public static Texture crownTexture(IsoSpriteManager manager,IsoTreeJumbo.TreeDescription description,IsoTree tree){
        if(Main.active && alpha(tree)==0){if(Diagnostics.ENABLED)crownTextureSkips++;return null;}
        return manager.getSprite(description.treetop()).getTextureForCurrentFrame(tree.getDir(),tree);
    }
    public static boolean hiddenOrdinary(IsoTree tree){
        if(!Main.active || !PeekAViewMod.fadeNWTrees || !PeekAViewMod.isActiveTreeFadeForCurrentRenderPlayer()
                || tree.fadeAlpha>=1 || Main.opacity(tree.fadeAlpha)!=0 || PeekAViewMod.fellsCrown(tree))return false;
        boolean stencil=(tree.renderFlag || tree.fadeAlpha<tree.getTargetAlpha(IsoCamera.frameState.playerIndex))
            && (!PerformanceSettings.fboRenderChunk || FBORenderCell.instance.renderTranslucentOnly);
        boolean hidden=stencil || (float)CUTAWAY.get(tree)!=1;
        if(Diagnostics.ENABLED && hidden)ordinarySkips++;
        return hidden;
    }
}
