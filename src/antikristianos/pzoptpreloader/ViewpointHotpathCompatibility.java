package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Immutable native feature selection, published once per finalized Java-mod selection. */
public final class ViewpointHotpathCompatibility {
    public static volatile int owners;
    private static int nativeFeatures;
    private static volatile boolean installed,failed;
    private ViewpointHotpathCompatibility(){ }
    public static void beginSelection(){owners=0;}
    public static synchronized void selectionChanged(){owners=installed&&!failed&&ViewpointAnimationCompatibility.selected()?nativeFeatures:0;}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static byte[] capture(Instrumentation inst,Class<?> target)throws Exception{
        AtomicReference<byte[]> result=new AtomicReference<>();ClassFileTransformer observer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t==target)result.set(b.clone());return null;}};inst.addTransformer(observer,true);try{inst.retransformClasses(target);}finally{inst.removeTransformer(observer);}if(result.get()==null)throw new IOException("Hotpath instrumentation capture incomplete");return result.get();
    }
    public static synchronized void afterJavaMods(){
        if(failed||!ViewpointAnimationCompatibility.selected()){selectionChanged();return;}if(installed){selectionChanged();return;}
        try{
            ClassLoader loader=ViewpointHotpathCompatibility.class.getClassLoader();byte[] original=null;for(String name:ViewpointHotpathRepair.RESOURCES.keySet()){try(InputStream in=loader.getResourceAsStream(name+".class")){if(in==null)return;byte[] code=in.readAllBytes();ViewpointHotpathRepair.verifyResource(name,code);if(name.equals(ViewpointHotpathRepair.TARGET))original=code;}}
            Class<?> target=Class.forName(ViewpointHotpathRepair.TARGET.replace('/','.'),false,loader),config=Class.forName("pzopt.Config");
            int features=0;if((boolean)Class.forName("pzopt.Overrides").getMethod("enabled").invoke(null)){features=8|16;if(config.getField("STATS_NO_BOX").getBoolean(null))features|=1;if(config.getField("ECS_LOOKUP_FAST").getBoolean(null))features|=2;if(config.getField("STATE_MACHINE_NO_ITER").getBoolean(null))features|=4;}
            Instrumentation inst=(Instrumentation)field(Class.forName("me.zed_0xff.zombie_buddy.Loader"),"g_instrumentation").get(null);if(inst==null||!inst.isRetransformClassesSupported()||!inst.isModifiableClass(target))throw new IOException("Hotpath instrumentation unavailable");
            Map<String,String> resources=Compatibility.methods(original);if(!resources.equals(Compatibility.methods(capture(inst,target))))throw new IOException("Changed live Viewpoint hotpath methods");Map<String,String> expected=Compatibility.methods(ViewpointHotpathRepair.patch(original));
            ClassFileTransformer transformer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t!=target)return null;try{return ViewpointHotpathRepair.patch(b);}catch(Throwable error){failed=true;owners=0;System.err.println("[PZOptPreloader/Viewpoint] hotpath transform refused: "+error);return null;}}};inst.addTransformer(transformer,true);try{if(!expected.equals(Compatibility.methods(capture(inst,target)))||failed)throw new IOException("Hotpath effective bytes differ");}catch(Throwable error){inst.removeTransformer(transformer);throw error;}
            nativeFeatures=features;installed=true;selectionChanged();System.out.println("[PZOptPreloader/Viewpoint] equivalent native hotpaths retained in ISO and perspective; feature mask="+owners);
        }catch(ClassNotFoundException|NoClassDefFoundError waiting){/* Retry at the next existing optional Java boundary. */}
        catch(Throwable error){failed=true;owners=0;System.err.println("[PZOptPreloader/Viewpoint] hotpath compatibility inactive: "+error);}
    }
}
