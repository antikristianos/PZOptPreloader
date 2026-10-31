package antikristianos.pzoptpreloader;

import java.util.*;
import me.zed_0xff.zombie_buddy.Exposer;
import me.zed_0xff.zombie_buddy.PatchEngine;
import zombie.ZomboidFileSystem;

/** B42.22 only: immutable session decisions are captured at the completed native mod-loading boundary. */
@Exposer.LuaClass(name="PZOptPeek")
public final class PeekIntegration {
    private static volatile boolean ready, selected, stairs;
    private static volatile String status="waiting for the session mod list";
    private static boolean bridgeLoaded, patchesInstalled;
    private static long epoch, attemptedEpoch=-1;
    private PeekIntegration() { }
    private static boolean supported() {
        return !RuntimeState.server && RuntimeState.payload!=null
            && Compatibility.CURRENT.equals(zombie.GitVersion.REVISION);
    }
    public static synchronized void beforeModSet(Object[] arguments) {
        if(!supported() || arguments.length!=1 || !(arguments[0] instanceof List<?> || arguments[0] instanceof String))return;
        ready=false;selected=false;stairs=false;
        if(bridgeLoaded)antikristianos.pzpeek42.Main.active=false;
    }
    public static synchronized void afterModSet(Object[] arguments) {
        if(!supported() || arguments.length!=1 || !(arguments[0] instanceof List<?> || arguments[0] instanceof String) || arguments[0] instanceof String && ready)return;
        captureFinalMods();
    }
    private static void captureFinalMods() {
        List<String> loaded=ZomboidFileSystem.instance.getModIDs();
        Set<String> ids=zombie.core.Core.optionModsEnabled && loaded!=null?Set.copyOf(loaded):Set.of();
        selected=ids.contains("PeekAView");
        stairs=ids.contains("Staircast") || ids.contains("StaircastRP");
        if(ids.contains("PZPeek") || ids.contains("PZPeek_WIP")) {
            selected=false;status="inactive: standalone PZPeek belongs to B42.21";
        } else status=selected?"PeekAView selected; waiting for completed Java mod loading":"inactive: PeekAView is not selected";
        ready=true;epoch++;
    }
    public static synchronized void afterJavaMods() {
        if(!supported())return;
        // First activation may install this lifecycle hook inside the already running loadMods call.
        // Lua initialization is the next final-list event, not a polling/retry loop.
        if(!ready)captureFinalMods();
        if(!selected || attemptedEpoch==epoch)return;
        attemptedEpoch=epoch;
        try {
            if(!bridgeLoaded) {
                antikristianos.pzpeek42.PeekApiCompatibility.install();
                antikristianos.pzpeek42.Main.main(new String[0]);
                bridgeLoaded=true;
            }
            if(!patchesInstalled) {
                antikristianos.pzpeek42.Main.prepareAfterModsLoaded();
                PatchEngine.applyPatches("antikristianos.pzpeek42",PeekIntegration.class.getClassLoader());
                patchesInstalled=true;
            }
            antikristianos.pzpeek42.Main.initializeAfterModsLoaded();
            antikristianos.pzpeek42.Main.resumeSelectedSession();
            status=antikristianos.pzpeek42.Main.status;
        } catch(Throwable error) {
            if(bridgeLoaded)antikristianos.pzpeek42.Main.active=false;
            status="inactive: "+error;
            System.err.println("[PZOptPreloader/PeekAView] "+status);
        }
    }
    public static boolean sessionReady(){return ready;}
    public static boolean selected(){return ready && selected;}
    public static boolean stairsSelected(){return ready && stairs;}
    public static boolean isActive(){return ready && selected && bridgeLoaded && antikristianos.pzpeek42.Main.active;}
    public static String getStatus(){return status;}
    public static double getVisibility(){return bridgeLoaded?antikristianos.pzpeek42.Main.getVisibility():30;}
    public static void setVisibility(double value){if(bridgeLoaded)antikristianos.pzpeek42.Main.setVisibility(value);}
}
