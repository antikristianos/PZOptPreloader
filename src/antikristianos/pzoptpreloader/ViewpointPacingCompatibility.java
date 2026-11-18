package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;

/** Scoped to the selected, reviewed VP fixed ticker. Native ISO, MP and user settings stay owned. */
public final class ViewpointPacingCompatibility {
    private static volatile boolean installed,broken;
    private static MethodHandle dt,perState,start,onFrame,done;
    private ViewpointPacingCompatibility(){ }
    public static boolean active(){return installed&&!broken&&ViewpointAnimationCompatibility.selected();}
    public static long begin(){
        if(!active())return 0;
        try{long interval=(long)dt.invokeExact();int ticks=zombie.GameTime.isGamePaused()?1:(int)perState.invokeExact();if(interval<=0||ticks<1)throw new IllegalStateException("Invalid Viewpoint clock interval");long now=System.nanoTime();start.invokeExact(now,Math.multiplyExact(interval,(long)ticks));return now;}
        catch(Throwable error){broken=true;System.err.println("[PZOptPreloader/Viewpoint] fixed-step budget bridge inactive: "+error);return 0;}
    }
    public static void end(long began){if(began==0)return;try{onFrame.invokeExact(began);done.invokeExact(began);}catch(Throwable error){broken=true;System.err.println("[PZOptPreloader/Viewpoint] fixed-step completion inactive: "+error);}}
    private static Object value(Class<?> owner,String name)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static byte[] capture(Instrumentation inst,Class<?> target)throws Exception{
        byte[][] bytes={null};ClassFileTransformer observer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t==target)bytes[0]=b.clone();return null;}};inst.addTransformer(observer,true);try{inst.retransformClasses(target);}finally{inst.removeTransformer(observer);}if(bytes[0]==null)throw new IOException("Missing live fixed-step bytes");return bytes[0];
    }
    public static synchronized void afterJavaMods(){
        if(installed||broken||!ViewpointAnimationCompatibility.selected())return;
        try{
            ClassLoader loader=ViewpointPacingCompatibility.class.getClassLoader();byte[] resource;try(InputStream in=loader.getResourceAsStream(ViewpointPacingRepair.TARGET+".class")){if(in==null)return;resource=in.readAllBytes();}if(!ViewpointPacingRepair.RESOURCE.equals(Compatibility.hash(resource)))throw new IOException("Unreviewed Viewpoint fixed ticker resource");ViewpointPacingRepair.verify(resource);
            Class<?> target=Class.forName(ViewpointPacingRepair.TARGET.replace('/','.'),false,loader);Object clock=value(target,"CLOCK"),count=value(target,"PER_STATE");MethodHandles.Lookup lookup=MethodHandles.publicLookup();dt=lookup.findVirtual(clock.getClass(),"dt",MethodType.methodType(long.class)).bindTo(clock);perState=lookup.findVirtual(count.getClass(),"getInt",MethodType.methodType(int.class)).bindTo(count);
            Class<?> pacing=Class.forName("pzopt.Pacing",false,loader),cap=Class.forName("pzopt.FrameCap",false,loader);start=lookup.findStatic(pacing,"stepStart",MethodType.methodType(void.class,long.class,long.class));onFrame=lookup.findStatic(cap,"onFrame",MethodType.methodType(void.class,long.class));done=lookup.findStatic(cap,"stepDone",MethodType.methodType(void.class,long.class));
            Instrumentation inst=(Instrumentation)value(Class.forName("me.zed_0xff.zombie_buddy.Loader"),"g_instrumentation");if(inst==null||!inst.isModifiableClass(target))throw new IOException("Fixed ticker instrumentation unavailable");byte[] original=capture(inst,target);ViewpointPacingRepair.verify(original);var expected=Compatibility.methods(ViewpointPacingRepair.patch(original));
            for(Class<?> nativeType:new Class<?>[]{pacing,cap}){var base=Compatibility.methods(RuntimeState.payload.baseClassBytes(nativeType.getName().replace('.','/')));var live=Compatibility.methods(capture(inst,nativeType));String[] methods=nativeType==pacing?new String[]{"stepStart(JJ)V","stepStart(JJJ)V","heavyAddHold()Z"}:new String[]{"onFrame(J)V","stepDone(J)V"};for(String method:methods)if(base.get(method)==null||!base.get(method).equals(live.get(method)))throw new IOException("Unreviewed native frame lifecycle: "+nativeType.getName()+"."+method);}
            ClassFileTransformer transformer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t!=target)return null;try{return ViewpointPacingRepair.patch(b);}catch(Throwable error){broken=true;System.err.println("[PZOptPreloader/Viewpoint] fixed-step transform refused: "+error);return null;}}};inst.addTransformer(transformer,true);try{if(!expected.equals(Compatibility.methods(capture(inst,target)))||broken)throw new IOException("Effective fixed-step transform differs");}catch(Throwable error){inst.removeTransformer(transformer);throw error;}
            installed=true;System.out.println("[PZOptPreloader/Viewpoint] fixed steps advance native frame budgets; newly loaded zombie models retain admission progress");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Existing next lifecycle boundary retries optional Java availability. */}
        catch(Throwable error){broken=true;System.err.println("[PZOptPreloader/Viewpoint] fixed-step budget compatibility inactive: "+error);}
    }
}
