package antikristianos.pzoptpreloader;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Release source boundary: use GitHub/config or custom installations, never Steam Workshop payloads. */
public final class SourcePolicy {
    private SourcePolicy() { }
    public static boolean workshop(Path input) throws IOException {
        Path path=input.toAbsolutePath().normalize(),existing=path;
        List<Path> missing=new ArrayList<>();
        while(existing!=null&&!Files.exists(existing,LinkOption.NOFOLLOW_LINKS)){
            missing.add(0,existing.getFileName());existing=existing.getParent();
        }
        if(existing!=null){path=existing.toRealPath();for(Path part:missing)path=path.resolve(part);}
        for(int i=0;i+2<path.getNameCount();i++)
            if(path.getName(i).toString().equalsIgnoreCase("workshop")
                &&path.getName(i+1).toString().equalsIgnoreCase("content")
                &&path.getName(i+2).toString().equals("108600"))return true;
        return false;
    }
    public static void requireAllowed(Path root) throws IOException {
        boolean blocked=workshop(root);
        Path parent=root.getParent();
        if(!blocked&&parent!=null&&root.getFileName().toString().equals("payload")){
            Path metadata=parent.resolve("snapshot.json");
            if(Files.isRegularFile(metadata)){
                JSONObject state=new JSONObject(Files.readString(metadata));
                String origin=state.optString("source","");
                if(!origin.isBlank())blocked=workshop(Path.of(origin));
            }
        }
        if(blocked)throw new Payload.SourceUnavailable("incompatible",
            "PZ Optimization Workshop sources are disabled in this release. Install the GitHub payload in the config PZ_Optimization folder or a separate custom directory.");
    }
}
