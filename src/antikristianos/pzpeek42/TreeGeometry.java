package antikristianos.pzpeek42;

import java.util.WeakHashMap;
import pzmod.peekaview.PeekAViewMod;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.iso.objects.IsoTree;
import zombie.iso.sprite.IsoSprite;

/** Geometry follows the actual current textures, including snow, animation and late loading. */
public final class TreeGeometry {
    private static final ThreadLocal<WeakHashMap<Texture,Bounds>> BOUNDS=ThreadLocal.withInitial(WeakHashMap::new);
    public static long builds,hits;
    private static final class Bounds {
        float ox,oy; int w,h,ow,oh,scale;
        float x0,x1,y0,y1;
    }
    private static Bounds bounds(Texture texture){
        float ox=texture.getOffsetX(),oy=texture.getOffsetY();
        int w=texture.getWidth(),h=texture.getHeight(),ow=texture.getWidthOrig(),oh=texture.getHeightOrig(),scale=2;
        WeakHashMap<Texture,Bounds> cache=BOUNDS.get();Bounds value=cache.get(texture);
        // Never freeze a missing or unfinished texture into a prepared cache entry.
        boolean ready=texture.isReady();
        if(value!=null && ready && value.ox==ox && value.oy==oy && value.w==w && value.h==h
                && value.ow==ow && value.oh==oh && value.scale==scale){if(Diagnostics.ENABLED)hits++;return value;}
        if(value==null)value=new Bounds();
        value.ox=ox;value.oy=oy;value.w=w;value.h=h;value.ow=ow;value.oh=oh;value.scale=scale;
        value.x0=ox-(float)ow*.5f;value.x1=value.x0+(float)w;
        value.y0=oy-((float)oh-32f*(float)scale);value.y1=value.y0+(float)h;
        if(ready)cache.put(texture,value);else cache.remove(texture);
        if(Diagnostics.ENABLED)builds++;return value;
    }
    public static boolean prepare(IsoTree tree){
        if(!Main.active || tree.square==null || tree.sprite==null)return false;
        Texture base=tree.sprite.getTextureForCurrentFrame(tree.getDir(),tree);
        if(base==null)return false;
        Bounds a=bounds(base);
        float x0=a.x0,x1=a.x1,y0=a.y0,y1=a.y1;
        if(tree.attachedAnimSprite!=null && !tree.attachedAnimSprite.isEmpty()){
            IsoSprite sprite=tree.attachedAnimSprite.get(0).parentSprite;
            Texture foliage=sprite==null?null:sprite.getTextureForCurrentFrame(tree.getDir(),tree);
            if(foliage!=null){
                Bounds b=bounds(foliage);
                x0=Math.min(x0,b.x0);x1=Math.max(x1,b.x1);y0=Math.min(y0,b.y0);y1=Math.max(y1,b.y1);
            }
        }
        PeekAViewMod.treeBoxX0=x0;PeekAViewMod.treeBoxX1=x1;
        PeekAViewMod.treeBoxY0=y0;PeekAViewMod.treeBoxY1=y1;
        RenderTargets.geometry(tree);
        return true;
    }
}
