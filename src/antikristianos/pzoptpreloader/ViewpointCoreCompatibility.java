package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional native client owner selection; shared TableReads gate covers both VP advice phases. */
public final class ViewpointCoreCompatibility {
    private static volatile boolean installed,failed;
    private static volatile int fallback=Integer.MIN_VALUE;
    private static Field rawLock,rawUncapped;
    private ViewpointCoreCompatibility(){ }
    public static boolean active(){return installed&&!failed&&ViewpointAnimationCompatibility.selected();}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static int nativeCap()throws Exception{if(rawLock==null){rawLock=field(zombie.core.PerformanceSettings.class,"lockFps");rawUncapped=field(zombie.core.PerformanceSettings.class,"uncappedFps");}return rawUncapped.getBoolean(null)?0:rawLock.getInt(null);}
    public static void beforeModSelection(){if(RuntimeState.server||RuntimeState.payload==null||!NativeSource.is(RuntimeState.payload))return;try{fallback=nativeCap();}catch(Throwable error){fallback=Integer.MIN_VALUE;}}
    public static int gameCap(int saved,int original){if(!active())return original;try{int selected=saved==Integer.MIN_VALUE?(fallback==Integer.MIN_VALUE?nativeCap():fallback):saved;return selected<=0?0:selected;}catch(Throwable error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] native cap read refused: "+error);return original;}}
    private static Map<String,byte[]> capture(Instrumentation inst,Map<String,Class<?>> targets)throws Exception{
        Map<String,byte[]> bytes=new ConcurrentHashMap<>();ClassFileTransformer observer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t!=null&&targets.get(n)==t)bytes.put(n,b.clone());return null;}};inst.addTransformer(observer,true);try{inst.retransformClasses(targets.values().toArray(Class<?>[]::new));}finally{inst.removeTransformer(observer);}if(bytes.size()!=targets.size())throw new IOException("Core instrumentation capture incomplete");return bytes;
    }
    public static synchronized void afterJavaMods(){
        if(installed||failed||!ViewpointAnimationCompatibility.selected())return;
        try{
            ClassLoader loader=ViewpointCoreCompatibility.class.getClassLoader();Map<String,Class<?>> targets=new TreeMap<>();Map<String,Map<String,String>> resources=new TreeMap<>();
            for(var entry:ViewpointCoreRepair.RESOURCES.entrySet()){byte[] code;try(InputStream in=loader.getResourceAsStream(entry.getKey()+".class")){if(in==null)return;code=in.readAllBytes();}if(!entry.getValue().equals(Compatibility.hash(code)))throw new IOException("Unreviewed Viewpoint core resource: "+entry.getKey());ViewpointCoreRepair.verify(entry.getKey(),code);resources.put(entry.getKey(),Compatibility.methods(code));targets.put(entry.getKey(),Class.forName(entry.getKey().replace('/','.'),false,loader));}
            nativeCap();Instrumentation inst=(Instrumentation)field(Class.forName("me.zed_0xff.zombie_buddy.Loader"),"g_instrumentation").get(null);if(inst==null||!inst.isRetransformClassesSupported())throw new IOException("Core instrumentation unavailable");for(Class<?> t:targets.values())if(!inst.isModifiableClass(t))throw new IOException("Unmodifiable Viewpoint core target");
            Map<String,byte[]> original=capture(inst,targets);Map<String,Map<String,String>> expected=new TreeMap<>();for(var entry:original.entrySet()){if(!resources.get(entry.getKey()).equals(Compatibility.methods(entry.getValue())))throw new IOException("Changed live Viewpoint core methods: "+entry.getKey());expected.put(entry.getKey(),Compatibility.methods(ViewpointCoreRepair.patch(entry.getKey(),entry.getValue())));}
            ClassFileTransformer transformer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t==null||targets.get(n)!=t)return null;try{if(!resources.get(n).equals(Compatibility.methods(b)))throw new IOException("Changed live core owner methods");return ViewpointCoreRepair.patch(n,b);}catch(Throwable error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] core transform refused: "+error);return null;}}};inst.addTransformer(transformer,true);try{Map<String,byte[]> actual=capture(inst,targets);if(failed)throw new IOException("Core transformation refused");for(var entry:actual.entrySet())if(!expected.get(entry.getKey()).equals(Compatibility.methods(entry.getValue())))throw new IOException("Core effective bytes differ: "+entry.getKey());}catch(Throwable error){inst.removeTransformer(transformer);throw error;}
            installed=true;
            // Viewpoint may already have loaded its monitor/old cap before this final Java-mod boundary.
            Class<?> caps=targets.get(ViewpointCoreRepair.CAPS);if((int)caps.getMethod("gameCap").invoke(null)!=Integer.MIN_VALUE)caps.getMethod("optionsLoaded").invoke(null);
            System.out.println("[PZOptPreloader/Viewpoint] native Lua/UI reads retained; saved game FPS takes priority while menu and simulation caps remain separate");
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Next existing lifecycle boundary retries missing optional Java. */}
        catch(Throwable error){failed=true;System.err.println("[PZOptPreloader/Viewpoint] core compatibility inactive: "+error);}
    }
}
