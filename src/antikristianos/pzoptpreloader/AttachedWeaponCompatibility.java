package antikristianos.pzoptpreloader;

import java.util.ArrayList;

/** Invocation-owned outfit scratch; no global entity-worker shutdown or loot-policy replacement. */
public final class AttachedWeaponCompatibility {
    private static volatile boolean installed,failed;
    private AttachedWeaponCompatibility() { }
    public static boolean active(){return installed&&!failed&&ViewpointAnimationCompatibility.selected();}
    public static ArrayList<?> local(ArrayList<?> original){return active()?new ArrayList<>():original;}
    public static synchronized void afterJavaMods(){
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try{
            installed=ViewpointVerifiedBridge.install(AttachedWeaponRepair.PINS,java.util.Set.of(AttachedWeaponRepair.TARGET),AttachedWeaponRepair::patch,()->failed=true);
            if(installed)System.out.println("[PZOptPreloader/Viewpoint] attached-weapon scratch belongs to each outfit invocation; entity workers retained");
        }catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] attached-weapon isolation inactive: "+error);}
    }
}
