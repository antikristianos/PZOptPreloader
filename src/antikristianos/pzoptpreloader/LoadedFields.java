package antikristianos.pzoptpreloader;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Instance-local storage for nullable reference memos added after a class was loaded. */
public final class LoadedFields {
    private static final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private static final Map<Key, Map<String, Object>> values = new HashMap<>();

    private static final class Key extends WeakReference<Object> {
        private final int hash;
        Key(Object owner, ReferenceQueue<Object> queue) {
            super(owner, queue); hash = System.identityHashCode(owner);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            Object owner = get();
            return owner != null && other instanceof Key key && owner == key.get();
        }
    }
    private static void clean() {
        Key key;
        while ((key = (Key) queue.poll()) != null) values.remove(key);
    }
    public static synchronized Object get(Object owner, String field) {
        Objects.requireNonNull(owner);
        clean();
        Map<String, Object> state = values.get(new Key(owner, null));
        return state == null ? null : state.get(field);
    }
    public static synchronized void put(Object owner, Object value, String field) {
        Objects.requireNonNull(owner);
        clean();
        Key lookup = new Key(owner, null);
        Map<String, Object> state = values.get(lookup);
        if (value == null) {
            if (state != null) {
                state.remove(field);
                if (state.isEmpty()) values.remove(lookup);
            }
        } else {
            if (state == null) {
                state = new HashMap<>();
                values.put(new Key(owner, queue), state);
            }
            state.put(field, value);
        }
    }
}
