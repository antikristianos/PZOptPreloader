package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** User-local ORIGINAL source snapshots. Nothing from this store is a packaged mod input. */
public final class SourceHistory {
    public record Previous(Path root,String id,String label){}
    private SourceHistory(){}
    private static Path directory(Path config,String revision)throws IOException{
        if(!revision.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("Invalid game revision for source history");
        return config.toAbsolutePath().normalize().resolve("PZOptPreloader/sources").resolve(revision);
    }
    private static String relative(Path root,Path path)throws IOException{
        Path actual=path.toRealPath();if(!actual.startsWith(root))throw new IOException("Source file leaves its installation");
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\','/');
    }
    private static Properties properties(Path path)throws IOException{
        Properties result=new Properties();if(Files.isRegularFile(path))try(InputStream in=Files.newInputStream(path)){result.load(in);}return result;
    }
    private static void atomicProperties(Path path,Properties data)throws IOException{
        Files.createDirectories(path.getParent());Path temporary=Files.createTempFile(path.getParent(),"state-",".tmp");
        try{
            try(OutputStream out=Files.newOutputStream(temporary)){data.store(out,"PZOptPreloader original-source selection");}
            try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException error){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private static Path member(Path root,String name)throws IOException{
        Path path=root.resolve(name.replace('\\','/')).normalize();
        if(!path.startsWith(root)||path.equals(root)||name.contains(":"))throw new IOException("Unsafe snapshot member");return path;
    }
    private static void removeStage(Path root,Path stage)throws IOException{
        if(!Files.exists(stage))return;
        Path base=root.toRealPath(),actual=stage.toRealPath();
        if(!actual.startsWith(base)||actual.equals(base)||!actual.getFileName().toString().startsWith(".stage-"))throw new IOException("Refused unsafe history-stage removal");
        try(var files=Files.walk(actual)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
    }
    public static void remember(Payload payload,String revision)throws IOException{
        remember(SourcePaths.configDirectory(),payload,revision);
    }
    static void remember(Path config,Payload payload,String revision)throws IOException{
        Path base=directory(config,revision);Files.createDirectories(base);
        try(FileChannel channel=FileChannel.open(base.resolve("history.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            FileLock lock;try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){return;}if(lock==null)return;
            try(lock){
                SortedMap<String,Path> files=new TreeMap<>();
                for(Path path:payload.classes.values())files.put(relative(payload.root,path),path);
                Path mediaRoot=payload.root.resolve("media");
                if(Files.isDirectory(mediaRoot))try(var media=Files.walk(mediaRoot)){
                    for(Path path:media.filter(Files::isRegularFile).toList())files.put(relative(payload.root,path),path);
                }
                for(Path path:payload.resources.values())files.put(relative(payload.root,path),path);
                Path manifest=payload.root.resolve("pzopt-files.txt");
                if(Files.isRegularFile(manifest)){
                    files.put("pzopt-files.txt",manifest);
                    for(String line:Files.readAllLines(manifest))if(!line.isBlank()&&!line.startsWith("#")){
                        Path file=member(payload.root,line);files.put(relative(payload.root,file),file);
                    }
                }
                Path receipt=payload.root.resolve("pzoptpreloader-release.properties");if(Files.isRegularFile(receipt))files.put(receipt.getFileName().toString(),receipt);
                Path stage=Files.createTempDirectory(base,".stage-");
                try{
                    Path copied=Files.createDirectory(stage.resolve("payload"));JSONObject hashes=new JSONObject();StringBuilder identity=new StringBuilder();
                    for(var entry:files.entrySet()){
                        byte[] bytes=Files.readAllBytes(entry.getValue());String digest=Compatibility.hash(bytes);
                        if(entry.getKey().endsWith(".class")){
                            String name=entry.getKey().substring(0,entry.getKey().length()-6);
                            String expected=payload.originalClassHashes.get(name);
                            if(expected!=null&&!expected.equals(digest))throw new IOException("Original source changed after injection: "+name);
                        }
                        Path target=member(copied,entry.getKey());Files.createDirectories(target.getParent());Files.write(target,bytes);
                        hashes.put(entry.getKey(),digest);identity.append(entry.getKey()).append('\0').append(digest).append('\n');
                    }
                    String id=Compatibility.hash(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String label=Installer.installedLabel(payload);
                    JSONObject metadata=new JSONObject().put("gameRevision",revision).put("sourceRevision",payload.build.getProperty("revision"))
                        .put("source",payload.root.toString()).put("label",label).put("id",id).put("files",hashes)
                        .put("validation","Successfully injected; no gameplay acceptance implied");
                    Files.writeString(stage.resolve("snapshot.json"),metadata.toString(2)+"\n");
                    // Verify the independent copy, including manifest completeness, before retaining its identity.
                    Payload.verifiedSource(copied,revision);
                    Path destination=base.resolve(id);
                    if(Files.exists(destination))load(base,id,revision);
                    else Files.move(stage,destination);
                    Properties latest=properties(base.resolve("latest.properties"));String old=latest.getProperty("snapshot");
                    if(old!=null&&!old.equals(id))latest.setProperty("previous",old);
                    latest.setProperty("snapshot",id);atomicProperties(base.resolve("latest.properties"),latest);
                    System.out.println("[PZOptPreloader] original source retained after injection: "+label);
                }finally{removeStage(base,stage);}
            }
        }
    }
    private static Previous load(Path base,String id,String revision)throws IOException{
        if(!id.matches("[a-f0-9]{64}"))throw new IOException("Invalid snapshot identity");
        Path folder=base.resolve(id),payload=folder.resolve("payload");JSONObject meta=new JSONObject(Files.readString(folder.resolve("snapshot.json")));
        if(!revision.equals(meta.getString("gameRevision"))||!id.equals(meta.getString("id")))throw new IOException("Snapshot identity mismatch");
        JSONObject hashes=meta.getJSONObject("files");StringBuilder identity=new StringBuilder();
        for(String name:new TreeSet<>(hashes.keySet())){
            Path file=member(payload,name);if(!file.toRealPath().startsWith(payload.toRealPath()))throw new IOException("Snapshot symlink leaves retained source");
            String digest=Compatibility.hash(Files.readAllBytes(file));if(!hashes.getString(name).equals(digest))throw new IOException("Retained original changed: "+name);
            identity.append(name).append('\0').append(digest).append('\n');
        }
        if(!id.equals(Compatibility.hash(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))))throw new IOException("Retained original digest differs");
        Payload.verifiedSource(payload,revision);return new Previous(payload.toRealPath(),id,meta.getString("label"));
    }
    public static Previous previous(Path config,String revision)throws IOException{
        Path base=directory(config,revision);Properties index=properties(base.resolve("latest.properties"));IOException failed=null;
        for(String key:List.of("snapshot","previous")){
            String id=index.getProperty(key);if(id==null)continue;
            try{return load(base,id,revision);}catch(IOException|RuntimeException invalid){failed=new IOException("Retained source unavailable: "+id,invalid);}
        }
        if(failed!=null)throw failed;return null;
    }
    /** Bind fallback to the exact rejected source; a newer compatible input always wins normal discovery. */
    static String rejectedToken(Path source)throws IOException{
        Path root=source.toAbsolutePath().normalize();StringBuilder identity=new StringBuilder(root.toString());
        Path build=root.resolve("pzopt/build-info.properties");
        if(Files.isRegularFile(build)){
            List<Path> files;try(var paths=Files.walk(root)){files=paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".class")||p.equals(build)||p.getFileName().toString().equals("pzopt-files.txt")).sorted().limit(10001).toList();}
            if(files.size()>10000)throw new IOException("Rejected source exceeds file bound");
            long size=0;for(Path file:files){size+=Files.size(file);if(size>256L*1024*1024)throw new IOException("Rejected source exceeds size bound");
                if(!file.toRealPath().startsWith(root.toRealPath()))throw new IOException("Rejected source has an escaping symlink");
                identity.append('\n').append(root.relativize(file)).append(':').append(Compatibility.hash(Files.readAllBytes(file)));
            }
        }else identity.append("\nmissing");
        return Compatibility.hash(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void choose(Path config,String revision,Payload.SourceUnavailable failure,Previous previous)throws IOException{
        if(failure.candidate==null)throw new IOException("No rejected source identity for previous-version selection");
        // Revalidate the exact chosen version before publishing a next-start selection.
        Path base=directory(config,revision);load(base,previous.id,revision);Properties selection=new Properties();
        selection.setProperty("rejected",rejectedToken(failure.candidate));selection.setProperty("snapshot",previous.id);
        atomicProperties(base.resolve("selection.properties"),selection);
    }
    static Path selected(Path config,String revision,Collection<Payload.SourceUnavailable> failures)throws IOException{
        Path base=directory(config,revision);Properties selection=properties(base.resolve("selection.properties"));String wanted=selection.getProperty("rejected"),id=selection.getProperty("snapshot");
        if(wanted==null||id==null)return null;
        for(var failure:failures)if(failure.candidate!=null&&wanted.equals(rejectedToken(failure.candidate)))return load(base,id,revision).root;
        return null;
    }
    /** Preserve physical identity through config junctions even after an installation was moved away. */
    static Path canonicalLocation(Path path)throws IOException{
        Path existing=path.toAbsolutePath().normalize();Deque<Path> missing=new ArrayDeque<>();
        while(!Files.exists(existing)){
            if(existing.getParent()==null)throw new IOException("No existing ancestor for source location");
            missing.addFirst(existing.getFileName());existing=existing.getParent();
        }
        Path result=existing.toRealPath();for(Path part:missing)result=result.resolve(part);return result;
    }
    static boolean configUpdates(Payload payload)throws IOException{return configUpdates(payload,SourcePaths.configDirectory());}
    static boolean configUpdates(Payload payload,Path config)throws IOException{
        Path installation=canonicalLocation(config.resolve("PZ_Optimization"));
        if(payload.root.equals(installation))return true;
        Path base=canonicalLocation(config.resolve("PZOptPreloader/sources"));
        if(!payload.root.startsWith(base))return false;
        JSONObject metadata=new JSONObject(Files.readString(payload.root.getParent().resolve("snapshot.json")));
        Path origin=canonicalLocation(Path.of(metadata.getString("source")));
        return origin.equals(installation);
    }
    public static void ignoreRelease(Path config,String revision,String digest)throws IOException{
        if(!digest.matches("[a-f0-9]{64}"))throw new IOException("Invalid rejected release digest");
        Path path=directory(config,revision).resolve("retained-releases.properties");Properties ignored=properties(path);ignored.setProperty(digest,"keep previous");atomicProperties(path,ignored);
    }
    public static boolean ignoredRelease(Path config,String revision,String digest)throws IOException{
        return properties(directory(config,revision).resolve("retained-releases.properties")).containsKey(digest);
    }
}
