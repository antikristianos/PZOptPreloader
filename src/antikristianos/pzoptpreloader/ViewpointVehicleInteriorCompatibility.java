package antikristianos.pzoptpreloader;

import java.util.Set;
import java.lang.invoke.*;
import org.lwjgl.opengl.GL11;

/** Draw the inside of opaque own-vehicle panels without altering glass or part masks. */
public final class ViewpointVehicleInteriorCompatibility {
    private static volatile boolean installed,failed;
    private static MethodHandle restoreClear,setVector;
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
                Set.of(ViewpointVehicleInteriorRepair.TARGET,"viewpoint/render/CameraClear"),ViewpointVehicleInteriorRepair::patch,()->failed=true);
            if(installed)System.out.println("[PZOptPreloader/Viewpoint] own FP opaque vehicle interiors are two-sided");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Retry at the final Java-mod boundary. */}
        catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] vehicle interior bridge inactive: "+error);}
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
