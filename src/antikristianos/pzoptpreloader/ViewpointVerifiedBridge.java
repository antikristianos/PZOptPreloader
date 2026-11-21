package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Install new optional bridges only after resource, live-method and effective-byte checks. */
public final class ViewpointVerifiedBridge {
    @FunctionalInterface public interface Patch { byte[] apply(String name,byte[] bytes)throws IOException; }
    private ViewpointVerifiedBridge() { }
    private static Map<String,byte[]> capture(Instrumentation inst,Map<String,Class<?>> targets)throws Exception {
        Map<String,byte[]> bytes=new ConcurrentHashMap<>();
        ClassFileTransformer observer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t!=null&&targets.get(n)==t)bytes.put(n,b.clone());return null;}};
        inst.addTransformer(observer,true);try{inst.retransformClasses(targets.values().toArray(Class<?>[]::new));}finally{inst.removeTransformer(observer);}
        if(bytes.size()!=targets.size())throw new IOException("Incomplete Viewpoint bridge capture");return bytes;
    }
    public static boolean install(Map<String,String> pins,Set<String> names,Patch patch,Runnable refused)throws Exception {
        if(!ViewpointAnimationCompatibility.selected())return false;
        ClassLoader loader=ViewpointVerifiedBridge.class.getClassLoader();Map<String,byte[]> resources=new TreeMap<>();
        for(var pin:pins.entrySet())try(InputStream in=loader.getResourceAsStream(pin.getKey()+".class")){
            if(in==null)return false;byte[] bytes=in.readAllBytes();if(!pin.getValue().equals(Compatibility.hash(bytes)))throw new IOException("Unreviewed Viewpoint resource: "+pin.getKey());resources.put(pin.getKey(),bytes);
        }
        var field=Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");field.setAccessible(true);Instrumentation inst=(Instrumentation)field.get(null);
        if(inst==null||!inst.isRetransformClassesSupported())throw new IOException("Viewpoint retransformation unavailable");
        Map<String,Class<?>> targets=new TreeMap<>();for(String name:names){Class<?> target=Class.forName(name.replace('/','.'),false,loader);if(!inst.isModifiableClass(target))throw new IOException("Unmodifiable Viewpoint target: "+name);targets.put(name,target);}
        Map<String,byte[]> live=capture(inst,targets);Map<String,Map<String,String>> expected=new TreeMap<>();
        for(var entry:live.entrySet()){
            if(!Compatibility.methods(resources.get(entry.getKey())).equals(Compatibility.methods(entry.getValue())))throw new IOException("Changed live Viewpoint methods: "+entry.getKey());
            expected.put(entry.getKey(),Compatibility.methods(patch.apply(entry.getKey(),entry.getValue())));
        }
        ClassFileTransformer transformer=new ClassFileTransformer(){@Override public byte[] transform(ClassLoader l,String n,Class<?> t,ProtectionDomain d,byte[] b){if(t==null||targets.get(n)!=t)return null;try{return patch.apply(n,b);}catch(IOException|RuntimeException error){refused.run();throw new IllegalStateException("Viewpoint bridge transformation refused",error);}}};
        inst.addTransformer(transformer,true);
        try{for(var entry:capture(inst,targets).entrySet())if(!expected.get(entry.getKey()).equals(Compatibility.methods(entry.getValue())))throw new IOException("Viewpoint bridge effective bytes differ: "+entry.getKey());}
        catch(Exception|LinkageError error){inst.removeTransformer(transformer);try{inst.retransformClasses(targets.values().toArray(Class<?>[]::new));}catch(Exception restore){error.addSuppressed(restore);}throw error;}
        return true;
    }
}
