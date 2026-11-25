package antikristianos.pzoptpreloader;

import java.util.Set;
import java.lang.ref.WeakReference;
import java.lang.invoke.*;
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
    private static MethodHandle readPitch,writeYaw,hold,enabled,free,centre,followedOwner,addonOwner;
    private ViewpointVehicleCompatibility() { }
    public static synchronized void afterJavaMods() {
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try{
            Class<?> look=Class.forName("viewpoint.input.Look"),view=Class.forName("viewpoint.core.View"),camera=Class.forName("viewpoint.input.FreeCam");
            var lookup=MethodHandles.lookup();
            writeYaw=lookup.unreflectSetter(look.getField("yaw"));readPitch=lookup.unreflectGetter(look.getField("pitch"));
            hold=lookup.unreflect(look.getMethod("hold",float.class,float.class));enabled=lookup.unreflectGetter(view.getField("enabled"));free=lookup.unreflectGetter(camera.getField("active"));
            centre=lookup.unreflect(camera.getMethod("centre")).asType(MethodType.methodType(Object.class));
            var primary=look.getDeclaredField("followed");primary.setAccessible(true);followedOwner=lookup.unreflectGetter(primary);
            var addon=look.getDeclaredField("addonFollowed");addon.setAccessible(true);addonOwner=lookup.unreflectGetter(addon);
            installed=ViewpointVerifiedBridge.install(ViewpointVehicleRepair.RESOURCES,Set.of(ViewpointVehicleRepair.TARGET),ViewpointVehicleRepair::patch,()->failed=true);
            if(installed)System.out.println("[PZOptPreloader/Viewpoint] vehicle-relative driver look and native entry ready in FP/TP");
        }
        catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Retry at the existing final Java-mod lifecycle boundary. */}
        catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] vehicle look inactive: "+error);}
    }
    private static float release(float yaw){previousVehicle=null;previousPlayer=null;return yaw;}
    public static void selectionReset(){selectionEpoch++;}
    /** Observe the real setter, then queue the original one-shot look API for its next input read. */
    public static void vehicleSet(zombie.characters.IsoGameCharacter actor,BaseVehicle vehicle){
        if(RuntimeState.server||!installed||failed||!ViewpointAnimationCompatibility.selected()||actor!=IsoPlayer.players[0])return;
        try{
            if(vehicle==null){observe((IsoPlayer)actor,null);release(0);return;}
            if(!(boolean)enabled.invokeExact()||centre.invokeExact()!=null||(boolean)free.invokeExact()
                    ||(long)followedOwner.invokeExact()!=NO_FOLLOW||(long)addonOwner.invokeExact()!=NO_FOLLOW)return;
            vehicle.getForwardVector(direction);float length=direction.x*direction.x+direction.z*direction.z;
            if(!Float.isFinite(length)||length<1e-8f)return;
            float heading=(float)Math.atan2(direction.z,direction.x),pitch=(float)readPitch.invokeExact();
            if(!Float.isFinite(pitch))return;
            observe((IsoPlayer)actor,vehicle);release(0);
            if(vehicle.isDriver((IsoPlayer)actor)){previousPlayer=new WeakReference<>((IsoPlayer)actor);previousVehicle=new WeakReference<>(vehicle);previousHeading=heading;}
            writeYaw.invokeExact(heading);hold.invokeExact(heading,pitch);
        }catch(Throwable error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] vehicle entry look inactive: "+error);}
    }
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
