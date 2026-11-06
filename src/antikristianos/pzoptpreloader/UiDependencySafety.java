package antikristianos.pzoptpreloader;

/** Bounded dependency walks. Only logs on an unsafe path fall back to fresh drawing. */
public final class UiDependencySafety {
    public interface LogState {
        boolean pzoptPreloaderUiUnsafe();
        void pzoptPreloaderUiUnsafe(boolean value);
    }
    private static final int MAX_DEPTH=128;
    private static final ThreadLocal<Walk> VALIDATION=ThreadLocal.withInitial(Walk::new);
    private static final ThreadLocal<Walk> COUNTING=ThreadLocal.withInitial(Walk::new);
    private UiDependencySafety() { }

    private static final class Walk {
        final LogState[] path=new LogState[MAX_DEPTH];
        int depth;
        boolean enter(LogState log) {
            boolean repeated=false;
            for(int i=0;i<depth;i++)if(path[i]==log){repeated=true;break;}
            if(repeated||depth==MAX_DEPTH){
                log.pzoptPreloaderUiUnsafe(true);
                for(int i=0;i<depth;i++)path[i].pzoptPreloaderUiUnsafe(true);
                return false;
            }
            path[depth++]=log;
            return true;
        }
        void leave() { path[--depth]=null; }
    }
    public static boolean enter(LogState log,boolean counting) {
        return (counting?COUNTING:VALIDATION).get().enter(log);
    }
    public static void leave(boolean counting) {
        (counting?COUNTING:VALIDATION).get().leave();
    }
}