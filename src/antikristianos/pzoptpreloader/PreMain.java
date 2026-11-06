package antikristianos.pzoptpreloader;
public final class PreMain {
    public static void premain(String[] args) {
        try { RuntimeState.install(); WallReflectionRepair.prepareNativeHooks(); OffscreenReflectionRepair.prepareNativeHooks(); }
        catch (Payload.SourceUnavailable error) {
            RuntimeState.status = "source unavailable: " + error.getMessage();
            if(!Installation.recoveryNotice())Installation.unavailable(error);
        }
        catch (Throwable error) {
            if(error instanceof Exception||error instanceof LinkageError)RuntimeState.recordFailure(error);
            RuntimeState.status = "FAILED: " + error;
            System.err.println("[PZOptPreloader] " + RuntimeState.status + "; arrange this preloader before other Java preload mods and restart");
            error.printStackTrace(System.err);
        }
    }
}
