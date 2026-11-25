package antikristianos.pzoptpreloader;

import java.util.Set;
import java.lang.invoke.*;
import org.lwjgl.opengl.GL11;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.VehicleModelInstance;

/** Draw the inside of opaque own-vehicle panels without altering glass or part masks. */
public final class ViewpointVehicleInteriorCompatibility {
    private static volatile boolean installed,failed;
    private static MethodHandle restoreClear,setVector;
    private static final ThreadLocal<Boolean> genericOpaque=ThreadLocal.withInitial(()->false);
    private ViewpointVehicleInteriorCompatibility() { }
    public static synchronized void afterJavaMods() {
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try {
            Class<?> program=Class.forName("viewpoint.platform.GlProgram"),clear=Class.forName("viewpoint.render.CameraClear");
            var apply=clear.getDeclaredMethod("apply",program);apply.setAccessible(true);
            restoreClear=MethodHandles.lookup().unreflect(apply).asType(MethodType.methodType(void.class,Object.class));
            setVector=MethodHandles.lookup().unreflect(program.getMethod("set",String.class,float.class,float.class,float.class,float.class))
                .asType(MethodType.methodType(void.class,Object.class,String.class,float.class,float.class,float.class,float.class));
            installed=ViewpointVerifiedBridge.install(ViewpointVehicleInteriorRepair.RESOURCES,
                Set.of(ViewpointVehicleInteriorRepair.TARGET,ViewpointVehicleInteriorRepair.MODELS,ViewpointVehicleInteriorRepair.BATCHES,"viewpoint/render/CameraClear"),ViewpointVehicleInteriorRepair::patch,()->failed=true);
            if(installed)System.out.println("[PZOptPreloader/Viewpoint] own FP opaque vehicle interiors are two-sided");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Retry at the final Java-mod boundary. */}
        catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] vehicle interior bridge inactive: "+error);}
    }
    public static void genericBegin(){genericOpaque.set(true);}
    public static void genericEnd(){genericOpaque.set(false);}
    private static boolean vehiclePart(Object key){
        return key instanceof ModelInstance model&&model.getOwner() instanceof ModelManager.ModelSlot slot&&slot.model instanceof VehicleModelInstance;
    }
    public static boolean visible(boolean original,int flags,Object key,boolean enabled,boolean thirdPerson){
        if(original)return true;
        return installed&&!failed&&ViewpointAnimationCompatibility.selected()&&enabled&&!thirdPerson&&genericOpaque.get()
            &&(flags&65)==65&&vehiclePart(key);
    }
    public static void generic(Object program,int material,int flags,Object key,boolean enabled,boolean thirdPerson){
        if(material<0&&genericOpaque.get())surface(program,program,vehiclePart(key)?flags:flags&~64,enabled,thirdPerson);
    }
    public static boolean group(boolean original,int first,int second,Object firstKey,Object secondKey,int material,boolean enabled,boolean thirdPerson){
        return original&&(!installed||failed||!ViewpointAnimationCompatibility.selected()||!enabled||thirdPerson||!genericOpaque.get()
            ||((first&64)!=0&&(material>=0||vehiclePart(firstKey)))==((second&64)!=0&&(material>=0||vehiclePart(secondKey))));
    }
    public static void surface(Object program,Object opaqueProgram,int flags,boolean enabled,boolean thirdPerson) {
        if(!installed||failed||!ViewpointAnimationCompatibility.selected()||program!=opaqueProgram)return;
        try {
            // Restore before every opaque material so the own-car exception cannot reach another car.
            restoreClear.invokeExact(program);
            if(enabled&&!thirdPerson&&(flags&64)!=0){
                setVector.invokeExact(program,"uClearEye",0f,0f,0f,0f);
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
        }catch(Throwable error){try{restoreClear.invokeExact(program);}catch(Throwable ignored){ }failed=true;System.err.println("[PZOptPreloader/Viewpoint] vehicle interior drawing inactive: "+error);}
    }
}
