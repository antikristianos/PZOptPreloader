package antikristianos.pzoptpreloader;

/** A paused world keeps its precipitation phase; camera/UI frame clocks remain original. */
public final class ViewpointWeatherCompatibility {
    private static volatile boolean installed,failed;
    private static boolean seen,wasPaused;
    private static float previousRaw;
    private static double runningTime;
    private static boolean heldSnapshot,windPaused;
    private static long pausedStamp;
    private ViewpointWeatherCompatibility(){ }
    public static boolean active(){return installed&&!failed&&ViewpointAnimationCompatibility.selected();}
    public static synchronized void reset(){seen=false;wasPaused=false;previousRaw=0;runningTime=0;heldSnapshot=false;windPaused=false;pausedStamp=0;}
    public static synchronized float time(float raw){
        if(!active())return raw;
        if(!Float.isFinite(raw)){reset();return raw;}
        boolean paused=zombie.GameTime.isGamePaused();
        if(!seen){seen=true;runningTime=raw;previousRaw=raw;}
        else if(!wasPaused)runningTime+=Math.max(0,(double)raw-previousRaw);
        previousRaw=Math.max(previousRaw,raw);wasPaused=paused;return (float)runningTime;
    }
    /** Observe pause on every rendered frame, even when the producer retains its last scene. */
    public static float frameTime(float original,float sourceTime,long sourceStamp,long now){
        if(!active())return original;
        float raw=sourceTime+(sourceStamp==0?0:(float)(now-sourceStamp)*1.0e-9f);
        return time(raw);
    }
    public static synchronized float carryElapsed(float seconds,long stamp){
        if(!active())return seconds;
        if(zombie.GameTime.isGamePaused()){heldSnapshot=true;pausedStamp=stamp;windPaused=true;return 0;}
        if(heldSnapshot&&stamp==pausedStamp)return 0;
        heldSnapshot=false;return seconds;
    }
    public static synchronized float elapsed(float seconds){
        if(!active())return seconds;
        if(zombie.GameTime.isGamePaused()){windPaused=true;return 0;}
        if(windPaused){windPaused=false;return 0;}
        return seconds;
    }
    public static synchronized void afterJavaMods(){
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try{
            installed=ViewpointVerifiedBridge.install(ViewpointWeatherRepair.PINS,ViewpointWeatherRepair.PINS.keySet(),ViewpointWeatherRepair::patch,()->failed=true);
            if(installed)System.out.println("[PZOptPreloader/Viewpoint] precipitation phase and carried wind respect native game pause; sky colours unchanged");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Existing final Java-mod boundary retries optional classes. */}
        catch(Exception|LinkageError error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] precipitation pause bridge inactive: "+error);}
    }
}
