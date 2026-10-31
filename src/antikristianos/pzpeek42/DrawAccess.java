package antikristianos.pzpeek42;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import zombie.iso.fboRenderChunk.FBORenderTrees;
import zombie.core.textures.Texture;

/** Prepared typed access to native draw records; no reflective field operations per draw. */
public final class DrawAccess {
    private static VarHandle handle(Class<?> type,String name,Class<?> value)throws Exception {
        return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).findVarHandle(type,name,value);
    }
    private static final Class<?> RECORD;
    private static final VarHandle TREES, FADE, CUTAWAY, TRANSPARENT, STENCIL, TEXTURE, PROBE;
    static {
        try {
            RECORD=Class.forName("zombie.iso.fboRenderChunk.FBORenderTrees$Tree");
            TREES=handle(FBORenderTrees.class,"trees",java.util.ArrayList.class);
            FADE=handle(RECORD,"fadeAlpha",float.class);
            CUTAWAY=handle(RECORD,"cutawayAlpha",float.class);
            TRANSPARENT=handle(RECORD,"transparent",boolean.class);
            STENCIL=handle(RECORD,"useStencil",boolean.class);
            TEXTURE=handle(RECORD,"texture",Texture.class);
            PROBE=handle(RECORD,"pzoptProbe",boolean.class);
        } catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    public static void initialize(){ }
    public static List<?> records(FBORenderTrees batch){return (List<?>)TREES.get(batch);}
    public static boolean stencil(Object draw){return (boolean)STENCIL.get(draw);}
    public static boolean transparent(Object draw){return (boolean)TRANSPARENT.get(draw);}
    public static Texture texture(Object draw){return (Texture)TEXTURE.get(draw);}
    public static boolean hidden(Object draw){
        return Main.active && !(boolean)PROBE.get(draw) && transparent(draw)
            && Math.min((float)FADE.get(draw),(float)CUTAWAY.get(draw))==0;
    }
    public static void opacity(Object draw,float value){
        FADE.set(draw,value);CUTAWAY.set(draw,value);TRANSPARENT.set(draw,true);STENCIL.set(draw,false);
    }
}
