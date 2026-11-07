package antikristianos.pzpeek42;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.List;
import pzmod.peekaview.PeekAViewMod;
import zombie.ZomboidFileSystem;
import zombie.core.textures.Texture;

/** Small bounded caches; original dependency methods remain miss/failure fallbacks. */
public final class FastPeek {
    private static Map<Texture,Integer> parts;
    public static long modScans,depthHits;
    @SuppressWarnings("unchecked")
    public static void initialize()throws Exception{
        Field field=PeekAViewMod.class.getDeclaredField("jumboPartTextures");field.setAccessible(true);
        parts=(Map<Texture,Integer>)field.get(null);
    }
    public static void invalidateMods(){ /* Detection belongs exclusively to the completed session-mod event. */ }
    public static boolean mods(){
        return antikristianos.pzoptpreloader.PeekIntegration.sessionReady();
    }
    public static boolean own(){return antikristianos.pzoptpreloader.PeekIntegration.selected();}
    public static boolean stairs(){return antikristianos.pzoptpreloader.PeekIntegration.stairsSelected();}
    public static int depthPart(Object draw){
        if(!Main.active)return -1;
        Texture texture=DrawAccess.texture(draw);
        if(texture==null)return 0;
        Integer part=parts.get(texture);
        if(part==null)return -1;
        if(Diagnostics.ENABLED)depthHits++;return part;
    }
    public static float depth(int part,boolean second){
        return part==2?-(second?1f:2f)*.00005f:part==1&&second?.00005f:0f;
    }
}
