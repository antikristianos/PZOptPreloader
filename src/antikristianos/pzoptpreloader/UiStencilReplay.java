package antikristianos.pzoptpreloader;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import zombie.ui.UIElement;

/** Producer-side companion to a recorded native stencil command span. */
public final class UiStencilReplay {
    public interface Owner { Span pzoptPreloaderStencilSpan(); }
    public static final class Span {
        private int pendingEntry, entry, exit;
        private boolean pending, valid;
    }
    // Resolve once, not once per UI element/frame. Native field and class structure remain unchanged.
    private static final VarHandle LEVEL = levelHandle();
    private UiStencilReplay() { }
    private static VarHandle levelHandle() {
        try {
            return MethodHandles.privateLookupIn(UIElement.class,MethodHandles.lookup())
                .findStaticVarHandle(UIElement.class,"stencilLevel",int.class);
        } catch(ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    public static void begin(Owner owner) {
        Span span=owner.pzoptPreloaderStencilSpan();
        span.pendingEntry=(int)LEVEL.get(); span.pending=true;
    }
    public static void recorded(Owner owner) {
        Span span=owner.pzoptPreloaderStencilSpan();
        span.valid=span.pending;
        if(span.pending) { span.entry=span.pendingEntry; span.exit=(int)LEVEL.get(); }
        span.pending=false;
    }
    public static boolean requiresFresh(Owner owner) {
        Span span=owner.pzoptPreloaderStencilSpan();
        return !span.valid || (int)LEVEL.get()!=span.entry;
    }
    public static void replayed(Owner owner) {
        Span span=owner.pzoptPreloaderStencilSpan();
        if(!span.valid)throw new IllegalStateException("UI stencil replay without a completed recording");
        LEVEL.set(span.exit);
    }
}
