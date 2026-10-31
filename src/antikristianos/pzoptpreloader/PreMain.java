package antikristianos.pzoptpreloader;
public final class PreMain {
    public static void premain(String[] args) {
        try { RuntimeState.install(); }
        catch (Payload.SourceUnavailable error) {
            RuntimeState.status = "source unavailable: " + error.getMessage();
            Installation.unavailable(error);
        }
        catch (Throwable error) {
            RuntimeState.status = "FAILED: " + error;
            System.err.println("[PZOptPreloader] " + RuntimeState.status + "; arrange this preloader before other Java preload mods and restart");
            error.printStackTrace(System.err);
        }
    }
}
