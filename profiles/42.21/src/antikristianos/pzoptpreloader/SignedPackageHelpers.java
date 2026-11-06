package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.jar.asm.ClassReader;

/** Defines installed-input additions beside their native package anchor without rewriting signed JARs. */
public final class SignedPackageHelpers {
    private SignedPackageHelpers(){ }
    public static int define(Payload payload,boolean server,Instrumentation instrumentation,JarFile cache)throws Exception{
        var gamePath=Compatibility.gameJar();
        Map<String,Class<?>> anchors=new TreeMap<>();Set<String> loaded=new HashSet<>();
        for(Class<?> type:instrumentation.getAllLoadedClasses()){
            loaded.add(type.getName().replace('.','/'));
            if(type.getClassLoader()!=RuntimeState.class.getClassLoader()||type.isArray()||type.isPrimitive())continue;
            var source=type.getProtectionDomain().getCodeSource();
            if(source!=null&&source.getCertificates()!=null&&source.getCertificates().length>0
                &&java.nio.file.Path.of(source.getLocation().toURI()).toRealPath().equals(gamePath))
                anchors.putIfAbsent(type.getPackageName(),type);
        }
        Map<String,byte[]> missing=new TreeMap<>();Map<String,String> nativeAnchors=new TreeMap<>();
        try(JarFile game=new JarFile(gamePath.toFile())){
            for(String name:payload.classNames()){
                if(!payload.selected(name,server)||game.getJarEntry(name+".class")!=null||loaded.contains(name))continue;
                String anchor=name;int split;
                while((split=anchor.lastIndexOf('$'))>=0){anchor=anchor.substring(0,split);if(game.getJarEntry(anchor+".class")!=null)break;}
                String pkg=name.substring(0,name.lastIndexOf('/')).replace('/','.');
                if(!anchors.containsKey(pkg)){
                    var entry=game.getJarEntry(anchor+".class");if(entry==null)continue;
                    try(InputStream input=game.getInputStream(entry)){input.readAllBytes();}
                    if(entry.getCertificates()==null||entry.getCertificates().length==0)continue;
                    nativeAnchors.put(name,anchor);
                }
                var entry=cache.getJarEntry(name+".class");if(entry==null)throw new IOException("Missing installed helper: "+name);
                try(InputStream input=cache.getInputStream(entry)){missing.put(name,input.readAllBytes());}
            }
        }
        int count=0;Set<String> visiting=new HashSet<>();
        for(String name:missing.keySet())count+=define(name,missing,nativeAnchors,anchors,loaded,visiting);
        return count;
    }
    private static int define(String name,Map<String,byte[]> missing,Map<String,String> nativeAnchors,Map<String,Class<?>> anchors,Set<String> loaded,Set<String> visiting)throws Exception{
        if(loaded.contains(name))return 0;
        if(!visiting.add(name))throw new IOException("Cyclic installed helper hierarchy: "+name);
        byte[] bytes=missing.get(name);ClassReader reader=new ClassReader(bytes);int count=0;
        List<String> parents=new ArrayList<>(List.of(reader.getInterfaces()));if(reader.getSuperName()!=null)parents.add(reader.getSuperName());
        for(String parent:parents)if(missing.containsKey(parent))count+=define(parent,missing,nativeAnchors,anchors,loaded,visiting);
        String pkg=name.substring(0,name.lastIndexOf('/')).replace('/','.');Class<?> anchor=anchors.get(pkg);
        if(anchor==null){anchor=Class.forName(nativeAnchors.get(name).replace('/','.'),false,RuntimeState.class.getClassLoader());anchors.put(pkg,anchor);}
        var origin=anchor.getProtectionDomain().getCodeSource();
        if(origin==null||!java.nio.file.Path.of(origin.getLocation().toURI()).toRealPath().equals(Compatibility.gameJar()))throw new IOException("Installed helper anchor is not native: "+name);
        Class<?> defined=MethodHandles.privateLookupIn(anchor,MethodHandles.lookup()).defineClass(bytes);
        if(defined.getClassLoader()!=anchor.getClassLoader()||defined.getProtectionDomain()!=anchor.getProtectionDomain())throw new IOException("Installed helper protection domain differs: "+name);
        loaded.add(name);visiting.remove(name);return count+1;
    }
}
