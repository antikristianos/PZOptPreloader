package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import me.zed_0xff.zombie_buddy.Exposer;

/** Main-menu bridge; a missing source never installs classes into the already running game. */
@Exposer.LuaClass(name="PZOptPreloader.Installation")
public final class Installation {
    private static volatile boolean setupNeeded;
    private static volatile boolean dismissed;
    private static volatile boolean cancelled;
    private static volatile String reason = "missing";
    private static volatile Installer.Progress progress = new Installer.Progress("idle", 0, 0, "");
    private static Thread worker;
    private static boolean startupChecked;
    private static volatile SourceHistory.Previous previous;
    private static volatile Payload.SourceUnavailable rejectedSource;
    private static volatile boolean updateMode;
    private static volatile String installedVersion = "";
    private static volatile String offeredVersion = "";
    private static volatile String updateStatus = "not checked";
    private static volatile Installer.Release offeredRelease;
    private Installation() { }

    public static boolean disabled() {
        return !Boolean.parseBoolean(System.getProperty("pzoptpreloader.enabled", "true"))
            || Files.exists(SourcePaths.disabledMarker());
    }

    public static void unavailable(Payload.SourceUnavailable error) {
        setupNeeded = true; reason = error.reason; rejectedSource=error;
        try{previous=SourceHistory.previous(SourcePaths.configDirectory(),revision());}
        catch(Exception unavailable){System.err.println("[PZOptPreloader] previous original source unavailable: "+unavailable);}
        progress = new Installer.Progress("idle", 0, 0, error.getMessage());
        System.err.println("[PZOptPreloader] source unavailable: " + error.getMessage());
    }

    public static synchronized void startup() {
        if (startupChecked || disabled()) return;
        startupChecked = true;
        try {
            Payload installed = RuntimeState.payload != null ? RuntimeState.payload : new Payload(Payload.discover());
            if (RuntimeState.detectServer(System.getProperty("sun.java.command", ""))
                    || !SourceHistory.configUpdates(installed)) {
                updateStatus = "Workshop/custom/server source; update check skipped"; return;
            }
            installedVersion = Installer.installedLabel(installed);
            updateStatus = "checking";
            System.out.println("[PZOptPreloader] checking GitHub updates for config installation " + installedVersion);
            Thread check = new Thread(() -> {
                try {
                    Installer.Release release = Installer.checkUpdate(installed);
                    if(SourceHistory.ignoredRelease(SourcePaths.configDirectory(),revision(),release.digest())){
                        updateStatus="previous version retained for this incompatible release";return;
                    }
                    if (Installer.isNewer(installed, release)) {
                        offerUpdate(release);
                        if (Files.isDirectory(SourcePaths.pendingDirectory())) {
                            try {
                                Payload pending = new Payload(Payload.verifiedSource(SourcePaths.pendingDirectory(),revision()));
                                if (!Installer.isNewer(pending, release)) progress = new Installer.Progress("complete", 1, 1, SourcePaths.pendingDirectory().toString());
                            } catch (IOException ignored) { }
                        }
                    } else {
                        updateStatus = "current";
                        System.out.println("[PZOptPreloader] config installation is current: " + installedVersion);
                    }
                } catch (Exception error) {
                    updateStatus = "check failed: " + error;
                    System.err.println("[PZOptPreloader] update " + updateStatus + "; installed version retained");
                }
            }, "PZOptPreloader-update-check");
            check.setDaemon(true); check.start();
        }
        catch (Payload.SourceUnavailable error) { unavailable(error); }
        catch (Exception error) { System.err.println("[PZOptPreloader] source check: " + error); }
    }

    static void offerUpdate(Installer.Release release) {
        offeredRelease = release; offeredVersion = release.tag();
        updateMode = true; reason = "update";
        progress = new Installer.Progress("update", 0, 0, "");
        setupNeeded = true; updateStatus = "available";
        System.out.println("[PZOptPreloader] update available: " + release.tag());
    }

