package antikristianos.pzoptpreloader;

import java.util.*;

/** Own reusable stable merge sorter for upstream call sites of the removed Vanilla sorter. */
public final class LegacySort {
    private Object[] scratch = new Object[0];
    @SuppressWarnings("unchecked")
    public <T> void doSort(Object[] values, Comparator<? super T> comparator, int from, int to) {
        Objects.checkFromToIndex(from, to, values.length);
        int length = to-from;
        if (length < 2) return;
        Comparator<? super T> order = comparator != null ? comparator : (a,b) -> ((Comparable<T>)a).compareTo(b);
        boolean sorted = true;
        for (int i = from+1; i < to; i++) if (order.compare((T)values[i-1], (T)values[i]) > 0) { sorted = false; break; }
        if (sorted) return;
        if (scratch.length < length) scratch = new Object[length];
        for (int width = 1; width < length; width = width > length/2 ? length : width*2) {
            for (int first = from; first < to-width; first += width*2) {
                int middle = first+width, end = Math.min(to,middle+width);
                if (order.compare((T)values[middle-1], (T)values[middle]) <= 0) continue;
                int size = end-first;
                System.arraycopy(values, first, scratch, 0, size);
                int left=0, right=width;
                for (int output=first; output<end; output++) {
                    if (left>=width) values[output]=(T)scratch[right++];
                    else if (right>=size || order.compare((T)scratch[left],(T)scratch[right]) <= 0) values[output]=(T)scratch[left++];
                    else values[output]=(T)scratch[right++];
                }
                Arrays.fill(scratch,0,size,null);
            }
        }
    }
}