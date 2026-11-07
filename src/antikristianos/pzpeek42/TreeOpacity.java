package antikristianos.pzpeek42;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.jar.asm.Type;
import pzmod.peekaview.PeekAViewMod;
import pzmod.peekaview.Patch_IsoTree;
import zombie.iso.fboRenderChunk.FBORenderTrees;
import zombie.iso.objects.IsoTree;

/** Applies the final ordinary alpha after PeekAView, with prepared typed record access. */
public final class TreeOpacity {
    public static long adjusted,hiddenSubmissions,hiddenDraws;
    public static void initialize()throws ReflectiveOperationException{
        DrawAccess.initialize();
        var adds=Arrays.stream(FBORenderTrees.class.getDeclaredMethods()).filter(method->method.getName().equals("addTree")).toList();
        if(adds.size()!=1 || Modifier.isStatic(adds.get(0).getModifiers()) || !Type.getMethodDescriptor(adds.get(0)).equals(
                "(Lzombie/core/textures/Texture;Lzombie/core/textures/Texture;FFFFFFFLzombie/iso/objects/ObjectRenderEffects;ZFZF)V"))
            throw new NoSuchMethodException("tree draw-record addTree signature changed");
        var rendering=Patch_IsoTree.class.getField("rendering");
        if(!Modifier.isStatic(rendering.getModifiers()) || rendering.getType()!=IsoTree.class)
            throw new NoSuchFieldException("PeekAView current rendered tree field changed");
    }
    public static boolean hiddenSubmission(boolean stencil,boolean transparent,float cutaway){
        if(!Main.active)return false;
        IsoTree tree=Patch_IsoTree.rendering;
        if(tree==null || !PeekAViewMod.fadeNWTrees || !PeekAViewMod.isActiveTreeFadeForCurrentRenderPlayer())return false;
        boolean hidden=PeekAViewMod.fellsCrown(tree)?transparent && cutaway==0
            :tree.fadeAlpha<1 && (stencil||transparent) && Main.opacity(tree.fadeAlpha)==0;
        if(Diagnostics.ENABLED && hidden)hiddenSubmissions++;
        return hidden;
    }
    public static void after(FBORenderTrees batch){
        if(!Main.active)return;
        IsoTree tree=Patch_IsoTree.rendering;
        if(tree==null || !PeekAViewMod.fadeNWTrees || tree.fadeAlpha>=1
                || !PeekAViewMod.isActiveTreeFadeForCurrentRenderPlayer() || PeekAViewMod.fellsCrown(tree))return;
        List<?> list=DrawAccess.records(batch);
        if(list.isEmpty())return;
        Object draw=list.get(list.size()-1);
        if(!DrawAccess.transparent(draw) && !DrawAccess.stencil(draw))return;
        DrawAccess.opacity(draw,Main.opacity(tree.fadeAlpha));if(Diagnostics.ENABLED)adjusted++;
    }
}