    public static boolean needsSetup() {
        return setupNeeded && !dismissed && !disabled() && !RuntimeState.detectServer(System.getProperty("sun.java.command", ""));
    }
    public static String state() { return progress.phase(); }
    public static String issue() { return reason; }
    public static String detail() { return progress.detail(); }
    public static double completed() { return progress.completed(); }
    public static double total() { return progress.total(); }
    public static String target() { return (updateMode ? SourcePaths.pendingDirectory() : SourcePaths.installationDirectory()).toString(); }
    public static boolean isUpdate() { return updateMode; }
    public static String installedVersion() { return installedVersion; }
    public static String offeredVersion() { return offeredVersion; }
    public static String updateStatus() { return updateStatus; }
    public static void dismiss() { dismissed = true; }
    public static void dismissUpdate() { if (updateMode && !busy()) dismiss(); }
    public static synchronized void cancelDownload() {
        if (progress.phase().equals("publish") || progress.phase().equals("complete")) return;
        cancelled = true;
        if (worker != null) worker.interrupt();
        progress = new Installer.Progress("cancelled", 0, 0, "");
    }
    static synchronized void reportProgress(Installer.Progress next) {
        if (cancelled) throw new java.util.concurrent.CancellationException("Installation cancelled before publication");
        progress = next;
    }
    /** Fixed upstream URL: Vanilla's Lua openUrl deliberately rejects non-Indie-Stone URLs. */
    public static boolean openReleases() { return browseReleases(zombie.network.DesktopBrowser::openURL); }
    static boolean browseReleases(java.util.function.Predicate<String> opener) {
        try {
            boolean opened = opener.test(Installer.RELEASES_URL);
            if (!opened) System.err.println("[PZOptPreloader] browser did not open " + Installer.RELEASES_URL);
            return opened;
        } catch (RuntimeException error) {
            System.err.println("[PZOptPreloader] cannot open releases page: " + error);
            return false;
        }
    }
    private static String revision()throws ReflectiveOperationException{
        return String.valueOf(Class.forName("zombie.GitVersion").getField("REVISION").get(null));
    }
    public static boolean canKeepPrevious(){
        return !busy()&&!progress.phase().equals("retained")&&!progress.phase().equals("rollback")
            && ((updateMode&&offeredRelease!=null&&RuntimeState.payload!=null&&progress.phase().equals("failed")
                && (reason.equals("incompatible")||reason.equals("invalid"))) || (previous!=null&&rejectedSource!=null));
    }
    public static String previousVersion(){return updateMode?installedVersion:previous==null?"":previous.label();}
    public static synchronized boolean keepPrevious(){
        if(!canKeepPrevious()||disabled())return false;
        try{
            if(updateMode){
                SourceHistory.ignoreRelease(SourcePaths.configDirectory(),revision(),offeredRelease.digest());
                progress=new Installer.Progress("retained",0,0,"");updateStatus="previous retained";
            }else{
                SourceHistory.choose(SourcePaths.configDirectory(),revision(),rejectedSource,previous);
                progress=new Installer.Progress("rollback",0,0,"");
            }
            return true;
        }catch(Exception error){reason="failed";progress=new Installer.Progress("failed",0,0,error.toString());return false;}
    }
    public static String releasesUrl() { return Installer.RELEASES_URL; }
    public static synchronized boolean busy() { return worker != null && worker.isAlive(); }

    public static synchronized boolean startDownload() {
        if (busy() || disabled() || !needsSetup() || (RuntimeState.payload != null && !updateMode)) return false;
        cancelled = false;
        progress = new Installer.Progress("checking", 0, 0, "");
        worker = new Thread(() -> {
            try {
                String revision = String.valueOf(Class.forName("zombie.GitVersion").getField("REVISION").get(null));
                java.util.function.Consumer<Installer.Progress> report = Installation::reportProgress;
                if (updateMode) Installer.installUpdate(SourcePaths.pendingDirectory(), revision, offeredRelease, report, () -> cancelled);
                else Installer.install(SourcePaths.installationDirectory(), revision, report, () -> cancelled);
            } catch (Exception error) {
                if (!cancelled) {
                    reason = error instanceof Payload.SourceUnavailable source ? source.reason : error instanceof Installer.Failure failure ? failure.reason : "failed";
                    progress = new Installer.Progress("failed", 0, 0, error.getMessage() == null ? error.toString() : error.getMessage());
                    System.err.println("[PZOptPreloader] install failed: " + error);
                }
            }
        }, "PZOptPreloader-install");
        worker.setDaemon(true); worker.start();
        return true;
    }

    public static synchronized boolean disable() {
        try {
            Path marker = SourcePaths.disabledMarker();
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "Disabled by the PZ Optimization Preloader setup dialog.\n");
            cancelled = true;
            if (worker != null) worker.interrupt();
            setupNeeded = false;
            progress = new Installer.Progress("disabled", 0, 0, "");
            return true;
        } catch (IOException error) {
            reason = "failed"; progress = new Installer.Progress("failed", 0, 0, error.toString());
            return false;
        }
    }
}
