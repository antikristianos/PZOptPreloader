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
    public static int getInt(Object owner,String field) { Object v=get(owner,field); return v==null?0:(Integer)v; }
    public static void putInt(Object owner,int value,String field) { put(owner,value,field); }
    public static boolean getBoolean(Object owner,String field) { Object v=get(owner,field); return v!=null&&(Boolean)v; }
    public static void putBoolean(Object owner,boolean value,String field) { put(owner,value,field); }
    public static byte getByte(Object owner,String field) { Object v=get(owner,field); return v==null?0:(Byte)v; }
    public static void putByte(Object owner,byte value,String field) { put(owner,value,field); }
    public static short getShort(Object owner,String field) { Object v=get(owner,field); return v==null?0:(Short)v; }
    public static void putShort(Object owner,short value,String field) { put(owner,value,field); }
    public static char getChar(Object owner,String field) { Object v=get(owner,field); return v==null?0:(Character)v; }
    public static void putChar(Object owner,char value,String field) { put(owner,value,field); }
    public static long getLong(Object owner,String field) { Object v=get(owner,field); return v==null?0L:(Long)v; }
    public static void putLong(Object owner,long value,String field) { put(owner,value,field); }
    public static float getFloat(Object owner,String field) { Object v=get(owner,field); return v==null?0F:(Float)v; }
    public static void putFloat(Object owner,float value,String field) { put(owner,value,field); }
    public static double getDouble(Object owner,String field) { Object v=get(owner,field); return v==null?0D:(Double)v; }
    public static void putDouble(Object owner,double value,String field) { put(owner,value,field); }
}
