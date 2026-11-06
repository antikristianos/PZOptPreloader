package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persisted next-process recovery; never retries injection in the current JVM. */
public final class StartupFallback {
    /** Only an explicitly identified PZOpt recipe/input refusal identifies its source as incompatible. */
    public static final class SourceFailure extends IOException {
        public SourceFailure(String message,Throwable cause){super(message,cause);}
    }
    public record Attempt(Path config,String revision,Path root,String identity,String token,String digest) { }
    public record Notice(String phase,String detail,String previous) { }
    private static volatile Notice notice;
    private static volatile String noticeId;
    private StartupFallback() { }
    static String loaderContext() throws IOException {
        try {
            Path location=Path.of(StartupFallback.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if(Files.isRegularFile(location))return Compatibility.hash(Files.readAllBytes(location));
            // Exploded test classes use precisely the recovery implementation, never a mutable version label.
            StringBuilder code=new StringBuilder();
            for(Class<?> type:List.of(StartupFallback.class,RuntimeState.class,Payload.class,SourceHistory.class))
                try(InputStream in=type.getResourceAsStream(type.getSimpleName()+".class")){if(in==null)throw new IOException("Missing recovery code identity");code.append(Compatibility.hash(in.readAllBytes()));}
            return Compatibility.hash(code.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }catch(java.net.URISyntaxException error){throw new IOException(error);}
    }
    static Attempt capture(Path config,String revision,Path candidate)throws IOException{
        Path root=Payload.normalizeSource(candidate);String identity=SourceHistory.runtimeIdentity(root);
        String token=Compatibility.hash((loaderContext()+"\n"+identity).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Properties receipt=SourceHistory.properties(root.resolve("pzoptpreloader-release.properties"));
        String digest=receipt.getProperty("sha256","");if(!digest.matches("[a-f0-9]{64}"))digest="";
        return new Attempt(config,revision,root,identity,token,digest);
    }
    private static Path record(Attempt attempt)throws IOException{return SourceHistory.directory(attempt.config,attempt.revision).resolve("rejected-"+attempt.token+".properties");}
    public static Path selected(Path config, String revision, Path candidate) throws IOException {
        Attempt attempt;try{attempt=capture(config,revision,candidate);}catch(IOException error){return null;}
        Properties rejected=SourceHistory.properties(record(attempt));if(rejected.isEmpty())return null;
        pendingNotice(config,revision,rejected,"failed",rejected.getProperty("label",""));
        String id=rejected.getProperty("snapshot","");
        try{
            SourceHistory.Previous previous=SourceHistory.load(SourceHistory.directory(config,revision),id,revision);
            if(SourceHistory.runtimeIdentity(previous.root()).equals(attempt.identity))throw new IOException("Retained source is the rejected runtime");
            if(quarantined(config,revision,previous.root()))throw new IOException("Retained source was also quarantined");
            pendingNotice(config,revision,rejected,"retained",previous.label());
            return previous.root();
        }catch(IOException|RuntimeException invalid){throw new Payload.SourceUnavailable("incompatible","Quarantined source; verified different previous version unavailable: "+invalid,attempt.root,invalid);}
    }
    static boolean quarantined(Path config,String revision,Path candidate)throws IOException{
        Attempt attempt;try{attempt=capture(config,revision,candidate);}catch(IOException error){return false;}return Files.isRegularFile(record(attempt));
    }
    static boolean reject(Attempt attempt,Throwable error)throws IOException{
        Properties existing=SourceHistory.properties(record(attempt));
        if(!existing.isEmpty())return !existing.getProperty("snapshot","").isEmpty();
        SourceHistory.Previous previous=SourceHistory.previousDifferent(attempt.config,attempt.revision,attempt.identity);
        Properties data=new Properties();data.setProperty("token",attempt.token);data.setProperty("context",loaderContext());
        data.setProperty("error",describe(error));data.setProperty("source",attempt.root.toString());data.setProperty("identity",attempt.identity);data.setProperty("acknowledged","false");
        data.setProperty("snapshot",previous==null?"":previous.id());data.setProperty("label",previous==null?"":previous.label());
        SourceHistory.atomicProperties(record(attempt),data);
        if(!attempt.digest.isEmpty())SourceHistory.ignoreAutomaticRelease(attempt.config,attempt.revision,attempt.digest);
        Path pending=SourceHistory.directory(attempt.config,attempt.revision).resolve("notice.properties");
        SourceHistory.atomicProperties(pending,data);noticeId=attempt.token;
        notice=new Notice(previous==null?"failed":"rollback",describe(error)+"\nSource: "+attempt.root,previous==null?"":previous.label());
        return previous!=null;
    }
    static void rejectedPending(Path config,String revision,Path pending,Throwable error)throws IOException{
        Attempt attempt=capture(config,revision,pending);reject(attempt,error);
        Properties data=SourceHistory.properties(record(attempt));Path active=config.resolve("PZ_Optimization");
        if(!data.containsKey("activeIdentity")&&Files.isDirectory(active)){
            data.setProperty("activeIdentity",SourceHistory.runtimeIdentity(active));SourceHistory.atomicProperties(record(attempt),data);
            SourceHistory.atomicProperties(SourceHistory.directory(config,revision).resolve("notice.properties"),data);
        }
    }
    private static void pendingNotice(Path config,String revision,Properties rejected,String phase,String label)throws IOException{
        if(Boolean.parseBoolean(rejected.getProperty("acknowledged","false"))||!loaderContext().equals(rejected.getProperty("context")))return;
        noticeId=rejected.getProperty("token");notice=new Notice(phase,rejected.getProperty("error","")+"\nSource: "+rejected.getProperty("source",""),label);
    }
    static Notice notice(){return notice;}
    static void active(Payload payload,String revision)throws IOException{
        Path config=SourcePaths.configDirectory();Properties pending=SourceHistory.properties(SourceHistory.directory(config,revision).resolve("notice.properties"));
        if(pending.isEmpty()||!loaderContext().equals(pending.getProperty("context")))return;
        String identity=SourceHistory.runtimeIdentity(payload.root),expected=pending.getProperty("activeIdentity","");
        String snapshot=pending.getProperty("snapshot","");
        if(expected.isEmpty()&&!snapshot.isEmpty())try{expected=SourceHistory.runtimeIdentity(SourceHistory.load(SourceHistory.directory(config,revision),snapshot,revision).root());}catch(IOException|RuntimeException invalid){return;}
        if(!identity.equals(expected))return;
        noticeId=pending.getProperty("token");notice=new Notice("retained",pending.getProperty("error","")+"\nSource: "+pending.getProperty("source",""),Installer.installedLabel(payload));
    }
    static void rejectedDownload(Path config,String revision,String digest,Payload active,Throwable error)throws IOException{
        SourceHistory.ignoreAutomaticRelease(config,revision,digest);
        noticeId=null;
        notice=new Notice("retained",describe(error),Installer.installedLabel(active));
    }
    static void acknowledge()throws IOException{
        acknowledge(SourcePaths.configDirectory(),revision());
    }
    static void acknowledge(Path config,String revision)throws IOException{
        if(noticeId==null)return;Path path=SourceHistory.directory(config,revision).resolve("notice.properties");
        Properties current=SourceHistory.properties(path);if(noticeId.equals(current.getProperty("token")))Files.deleteIfExists(path);
        Path rejected=path.getParent().resolve("rejected-"+noticeId+".properties");Properties record=SourceHistory.properties(rejected);
        if(!record.isEmpty()){record.setProperty("acknowledged","true");SourceHistory.atomicProperties(rejected,record);}
        notice=null;noticeId=null;
    }
    static String describe(Throwable error){
        StringBuilder detail=new StringBuilder();Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int depth=0;error!=null&&depth<8&&seen.add(error);depth++,error=error.getCause()){
            if(depth>0)detail.append("\nCaused by: ");detail.append(error);
            if(detail.length()>8192)return detail.substring(0,8192);
        }
        return detail.toString();
    }
    static String revision()throws IOException{
        try{return String.valueOf(Class.forName("zombie.GitVersion").getField("REVISION").get(null));}
        catch(ReflectiveOperationException error){throw new IOException("Actual game revision unavailable",error);}
    }
    static boolean environmentFailure(Throwable error){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(;error!=null&&seen.add(error);error=error.getCause())if(error instanceof VirtualMachineError||error instanceof ThreadDeath
            ||error instanceof java.nio.file.FileSystemException||error instanceof java.nio.file.ReadOnlyFileSystemException||error instanceof SecurityException)return true;
        return false;
    }
    static boolean sourceFailure(Throwable error){
        if(environmentFailure(error))return false;Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(;error!=null&&seen.add(error);error=error.getCause()){
            if(error instanceof SourceFailure)return true;
        }
        return false;
    }
}
