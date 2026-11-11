package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Additional reviewed method identities; existing source identities remain valid. */
public final class RepairProfiles {
    private static JSONObject update;
    private RepairProfiles() { }
    public static synchronized boolean matches(String previous,String actual) {
        if (previous.equals(actual)) return true;
        if (actual==null) return false;
        if (update==null) try (InputStream input=RepairProfiles.class.getResourceAsStream("repairs-154126d0-42.22.json")) {
            if (input==null) return false;
            update=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        } catch (IOException error) { throw new IllegalStateException("Repair profile unavailable",error); }
        return actual.equals(update.optString(previous))||NativeSource.repairPin(previous,actual);
    }
}
