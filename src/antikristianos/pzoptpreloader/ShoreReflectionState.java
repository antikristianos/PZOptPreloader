package antikristianos.pzoptpreloader;

/** Game-thread surface scope. Keep next-frame reflection writes behind every current-frame shore read. */
public final class ShoreReflectionState {
    private static final ThreadLocal<Pending> LOCAL=ThreadLocal.withInitial(Pending::new);
    private static final class Pending { boolean afterWater; }
    private ShoreReflectionState() { }
    public static void begin() { LOCAL.get().afterWater=false; }
    public static void defer() { LOCAL.get().afterWater=true; }
    public static boolean consume() {
        Pending pending=LOCAL.get();boolean value=pending.afterWater;pending.afterWater=false;return value;
    }
    public static void abort() { LOCAL.get().afterWater=false; }
}