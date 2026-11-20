package antikristianos.pzoptpreloader;

import java.util.Set;

/** Session selection only; worker calls never scan mods or change optimization preferences. */
public final class ViewpointAnimationCompatibility {
    private static volatile boolean ready,selected;
    private ViewpointAnimationCompatibility(){ }
    public static void beginSelection(){ready=false;selected=false;ViewpointSkeletonCompatibility.beginSelection();ViewpointHotpathCompatibility.beginSelection();ViewpointVehicleCompatibility.selectionReset();ViewpointPresentationCompatibility.selectionReset();ViewpointWeatherCompatibility.reset();}
    public static void captureFinalMods(Set<String> ids){
        selected=!RuntimeState.server&&RuntimeState.payload!=null&&NativeSource.is(RuntimeState.payload)&&ids.contains("Viewpoint");
        ready=true;
        ViewpointHotpathCompatibility.selectionChanged();
        if(selected)System.out.println("[PZOptPreloader/Viewpoint] model updates finish on the main thread; animator workers remain enabled");
    }
    public static boolean sessionReady(){return ready;}
    public static boolean selected(){return ready&&selected;}
    public static boolean serialModelUpdate(boolean hasActiveModel){return hasActiveModel&&ready&&selected;}
    public static boolean serialModelUpdate(zombie.characters.IsoGameCharacter character){return selected()&&(character.hasActiveModel()||ViewpointSkeletonCompatibility.owns(character));}
}
