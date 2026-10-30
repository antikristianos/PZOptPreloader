package antikristianos.pzoptpreloader;

import java.io.*;
import java.nio.file.*;
import me.zed_0xff.zombie_buddy.Exposer;

/** Main-menu bridge; a missing source never installs classes into the already running game. */
@Exposer.LuaClass(name="PZOptPreloader.Installation")
public final class Installation {
    private static volatile boolean setupNeeded;
    private static volatile boolean cancelled;
    private static volatile String reason = "missing";
    private static volatile Installer.Progress progress = new Installer.Progress("idle", 0, 0, "");
    private static Thread worker;
    private static boolean startupChecked;
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
        setupNeeded = true; reason = error.reason;
        progress = new Installer.Progress("idle", 0, 0, error.getMessage());
        System.err.println("[PZOptPreloader] source unavailable: " + error.getMessage());
    }

    public static synchronized void startup() {
        if (startupChecked || disabled()) return;
        startupChecked = true;
        try {
            Payload installed = RuntimeState.payload != null ? RuntimeState.payload : new Payload(Payload.discover());
            if (RuntimeState.detectServer(System.getProperty("sun.java.command", ""))
                    || !Files.isDirectory(SourcePaths.installationDirectory())
                    || !installed.root.equals(SourcePaths.installationDirectory().toRealPath())) {
                updateStatus = "Workshop/custom/server source; update check skipped"; return;
            }
            installedVersion = Installer.installedLabel(installed);
            updateStatus = "checking";
            System.out.println("[PZOptPreloader] checking GitHub updates for config installation " + installedVersion);
            Thread check = new Thread(() -> {
                try {
                    Installer.Release release = Installer.checkUpdate(installed);
                    if (Installer.isNewer(installed, release)) {
                        offerUpdate(release);
                        if (Files.isDirectory(SourcePaths.pendingDirectory())) {
                            try {
                                Payload pending = new Payload(Payload.verifiedSource(SourcePaths.pendingDirectory(), installed.build.getProperty("revision")));
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
        return setupNeeded && !disabled() && !RuntimeState.detectServer(System.getProperty("sun.java.command", ""));
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
    public static void dismissUpdate() { if (updateMode && !busy()) setupNeeded = false; }
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
                    reason = error instanceof Installer.Failure failure ? failure.reason : "failed";
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
