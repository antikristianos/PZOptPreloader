package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.bytebuddy.jar.asm.*;

import org.json.*;

/** Validates native sources and the separately verified full-feature legacy port. */
public final class Compatibility {
    public static final String LEGACY = "4a0e9546ec", CURRENT = "e013dbb529";
    private static JSONObject profile;
    private Compatibility() { }
    /** Fixed native B42.22 scale; separate call preserves source concat operands. */
    public static int tileScale() { return 2; }
    public static synchronized JSONObject profile() throws IOException {
        if (profile == null) try (InputStream input = Compatibility.class.getResourceAsStream("compatibility-42.21-42.22.json")) {
            if (input == null) throw new IOException("Legacy compatibility profile missing");
            profile = new JSONObject(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return profile;
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public static Map<String,String> methods(byte[] bytes) {
        Map<String,String> result = new TreeMap<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                ClassWriter writer = new ClassWriter(0);
                writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "MethodFingerprint", null, "java/lang/Object", null);
                return new MethodVisitor(Opcodes.ASM9, writer.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitEnd() { super.visitEnd(); writer.visitEnd(); result.put(name + descriptor, hash(writer.toByteArray())); }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }
    public static boolean supports(Payload payload, String actual) throws IOException {
        String source = payload.build.getProperty("revision");
        if (actual.equals(source)) return true;
        if (!LEGACY.equals(source) || !CURRENT.equals(actual)) return false;
        JSONObject expected = profile().getJSONObject("payloadClasses");
        if (payload.classes.size() != expected.length()) return false;
        for (var entry : payload.classes.entrySet()) {
            if (!hash(Files.readAllBytes(entry.getValue())).equals(expected.optString(entry.getKey()))) return false;
        }
        return true;
    }
    /** Upstream uses a shell-safe key: nested-class dollar signs become underscores. */
    private static String stockKey(String name){return "stock."+name.replace('/','.').replace('$','_');}
    /** Actual unmodified game JAR, never a classpath-shadowed cached override. */
    public static Path gameJar() throws IOException {
        try {
            Path path=Path.of(Class.forName("zombie.GitVersion",false,Compatibility.class.getClassLoader())
                .getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(path))throw new IOException("Game revision does not originate in a JAR");
            return path;
        }catch(java.net.URISyntaxException|ClassNotFoundException error){throw new IOException("Cannot locate game input",error);}
    }
    public static Map<String,byte[]> prepare(Payload payload,String actual)throws IOException {
        if(!supports(payload,actual))throw new Payload.SourceUnavailable("incompatible","No verified adaptation for installed PZOpt/game pair");
        Map<String,byte[]> bytes=new TreeMap<>();
        for(var entry:payload.classes.entrySet()){
            byte[] original=Files.readAllBytes(entry.getValue());
            if(!new ClassReader(original).getClassName().equals(entry.getKey()))throw new Payload.SourceUnavailable("invalid","Downloaded class identity differs: "+entry.getKey());
            String digest=hash(original);
            if(!actual.equals(payload.build.getProperty("revision"))&&!profile().getJSONObject("payloadClasses").getString(entry.getKey()).equals(digest))
                throw new Payload.SourceUnavailable("incompatible","Installed original changed during preparation: "+entry.getKey());
            bytes.put(entry.getKey(),original);payload.originalClassHashes.put(entry.getKey(),digest);
        }
        if(!actual.equals(payload.build.getProperty("revision"))){
            try(InputStream resource=Compatibility.class.getResourceAsStream("legacy-port-42.22.json");java.util.jar.JarFile game=new java.util.jar.JarFile(gameJar().toFile())){
                if(resource==null)throw new IOException("Input-reference adaptation rules missing");
                JSONObject rules=new JSONObject(new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                bytes=new PortRecipes(rules,bytes,game).apply();
            }catch(RuntimeException error){throw new Payload.SourceUnavailable("incompatible","Full adaptation refused: "+error.getMessage());}
        }
        Map<String,byte[]> result=new TreeMap<>();
        try{
            for(var entry:bytes.entrySet())result.put(VisualExclusions.mapped(entry.getKey()),VisualExclusions.patch(entry.getKey(),entry.getValue(),!LEGACY.equals(actual)));
        }catch(RuntimeException error){throw new Payload.SourceUnavailable("incompatible","Excluded-feature adapter refused: "+error.getMessage());}
        try(java.util.jar.JarFile game=new java.util.jar.JarFile(gameJar().toFile())){
            if(actual.equals(payload.build.getProperty("revision"))){
                Set<String> matched=new HashSet<>();
                for(var entry:game.stream().filter(e->e.getName().endsWith(".class")).toList()){
                    String name=entry.getName().substring(0,entry.getName().length()-6),key=stockKey(name);
                    String expected=payload.build.getProperty(key);if(expected==null)continue;
                    try(InputStream in=game.getInputStream(entry)){if(hash(in.readAllBytes()).equalsIgnoreCase(expected))matched.add(key);}
                }
                for(String key:payload.build.stringPropertyNames())if(key.startsWith("stock.")&&!matched.contains(key))
                    throw new IOException("Native source class differs from upstream catalog: "+key);
            }
            if(!actual.equals(payload.build.getProperty("revision"))){
                JSONObject stock=profile().getJSONObject("stockClasses");
                for(String name:stock.keySet()){
                    String expected=stock.getJSONObject(name).getString("currentHash");var entry=game.getJarEntry(name+".class");
                    if(expected.equals("removed")){if(entry!=null)throw new IOException("Removed native baseline type reappeared: "+name);continue;}
                    if(entry==null)throw new IOException("Missing verified native baseline: "+name);
                    try(InputStream in=game.getInputStream(entry)){if(!hash(in.readAllBytes()).equals(expected))throw new IOException("Native baseline changed: "+name);}
                }
            }
            ClassReferences.verify(result,game);
        }catch(IOException|RuntimeException error){throw new Payload.SourceUnavailable("incompatible","Game API preflight refused: "+error.getMessage());}
        return result;
    }
    /** Original provenance stays unchanged; only the derived process cache describes the verified target. */
    public static Properties effectiveBuild(Payload payload,String actual)throws IOException {
        Properties result=new Properties();result.putAll(payload.build);
        if(actual.equals(payload.build.getProperty("revision")))return result;
        result.setProperty("pzoptpreloader.sourceRevision",payload.build.getProperty("revision"));
        result.setProperty("pzoptpreloader.adaptation","installed-input-port-42.22");result.setProperty("revision",actual);
        try(java.util.jar.JarFile game=new java.util.jar.JarFile(gameJar().toFile())){
            for(String key:new HashSet<>(result.stringPropertyNames()))if(key.startsWith("stock."))result.remove(key);
            for(var entry:game.stream().filter(e->e.getName().endsWith(".class")).toList()){
                String name=entry.getName().substring(0,entry.getName().length()-6);
                if(payload.targets.stream().noneMatch(root->name.equals(root)||name.startsWith(root+"$")))continue;
                try(InputStream in=game.getInputStream(entry)){result.setProperty(stockKey(name),hash(in.readAllBytes()));}
            }
        }return result;
    }
}