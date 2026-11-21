package antikristianos.pzoptpreloader;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.*;
import fmod.fmod.FMODSoundEmitter;

/** Preserve requested measurements and pending work; avoid unused ISO diagnostics and repeat restores. */
public final class ViewpointPresentationCompatibility {
    private static volatile boolean installed,failed,profilingFallback;
    private static MethodHandle overlayMode;
    private static final class Timing { final byte[] pairs=new byte[13]; int luaDepth; boolean luaWanted; }
    private static final ThreadLocal<Timing> timing=ThreadLocal.withInitial(Timing::new);
    private static final class Sound { boolean restoring; long epoch; }
    private static final ThreadLocal<Sound> sound=ThreadLocal.withInitial(Sound::new);
    private static final Map<FMODSoundEmitter,Long> restored=new WeakHashMap<>();
    private static boolean lifted;
    private static long soundEpoch;
    private ViewpointPresentationCompatibility() { }
    public static synchronized void afterJavaMods() {
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try {
            // Resolve only after the matching original resources have been verified by the installer.
            installed=ViewpointVerifiedBridge.install(ViewpointPresentationRepair.RESOURCES,ViewpointPresentationRepair.TARGETS,ViewpointPresentationRepair::patch,()->failed=true);
            if(installed){Class<?> overlay=Class.forName("viewpoint.platform.PerformanceOverlay");Field field=overlay.getDeclaredField("MODE");field.setAccessible(true);Object choice=field.get(null);overlayMode=MethodHandles.publicLookup().unreflect(choice.getClass().getMethod("get")).bindTo(choice);System.out.println("[PZOptPreloader/Viewpoint] consumer-gated ISO timings, pending frame presentation and bounded audio restore ready");}
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Existing final Java-mod lifecycle retries optional classes. */}
        catch(Exception|LinkageError error){failed=true;installed=false;System.err.println("[PZOptPreloader/Viewpoint] presentation bridge inactive: "+error);}
    }
    public static boolean active(){return installed&&!failed&&ViewpointAnimationCompatibility.selected();}
    public static synchronized void selectionReset(){soundEpoch++;lifted=false;restored.clear();}
    private static boolean wanted(boolean perspective,boolean recording) {
        if(perspective||recording||!active()||overlayMode==null||profilingFallback)return true;
        try{return (int)overlayMode.invokeExact()!=3;}
        catch(Throwable error){profilingFallback=true;System.err.println("[PZOptPreloader/Viewpoint] keeping original profiling after unavailable overlay state: "+error);return true;}
    }
    public static boolean skipStart(int phase,boolean perspective,boolean recording) {
        if(phase<0||phase>=13)return false;boolean want=wanted(perspective,recording);timing.get().pairs[phase]=(byte)(want?2:1);return !want;
    }
    public static boolean skipEnd(int phase) {
        if(phase<0||phase>=13)return false;Timing state=timing.get();byte pair=state.pairs[phase];state.pairs[phase]=0;return pair==1;
    }
    public static boolean skipLuaBegin(boolean perspective,boolean recording) {
        Timing state=timing.get();if(state.luaDepth==0)state.luaWanted=wanted(perspective,recording);state.luaDepth++;return !state.luaWanted;
    }
    public static boolean skipLuaEnd() {
        Timing state=timing.get();if(state.luaDepth==0)return false;boolean skip=!state.luaWanted;state.luaDepth--;return skip;
    }
    public static boolean skipLuaCall(){Timing state=timing.get();return state.luaDepth>0&&!state.luaWanted;}
    public static boolean skipLuaFrame(boolean asked,boolean perspective,boolean recording){return !asked&&!wanted(perspective,recording);}
    public static boolean present(boolean drawn,boolean copied){return !active()||drawn&&copied;}
    public static synchronized boolean soundStart(Object lift,FMODSoundEmitter emitter,boolean raised,boolean server) {
        Sound state=sound.get();state.restoring=false;if(!active()||server)return true;
        if(lift!=null){if(!lifted){soundEpoch++;lifted=true;}return true;}
        lifted=false;
        if(!raised||emitter==null||emitter.x==0&&emitter.y==0)return true;
        Long epoch=restored.get(emitter);if(epoch!=null&&epoch==soundEpoch)return false;
        state.restoring=true;state.epoch=soundEpoch;return true;
    }
    public static synchronized void soundEnd(FMODSoundEmitter emitter) {
        Sound state=sound.get();if(!state.restoring)return;state.restoring=false;restored.put(emitter,state.epoch);
    }
}
