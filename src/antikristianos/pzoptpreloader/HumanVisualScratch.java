package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.*;

/** Separate the native shared clothing-location scratch list by updater thread. */
public final class HumanVisualScratch {
    private static final ThreadLocal<List<?>> LOCAL=ThreadLocal.withInitial(ArrayList::new);
    private HumanVisualScratch(){ }
    public static List<?> redirect(List<?> incoming){
        if(!Compatibility.developmentRuntime()||!NativeState.valid||incoming!=NativeState.shared)return incoming;
        return LOCAL.get();
    }
    private static final class NativeState {
        static final List<?> shared;static final boolean valid;
        static {List<?> list=null;boolean matches=false;try(ZipFile game=new ZipFile(Compatibility.gameJar().toFile())){
            var entry=game.getEntry("zombie/core/skinnedmodel/visual/HumanVisual.class");
            if(entry!=null)try(InputStream in=game.getInputStream(entry)){matches=Compatibility.hash(in.readAllBytes()).equals("59f86fd13cf79a7c12c456a6dee26d5afa5649f551a3d848a2ef8bb13e28e04f");}
            if(matches){Class<?> type=Class.forName("zombie.core.skinnedmodel.visual.HumanVisual");Field field=type.getDeclaredField("itemVisualLocations");field.setAccessible(true);list=(List<?>)field.get(null);}
        }catch(ReflectiveOperationException|IOException error){System.err.println("[PZOptPreloader] Native outfit scratch boundary unavailable: "+error);matches=false;}
        shared=list;valid=matches&&list!=null;}
    }
}
