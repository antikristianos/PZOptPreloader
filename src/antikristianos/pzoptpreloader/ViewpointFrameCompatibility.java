package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Late, fail-closed compatibility for the original render replay and exact once-only commands. */
public final class ViewpointFrameCompatibility {
    private static volatile boolean active,installed,failed;
    private static boolean attempted;
    private static volatile String status="waiting for final Java mod selection";
    private static volatile Map<String,Class<?>> types=Map.of();
    private static final ThreadLocal<Integer> REPLAY=ThreadLocal.withInitial(()->0);
    private static volatile boolean overlayFailed;
    private static final class OverlaySink {
        static final MethodHandle SWAP=resolve();
        static MethodHandle resolve(){try{return MethodHandles.publicLookup().unreflect(Class.forName("pzopt.Overlay").getMethod("onSwap"));}catch(ReflectiveOperationException|LinkageError unavailable){return null;}}
    }
    private ViewpointFrameCompatibility(){ }
    public static boolean active(){return active&&ViewpointAnimationCompatibility.selected();}
    public static String status(){return status;}
    public static boolean admitted(Object drawer){return active()&&drawer!=null&&(drawer.getClass()==types.get(ViewpointFrameRepair.DARKNESS)||drawer.getClass()==types.get(ViewpointFrameRepair.SHADOW)||drawer.getClass()==types.get(ViewpointFrameRepair.UPKEEP)||drawer.getClass()==SplitScreenHdrContext.PlayerPass.class);}
    public static int enterReplay(){if(!active())return -1;int previous=REPLAY.get();REPLAY.set(previous+1);return previous;}
    public static void exitReplay(int previous){if(previous>=0)REPLAY.set(previous);}
    public static void replayedSwap(){
        if(!active()||REPLAY.get()==0||overlayFailed||RuntimeState.server||RuntimeState.payload==null||!NativeSource.NEXT_COMMIT.equals(NativeSource.sourceCommit(RuntimeState.payload)))return;
        try{if(OverlaySink.SWAP!=null)OverlaySink.SWAP.invokeExact();}
        catch(Throwable error){overlayFailed=true;System.err.println("[PZOptPreloader/Viewpoint] optional overlay frame counter inactive: "+error);}
    }
    public static boolean skipOnce(Object drawer){return REPLAY.get()>0&&drawer!=null&&(drawer.getClass()==types.get(ViewpointFrameRepair.SHADOW)||drawer.getClass()==types.get(ViewpointFrameRepair.UPKEEP)||drawer.getClass()==SplitScreenHdrContext.PlayerPass.class);}
    public static synchronized void afterJavaMods(){
        if(!ViewpointAnimationCompatibility.selected()){active=false;status="inactive: Viewpoint not selected";return;}
        if(failed)return;if(installed){active=true;status="ready";return;}if(attempted)return;attempted=true;active=false;
        try{install();installed=true;active=true;status="ready";System.out.println("[PZOptPreloader/Viewpoint] intermediate frames retain the chosen FPS cap; once-only commands remain on original frames");}
        catch(ClassNotFoundException|NoClassDefFoundError waiting){active=false;attempted=false;status="waiting for selected Viewpoint Java classes";}
        catch(Throwable error){active=false;failed=true;status="inactive: "+error;System.err.println("[PZOptPreloader/Viewpoint] frame compatibility "+status);}
    }
    private static Map<String,byte[]> capture(Instrumentation instrumentation,Map<String,Class<?>> targets)throws Exception{
        Map<String,byte[]> observed=new ConcurrentHashMap<>();ClassFileTransformer observer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){if(type!=null&&name!=null&&targets.get(name)==type)observed.put(name,bytes.clone());return null;}};
        instrumentation.addTransformer(observer,true);try{instrumentation.retransformClasses(targets.values().toArray(Class<?>[]::new));}finally{instrumentation.removeTransformer(observer);}if(observed.size()!=targets.size())throw new IOException("Incomplete Viewpoint frame instrumentation capture");return observed;
    }
    private static void install()throws Exception{
        ClassLoader loader=ViewpointFrameCompatibility.class.getClassLoader();Map<String,Class<?>> selected=new TreeMap<>();
        for(var pin:ViewpointFrameRepair.RESOURCES.entrySet()){
            try(InputStream input=loader.getResourceAsStream(pin.getKey()+".class")){if(input==null)throw new ClassNotFoundException(pin.getKey());if(!ViewpointFrameRepair.resourceMatches(pin.getKey(),input.readAllBytes()))throw new IOException("Unreviewed Viewpoint/Optimization frame resource: "+pin.getKey());}
            selected.put(pin.getKey(),Class.forName(pin.getKey().replace('/','.'),false,loader));
        }
        Field field=Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");field.setAccessible(true);Instrumentation instrumentation=(Instrumentation)field.get(null);if(instrumentation==null||!instrumentation.isRetransformClassesSupported())throw new IOException("Viewpoint frame retransformation unavailable");for(Class<?> target:selected.values())if(!instrumentation.isModifiableClass(target))throw new IOException("Unmodifiable Viewpoint frame class: "+target.getName());
        Map<String,byte[]> original=capture(instrumentation,selected);Map<String,Map<String,String>> expected=new TreeMap<>();for(var entry:original.entrySet()){ViewpointFrameRepair.verify(entry.getKey(),entry.getValue());expected.put(entry.getKey(),Compatibility.methods(ViewpointFrameRepair.patch(entry.getKey(),entry.getValue())));}
        AtomicReference<Throwable> failure=new AtomicReference<>();ClassFileTransformer transformer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader cl,String name,Class<?> target,ProtectionDomain domain,byte[] bytes){if(target==null||name==null||selected.get(name)!=target)return null;try{return ViewpointFrameRepair.patch(name,bytes);}catch(Throwable error){active=false;failed=true;status="inactive: "+error;failure.compareAndSet(null,error);return null;}}};
        instrumentation.addTransformer(transformer,true);try{Map<String,byte[]> actual=capture(instrumentation,selected);if(failure.get()!=null)throw new IOException("Viewpoint frame transformation refused",failure.get());for(var entry:actual.entrySet())if(!expected.get(entry.getKey()).equals(Compatibility.methods(entry.getValue())))throw new IOException("Viewpoint effective frame transform differs: "+entry.getKey());types=Map.copyOf(selected);}catch(Throwable error){instrumentation.removeTransformer(transformer);throw error;}
    }
}
