package antikristianos.pzoptpreloader;

import java.io.InputStream;
import java.lang.invoke.*;
import java.lang.reflect.Modifier;

/** Keep the native ISO presentation while retaining perspective-only redundant-job guards. */
public final class ViewpointNativePresentationCompatibility {
    private static volatile MethodHandle viewEnabled;
    private static volatile boolean failed;
    private ViewpointNativePresentationCompatibility(){ }
    public static void selectionReset(){viewEnabled=null;}
    public static synchronized void afterJavaMods(){
        if(viewEnabled!=null||failed||!ViewpointAnimationCompatibility.selected()
            ||!NativeSource.NEXT_COMMIT.equals(NativeSource.sourceCommit(RuntimeState.payload)))return;
        try{
            String name="viewpoint/core/View";
            try(InputStream in=ViewpointNativePresentationCompatibility.class.getClassLoader().getResourceAsStream(name+".class")){
                if(in==null)return;
                if(!ViewpointPresentationRepair.RESOURCES.get(name).equals(Compatibility.hash(in.readAllBytes())))throw new IllegalStateException("Unreviewed Viewpoint mode resource");
            }
            var field=Class.forName("viewpoint.core.View").getDeclaredField("enabled");
            if(field.getType()!=boolean.class||!Modifier.isStatic(field.getModifiers()))throw new IllegalStateException("Unsupported Viewpoint mode field");
            field.setAccessible(true);viewEnabled=MethodHandles.lookup().unreflectGetter(field).asType(MethodType.methodType(boolean.class));
            System.out.println("[PZOptPreloader/Viewpoint] native ISO presentation retained; perspective job guards remain active");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Existing Java-mod lifecycle supplies the next attempt. */}
        catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] native presentation mode bridge inactive: "+error);}
    }
    private static boolean isometric(){
        MethodHandle mode=viewEnabled;
        if(failed||mode==null||!ViewpointAnimationCompatibility.selected())return false;
        if(!(zombie.GameWindow.states.current instanceof zombie.gameStates.IngameState))return false;
        try{return !(boolean)mode.invokeExact();}
        catch(Throwable error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] native presentation mode unavailable: "+error);return false;}
    }
    public static boolean worldJobs(boolean nativeDecision){return nativeDecision||isometric();}
    public static boolean publishDarkness(boolean desired,boolean queued){return desired!=queued||isometric();}
}
