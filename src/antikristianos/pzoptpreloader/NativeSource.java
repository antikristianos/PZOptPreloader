package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import net.bytebuddy.jar.asm.*;
import org.json.JSONObject;

/** Exact native-source identity and final-definition filtering; never rewrites installed input. */
public final class NativeSource {
    public static final String COMMIT="fad57862",NEXT_COMMIT="20f89d32",REVISION="3d39148846";
    private static final ThreadLocal<Boolean> REPAIR=ThreadLocal.withInitial(()->false);
    private record Context(Payload payload){ }
    private static final ThreadLocal<ArrayDeque<Context>> CONTEXT=ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<String,JSONObject> PROFILES=new HashMap<>();
    private NativeSource(){ }
    private static String profileKey(String commit){
        if(commit==null)return null;
        if(commit.startsWith(COMMIT))return COMMIT;
        return commit.startsWith(NEXT_COMMIT)?NEXT_COMMIT:null;
    }
    static boolean reviewedCommit(String commit){return profileKey(commit)!=null;}
    public static String sourceCommit(Payload p){return p==null?null:profileKey(p.build.getProperty("commit",""));}
    public static boolean is(Payload p){return sourceCommit(p)!=null&&REVISION.equals(p.build.getProperty("revision"));}
    private static synchronized JSONObject profile(Payload p)throws IOException {
        String key=sourceCommit(p);if(key==null)throw new IOException("Unknown native source profile");
        JSONObject profile=PROFILES.get(key);
        if(profile==null)try(InputStream in=NativeSource.class.getResourceAsStream("native-"+key+".json")){
            if(in==null)throw new IOException("Native source profile missing: "+key);profile=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));PROFILES.put(key,profile);
        }
        return profile;
    }
    public static void verify(Payload p)throws IOException {
        if(!is(p))return;
        JSONObject selected=profile(p),expected=selected.getJSONObject("classes");
        if(expected.length()!=p.classes.size())throw new IOException("Native source class inventory differs");
        for(var entry:p.classes.entrySet())if(!Compatibility.hash(Files.readAllBytes(entry.getValue())).equals(expected.optString(entry.getKey())))
            throw new IOException("Native source class changed: "+entry.getKey());
        if(!Compatibility.hash(Files.readAllBytes(Compatibility.gameJar())).equals(selected.getString("gameSha256")))throw new IOException("Native game identity differs");
    }
    public static boolean enter(Payload p){boolean previous=REPAIR.get();CONTEXT.get().push(new Context(p));REPAIR.set(is(p));return previous;}
    public static void leave(boolean previous){ArrayDeque<Context> contexts=CONTEXT.get();if(!contexts.isEmpty())contexts.pop();REPAIR.set(previous);}
    static boolean currentRepair(){return REPAIR.get();}
    public static boolean repairPin(String previous,String actual){
        if(!REPAIR.get()||actual==null)return false;
        try{Context context=CONTEXT.get().peek();return actual.equals(profile(context==null?RuntimeState.payload:context.payload()).getJSONObject("repairAliases").optString(previous));}
        catch(IOException e){throw new IllegalStateException(e);}
    }
    private static String normalized(byte[] bytes){ClassWriter w=new ClassWriter(0);new ClassReader(bytes).accept(w,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return Compatibility.hash(w.toByteArray());}
    public static Set<String> omitVanilla(Map<String,byte[]> definitions)throws IOException {
        Set<String> omitted=new TreeSet<>();
        try(JarFile game=new JarFile(Compatibility.gameJar().toFile())){for(var entry:definitions.entrySet()){
            var stock=game.getJarEntry(entry.getKey()+".class");if(stock==null)continue;
            byte[] nativeBytes;try(InputStream in=game.getInputStream(stock)){nativeBytes=in.readAllBytes();}
            if(Arrays.equals(nativeBytes,entry.getValue())||normalized(nativeBytes).equals(normalized(entry.getValue())))omitted.add(entry.getKey());
        }}
        omitted.forEach(definitions::remove);return Set.copyOf(omitted);
    }
}
