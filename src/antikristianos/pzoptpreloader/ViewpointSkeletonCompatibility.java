package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import zombie.characters.IsoGameCharacter;

/** Switch only after actual native animation completion, using VP's own lossless handback. */
public final class ViewpointSkeletonCompatibility {
    private static volatile boolean installed,failed,nativeOwned;
    private static Field view,on,pending,thinned,skipped,count,animActive,animPending,animEnabled,animFailed;
    private static Method animated,clear,clearRate,toggleGet,frameJoin,animJoin,framePending;
    private static Object batch,toggle;
    private static Map<?,?> owned;
    private ViewpointSkeletonCompatibility(){ }
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    public static boolean active(){return installed&&!failed&&ViewpointAnimationCompatibility.selected();}
    public static boolean nativeOwner(){return active()&&nativeOwned;}
    public static void beginSelection(){nativeOwned=false;}
    // A previously ported zombie may lose its model before its lazy pose has been returned.
    public static boolean owns(IsoGameCharacter character){return active()&&!nativeOwned&&owned.containsKey(character);}
    private static boolean callbacksComplete()throws Exception{return pending.get(null)==null&&thinned.get(null)==null&&skipped.get(null)==null;}
    public static void animationFinished(){
        if(!active()||Thread.currentThread()!=zombie.GameWindow.gameThread)return;
        try{
            boolean next=!view.getBoolean(null)&&animEnabled.getBoolean(null)&&!animFailed.getBoolean(null);
            if(next==nativeOwned)return;
            // FinishAnimation can deliberately defer draw/background bone work. Join it only at an owner change.
            frameJoin.invoke(null);animJoin.invoke(null);
            if((boolean)framePending.invoke(null)||animActive.getBoolean(null)||animPending.getInt(null)!=0||!callbacksComplete())return;
            if(next){
                // Late model completions can append rows after MovingObjectUpdateScheduler.postupdate.
                if(count.getInt(batch)!=0)animated.invoke(null);
                if(count.getInt(batch)!=0||!callbacksComplete())return;
                clearRate.invoke(null);clear.invoke(null);
                if(!owned.isEmpty()||count.getInt(batch)!=0)throw new IOException("Incomplete original skeleton handback");
                on.setBoolean(null,false);
            }else on.setBoolean(null,(boolean)toggleGet.invoke(toggle));
            nativeOwned=next;
            System.out.println("[PZOptPreloader/Viewpoint] skeleton owner="+(next?"native ISO":"Viewpoint perspective")+"; animation batches completed");
        }catch(Throwable error){failed=true;nativeOwned=false;System.err.println("[PZOptPreloader/Viewpoint] skeleton handback refused: "+error);}
    }
    private static Map<String,byte[]> capture(Instrumentation inst,Map<String,Class<?>> targets)throws Exception{
        Map<String,byte[]> bytes=new ConcurrentHashMap<>();ClassFileTransformer observer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t!=null&&targets.get(n)==t)bytes.put(n,b.clone());return null;}};inst.addTransformer(observer,true);try{inst.retransformClasses(targets.values().toArray(Class<?>[]::new));}finally{inst.removeTransformer(observer);}if(bytes.size()!=targets.size())throw new IOException("Skeleton instrumentation capture incomplete");return bytes;
    }
    public static synchronized void afterJavaMods(){
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try{
            ClassLoader loader=ViewpointSkeletonCompatibility.class.getClassLoader();Map<String,Class<?>> targets=new TreeMap<>();Map<String,Map<String,String>> resources=new TreeMap<>();
            for(var entry:ViewpointSkeletonRepair.RESOURCES.entrySet()){byte[] code;try(InputStream in=loader.getResourceAsStream(entry.getKey()+".class")){if(in==null)return;code=in.readAllBytes();}if(!entry.getValue().equals(Compatibility.hash(code)))throw new IOException("Unreviewed Viewpoint skeleton resource: "+entry.getKey());if(entry.getKey().equals(ViewpointSkeletonRepair.PORT)||entry.getKey().equals(ViewpointSkeletonRepair.RATE)){resources.put(entry.getKey(),Compatibility.methods(code));targets.put(entry.getKey(),Class.forName(entry.getKey().replace('/','.'),false,loader));}}
            Class<?> port=targets.get(ViewpointSkeletonRepair.PORT),rate=targets.get(ViewpointSkeletonRepair.RATE),ab=Class.forName("pzopt.AnimBatch"),fb=Class.forName("pzopt.FrameBatch");
            view=field(Class.forName("viewpoint.core.View"),"enabled");on=field(port,"on");pending=field(port,"pending");thinned=field(port,"thinned");skipped=field(rate,"skipped");batch=field(port,"batch").get(null);count=field(batch.getClass(),"count");Object slots=field(port,"slots").get(null);owned=(Map<?,?>)field(slots.getClass(),"slots").get(slots);
            animated=port.getMethod("animated");clear=port.getMethod("clear");clearRate=rate.getMethod("clear");toggle=field(port,"ON").get(null);toggleGet=toggle.getClass().getMethod("get");frameJoin=fb.getMethod("join");animJoin=ab.getMethod("join");framePending=fb.getMethod("hasPending");animActive=field(ab,"active");animPending=field(ab,"pendingCount");animEnabled=field(ab,"ENABLED");animFailed=field(ab,"failed");
            Instrumentation inst=(Instrumentation)field(Class.forName("me.zed_0xff.zombie_buddy.Loader"),"g_instrumentation").get(null);if(inst==null||!inst.isRetransformClassesSupported())throw new IOException("Skeleton instrumentation unavailable");for(Class<?> t:targets.values())if(!inst.isModifiableClass(t))throw new IOException("Unmodifiable skeleton target");
            Map<String,byte[]> original=capture(inst,targets);Map<String,Map<String,String>> expected=new TreeMap<>();for(var entry:original.entrySet()){if(!resources.get(entry.getKey()).equals(Compatibility.methods(entry.getValue())))throw new IOException("Changed live skeleton methods: "+entry.getKey());expected.put(entry.getKey(),Compatibility.methods(ViewpointSkeletonRepair.patch(entry.getKey(),entry.getValue())));}
            ClassFileTransformer transformer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t==null||targets.get(n)!=t)return null;try{return ViewpointSkeletonRepair.patch(n,b);}catch(Throwable error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] skeleton transform refused: "+error);return null;}}};inst.addTransformer(transformer,true);try{Map<String,byte[]> actual=capture(inst,targets);if(failed)throw new IOException("Skeleton transformation refused");for(var entry:actual.entrySet())if(!expected.get(entry.getKey()).equals(Compatibility.methods(entry.getValue())))throw new IOException("Skeleton effective bytes differ: "+entry.getKey());}catch(Throwable error){inst.removeTransformer(transformer);throw error;}
            installed=true;System.out.println("[PZOptPreloader/Viewpoint] mode-aware skeleton handback ready at native animation completion; crash protection retained");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Retry at the next existing optional-Java lifecycle boundary. */}
        catch(Throwable error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] skeleton compatibility inactive: "+error);}
    }
}
