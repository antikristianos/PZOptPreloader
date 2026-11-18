package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.lang.invoke.*;
import java.security.ProtectionDomain;

/** Optional final-session bridge; never changes the native texture budget or optimization preferences. */
public final class ViewpointTextureCompatibility {
    private static final ThreadLocal<Integer> DEPTH=ThreadLocal.withInitial(()->0);
    private static volatile boolean installed,broken;
    private static MethodHandle enabled;
    private ViewpointTextureCompatibility(){ }
    public static boolean scoped(){return installed&&!broken&&ViewpointAnimationCompatibility.selected()&&DEPTH.get()>0;}
    public static int enter(){int previous=DEPTH.get();if(installed&&!broken&&ViewpointAnimationCompatibility.selected())try{if((boolean)enabled.invokeExact())DEPTH.set(previous+1);}catch(Throwable error){broken=true;System.err.println("[PZOptPreloader/Viewpoint] texture bridge disabled: "+error);}return previous;}
    public static void exit(int previous){DEPTH.set(previous);}
    public static boolean installed(){return installed&&!broken;}
    public static synchronized void afterJavaMods(){
        if(installed||broken||!ViewpointAnimationCompatibility.selected())return;
        try{
            ClassLoader loader=ViewpointTextureCompatibility.class.getClassLoader();String name=ViewpointTextureRepair.SNAPSHOT;
            byte[] resource;try(InputStream in=loader.getResourceAsStream(name+".class")){if(in==null)return;resource=in.readAllBytes();}
            if(!ViewpointTextureRepair.SNAPSHOT_RESOURCE.equals(Compatibility.hash(resource)))throw new IOException("Unreviewed Viewpoint snapshot resource");
            ViewpointTextureRepair.verifySnapshot(resource);
            Class<?> target=Class.forName(name.replace('/','.'),false,loader);
            Class<?> view=Class.forName("viewpoint.core.View",false,loader);enabled=MethodHandles.publicLookup().findStaticGetter(view,"enabled",boolean.class);
            var field=Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");field.setAccessible(true);Instrumentation instrumentation=(Instrumentation)field.get(null);
            if(!instrumentation.isModifiableClass(target))throw new IOException("Viewpoint snapshot cannot be transformed");
            byte[][] actual={null};ClassFileTransformer capture=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader cl,String type,Class<?> redef,ProtectionDomain domain,byte[] bytes){if(name.equals(type)&&redef==target)actual[0]=bytes.clone();return null;}};
            instrumentation.addTransformer(capture,true);try{instrumentation.retransformClasses(target);}finally{instrumentation.removeTransformer(capture);}
            if(actual[0]==null)throw new IOException("Viewpoint snapshot live bytes unavailable");ViewpointTextureRepair.verifySnapshot(actual[0]);
            String expected=Compatibility.methods(ViewpointTextureRepair.snapshot(actual[0])).get("ready()Z");
            instrumentation.addTransformer(new ClassFileTransformer(){@Override public byte[] transform(ClassLoader cl,String type,Class<?> redef,ProtectionDomain domain,byte[] bytes){if(!name.equals(type)||redef!=target)return null;try{return ViewpointTextureRepair.snapshot(bytes);}catch(Throwable error){broken=true;System.err.println("[PZOptPreloader/Viewpoint] texture transform refused: "+error);return null;}}},true);
            actual[0]=null;instrumentation.addTransformer(capture,true);try{instrumentation.retransformClasses(target);}finally{instrumentation.removeTransformer(capture);}
            if(broken||actual[0]==null||!expected.equals(Compatibility.methods(actual[0]).get("ready()Z")))throw new IOException("Viewpoint snapshot transform did not match verified output");
            installed=true;System.out.println("[PZOptPreloader/Viewpoint] retained texture snapshots refresh expired first-sight pacing flags");
        }catch(Throwable error){broken=true;System.err.println("[PZOptPreloader/Viewpoint] texture bridge inactive: "+error);}
    }
}