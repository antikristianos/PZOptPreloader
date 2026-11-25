package antikristianos.pzoptpreloader;

import java.util.Set;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/** Camera-coordinate reads only; DAMN remains responsible for the actor's sorting height. */
public final class ViewpointSeatedHeightCompatibility {
    private static volatile boolean installed,failed;
    private ViewpointSeatedHeightCompatibility(){ }
    public static synchronized void afterJavaMods(){
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try{
            installed=ViewpointVerifiedBridge.install(ViewpointSeatedHeightRepair.RESOURCES,
                Set.of(ViewpointSeatedHeightRepair.TARGET,ViewpointSeatedHeightRepair.EYE),ViewpointSeatedHeightRepair::patch,()->failed=true);
            if(installed)System.out.println("[PZOptPreloader/Viewpoint] seated scene/eye excludes active DAMN sorting height");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Final Java-mod boundary retries. */}
        catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] seated height compatibility inactive: "+error);}
    }
    public static float z(IsoGameCharacter character){
        float actual=character.getZ();
        if(!installed||failed||!ViewpointAnimationCompatibility.selected()
                ||!(character instanceof IsoPlayer player)||player.getVehicle()==null||LuaManager.env==null)return actual;
        Object value=LuaManager.env.rawget("DAMN");if(!(value instanceof KahluaTable damn))return actual;
        value=damn.rawget("DepthSorting");if(!(value instanceof KahluaTable sorting))return actual;
        value=sorting.rawget("AppliedFloorZ");if(!(value instanceof KahluaTable floors))return actual;
        value=floors.rawget(player);if(!(value instanceof Number floor))return actual;
        float logical=floor.floatValue();
        return Float.isFinite(actual)&&Float.isFinite(logical)
            &&logical==(float)Math.floor(player.getVehicle().getZ())?logical:actual;
    }
}
