package antikristianos.pzoptpreloader;

import java.util.Set;
import java.lang.ref.WeakReference;
import org.joml.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.vehicles.BaseVehicle;

/** Face forward once on entry, then retain ordinary driver-relative look and camera ownership. */
public final class ViewpointVehicleCompatibility {
    private static final long NO_FOLLOW=9205357640488583168L;
    private static final float PI=(float)Math.PI,TAU=2*PI;
    private static final Vector3f direction=new Vector3f();
    private static volatile boolean installed,failed;
    private static volatile long selectionEpoch;
    private static long seenEpoch;
    private static WeakReference<BaseVehicle> previousVehicle;
    private static WeakReference<IsoPlayer> previousPlayer;
    private static WeakReference<BaseVehicle> occupiedVehicle;
    private static WeakReference<IsoPlayer> occupiedPlayer;
    private static float previousHeading;
    private ViewpointVehicleCompatibility() { }
    public static synchronized void afterJavaMods() {
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try{installed=ViewpointVerifiedBridge.install(ViewpointVehicleRepair.RESOURCES,Set.of(ViewpointVehicleRepair.TARGET),ViewpointVehicleRepair::patch,()->failed=true);if(installed)System.out.println("[PZOptPreloader/Viewpoint] vehicle-relative driver look ready in FP/TP");}
        catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Retry at the existing final Java-mod lifecycle boundary. */}
        catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] vehicle look inactive: "+error);}
    }
    private static float release(float yaw){previousVehicle=null;previousPlayer=null;return yaw;}
    public static void selectionReset(){selectionEpoch++;}
    private static boolean observe(IsoPlayer player,BaseVehicle vehicle){
        if((occupiedPlayer==null?null:occupiedPlayer.get())==player&&(occupiedVehicle==null?null:occupiedVehicle.get())==vehicle)return false;
        occupiedPlayer=player==null?null:new WeakReference<>(player);occupiedVehicle=vehicle==null?null:new WeakReference<>(vehicle);
        return vehicle!=null;
    }
    public static void viewMode(boolean perspective){if(!perspective){IsoPlayer p=IsoPlayer.players[0];observe(p,p==null?null:p.getVehicle());release(0);}}
    private static float wrap(float angle){return angle>PI?angle-TAU:angle< -PI?angle+TAU:angle;}
    public static float yaw(float yaw,boolean perspective,Object cameraClaim,boolean freeCamera,long followed,long addonFollowed) {
        long epoch=selectionEpoch;if(epoch!=seenEpoch){release(yaw);seenEpoch=epoch;}
        IsoPlayer player=IsoPlayer.players[0];BaseVehicle vehicle=player==null?null:player.getVehicle();boolean entered=observe(player,vehicle);
        if(!installed||failed||!ViewpointAnimationCompatibility.selected()||!perspective||cameraClaim!=null||freeCamera||followed!=NO_FOLLOW||addonFollowed!=NO_FOLLOW||!Float.isFinite(yaw))return release(yaw);
        if(vehicle==null)return release(yaw);
        vehicle.getForwardVector(direction);float length=direction.x*direction.x+direction.z*direction.z;
        if(!Float.isFinite(length)||length<1e-8f)return release(yaw);
        float heading=(float)Math.atan2(direction.z,direction.x);
        if(!vehicle.isDriver(player))return release(entered?heading:yaw);
        if(previousVehicle==null||previousVehicle.get()!=vehicle||previousPlayer==null||previousPlayer.get()!=player){previousVehicle=new WeakReference<>(vehicle);previousPlayer=new WeakReference<>(player);previousHeading=heading;return entered?heading:yaw;}
        float delta=wrap(heading-previousHeading);previousHeading=heading;
        return wrap(yaw+delta);
    }
}
