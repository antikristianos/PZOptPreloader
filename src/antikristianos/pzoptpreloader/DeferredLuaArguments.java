package antikristianos.pzoptpreloader;

/** The event dispatcher owns and clears its argument array after dispatch returns. */
public final class DeferredLuaArguments {
    private DeferredLuaArguments() { }
    public static Object[] snapshot(Object[] arguments) {
        return arguments == null ? null : arguments.clone();
    }
}
