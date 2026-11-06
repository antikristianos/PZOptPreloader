package antikristianos.pzoptpreloader;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.*;
import net.bytebuddy.jar.asm.*;

/** Retains the already supported installed PZFB 1.8.0 colour family in signed native packages. */
public final class PzfbSignedPackages {
    private static final String PREFIX="zombie/core/";
    private static final Map<String,String> HASHES=Map.ofEntries(
        Map.entry("Color$1","d5ce98b40758d42299a87510610f3c576d0a48c85c185592e952d451634c9bda"),
        Map.entry("Color$2","00be689d4015296fe677ae12d122b85a43c416379e61c02df699a3b135802674"),
        Map.entry("Color$3","78624d7fee8b0c6e31c953fa7c2e80303b9bc528f271ba45a7b66ed75208e027"),
        Map.entry("Color$4","fee2131ac72c0bfdd67a7b7e0307e70e92335ee8846153ffa71210d0407299b9"),
        Map.entry("Color$5","24ccd2a32822b49b743a065649606a08daefd333b879c4522e97295c3f4a8ad5"),
        Map.entry("Color$6","55ac7aec0378be3f97d5bcb3e18359056e95169581d21e8ce157d2ef72db85f9"),
        Map.entry("Color$7","96da660d1861cb5246f2c53c29772b98e7d9c25b22a5a4b35dc2641749488e07"),
        Map.entry("Color$8","808db4e82e3e7feb8797388554f5fd830c621d0a4fd02d988b02de765f8ab724"),
        Map.entry("Color$9","201b32c5c145ac3094e321fe258276e79be67c9d2276b208aa3242d58441d6c2"),
        Map.entry("Color","90d1ff7a63b068f60166737cd197928cfd3a9e9c20710b4648c6008408646855"));
    public record Prepared(Class<?> anchor,Map<String,byte[]> definitions){ }
    private PzfbSignedPackages(){ }
    public static Prepared prepare(Instrumentation instrumentation)throws Exception{
        try{return prepareInstalled(instrumentation);}
        catch(Exception|LinkageError error){throw new StartupFallback.EnvironmentFailure("Installed PZFB signed-package inputs refused: "+error,error);}
    }
    private static Prepared prepareInstalled(Instrumentation instrumentation)throws Exception{
        ClassLoader loader=PzfbSignedPackages.class.getClassLoader();var resource=loader.getResource(PREFIX+"Color.class");
        Path game=Compatibility.gameJar();
        if(resource==null||resource.openConnection() instanceof java.net.JarURLConnection connection
            &&Path.of(connection.getJarFileURL().toURI()).toRealPath().equals(game))return new Prepared(null,Map.of());
        byte[] root;try(InputStream input=resource.openStream()){root=input.readAllBytes();}
        boolean[] pzfb={false};new ClassReader(root).accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){if(n.equals("fbPing")&&d.equals("()Ljava/lang/String;"))pzfb[0]=true;return null;}},ClassReader.SKIP_CODE);
        if(!pzfb[0])return new Prepared(null,Map.of());
        Class<?> anchor=Class.forName("zombie.core.Core",false,loader);var origin=anchor.getProtectionDomain().getCodeSource();
        if(origin==null||origin.getCertificates()==null||origin.getCertificates().length==0)return new Prepared(null,Map.of());
        if(!Path.of(origin.getLocation().toURI()).toRealPath().equals(game))throw new IOException("PZFB native anchor changed");
        Set<String> loaded=new HashSet<>();for(Class<?> type:instrumentation.getAllLoadedClasses())if(type.getClassLoader()==loader)loaded.add(type.getName().replace('.','/'));
        Map<String,byte[]> definitions=new LinkedHashMap<>();
        for(String name:HASHES.keySet().stream().sorted(Comparator.comparing((String n)->!n.contains("$")).thenComparing(n->n)).toList()){
            String full=PREFIX+name;if(loaded.contains(full))throw new IOException("PZFB class loaded before signed-package preparation: "+full);
            byte[] bytes;try(InputStream input=loader.getResourceAsStream(full+".class")){if(input==null)throw new IOException("PZFB class missing: "+full);bytes=input.readAllBytes();}
            if(!HASHES.get(name).equals(Compatibility.hash(bytes)))throw new IOException("Unverified installed PZFB member: "+full);
            definitions.put(full,bytes);
        }
        return new Prepared(anchor,definitions);
    }
    public static void define(Prepared prepared)throws Exception{
        try{defineInstalled(prepared);}
        catch(Exception|LinkageError error){throw new StartupFallback.EnvironmentFailure("Installed PZFB signed-package definition refused: "+error,error);}
    }
    private static void defineInstalled(Prepared prepared)throws Exception{
        if(prepared.definitions.isEmpty())return;
        var lookup=MethodHandles.privateLookupIn(prepared.anchor,MethodHandles.lookup());
        for(var entry:prepared.definitions.entrySet()){
            Class<?> type=lookup.defineClass(entry.getValue());
            if(type.getProtectionDomain()!=prepared.anchor.getProtectionDomain())throw new IOException("PZFB native package domain differs: "+entry.getKey());
        }
    }
}
