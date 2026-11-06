package antikristianos.pzoptpreloader;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.*;
import java.util.*;

/** Read/write access to the actual B42.22 collection storage, without snapshots or Unsafe. */
public final class ListStorage {
    private static volatile VarHandle elements;
    private ListStorage() { }
    public static synchronized void install(Instrumentation instrumentation) throws ReflectiveOperationException {
        if (elements != null) return;
        Module javaBase = ArrayList.class.getModule(), own = ListStorage.class.getModule();
        if (!javaBase.isOpen("java.util", own)) {
            if (instrumentation == null) throw new IllegalStateException("Instrumentation required for direct ArrayList storage");
            instrumentation.redefineModule(javaBase, Set.of(), Map.of(), Map.of("java.util", Set.of(own)), Set.of(), Map.of());
        }
        elements = MethodHandles.privateLookupIn(ArrayList.class, MethodHandles.lookup())
            .findVarHandle(ArrayList.class, "elementData", Object[].class);
    }
    public static Object[] elements(List<?> list) {
        if (!(list instanceof ArrayList<?> array)) throw new IllegalArgumentException("Expected the actual B42.22 ArrayList storage");
        VarHandle reader = elements;
        if (reader == null) throw new IllegalStateException("ArrayList bridge not prepared before injection");
        return (Object[])reader.get(array);
    }
}