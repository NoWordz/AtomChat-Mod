package com.atom.chat.diagnostics;

import java.util.Arrays;
import java.util.Locale;

/** Render-thread wall-clock segments. Nested rows overlap and must not be summed. */
public final class FrameSegments {
    public enum Stage { PRE_UI, BLUR, ICONS, DRAW, DOTS, LIST }
    public enum Counter { ICON_RESOURCE, ICON_COLD, BLUR_RESOURCE }
    private static final Recorder GLOBAL = new Recorder(4096);
    private FrameSegments() {}
    /**
     * Begin a scope on the render thread. Every call must be paired with finish,
     * including when the returned ID is zero. Nested scopes are not sampled:
     * current and counters stay disabled until the matching finish(0, ...) call.
     * An outer wall-clock interval can still include time spent in nested work.
     */
    public static long begin(boolean enabled) { return GLOBAL.begin(enabled); }
    public static long current() { return GLOBAL.current(); }
    public static long start(long id) { return id != 0 && GLOBAL.current() == id ? System.nanoTime() : 0; }
    public static void end(long id, Stage stage, long started) {
        if (started != 0) GLOBAL.add(id, stage, Math.max(0L, System.nanoTime() - started));
    }
    public static void count(Counter counter) { GLOBAL.count(counter); }
    public static void finish(long id, boolean success) { GLOBAL.finish(id, success); }
    public static String summary() { return GLOBAL.summary(); }

    static final class Recorder {
        private final long[] ids;
        private final long[][] times;
        private final long[][] counts;
        private final long[] pendingTimes = new long[Stage.values().length];
        private final long[] pendingCounts = new long[Counter.values().length];
        private Thread owner;
        private long sequence, active;
        private int next, size, nestedDepth;
        Recorder(int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("capacity");
            ids = new long[capacity];
            times = new long[Stage.values().length][capacity];
            counts = new long[Counter.values().length][capacity];
        }
        long begin(boolean enabled) {
            if (owner == Thread.currentThread() && active != 0) { nestedDepth++; return 0; }
            if (!enabled || active != 0) return 0;
            owner = Thread.currentThread();
            active = ++sequence;
            Arrays.fill(pendingTimes, -1L);
            Arrays.fill(pendingCounts, 0L);
            return active;
        }
        long current() { return owner == Thread.currentThread() && nestedDepth == 0 ? active : 0; }
        void add(long id, Stage stage, long value) {
            if (id == 0 || id != current() || value < 0) return;
            int i = stage.ordinal();
            pendingTimes[i] = pendingTimes[i] < 0 ? value : pendingTimes[i] + value;
        }
        void count(Counter counter) { if (current() != 0) pendingCounts[counter.ordinal()]++; }
        void finish(long id, boolean success) {
            if (id == 0 && owner == Thread.currentThread() && nestedDepth > 0) { nestedDepth--; return; }
            if (id == 0 || id != current()) return;
            if (success) {
                ids[next] = id;
                for (int i=0;i<times.length;i++) times[i][next] = pendingTimes[i];
                for (int i=0;i<counts.length;i++) counts[i][next] = pendingCounts[i];
                next = (next + 1) % ids.length;
                if (size < ids.length) size++;
            }
            active = 0; owner = null;
        }
        int size() { return size; }
        private int slot(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
            return (next - size + index + ids.length) % ids.length;
        }
        long frameId(int index) { return ids[slot(index)]; }
        long value(int index, Stage stage) { return times[stage.ordinal()][slot(index)]; }
        String summary() {
            StringBuilder result = new StringBuilder("\nsegments wall-clock (nested; do not sum), frames=").append(size);
            for (Stage stage : Stage.values()) {
                long[] sorted = new long[size]; int n=0;
                for (int i=0;i<size;i++) { long v=value(i,stage); if(v>=0) sorted[n++]=v; }
                if (n==0) continue;
                Arrays.sort(sorted,0,n);
                result.append(String.format(Locale.ROOT,"\n  %s n=%d p50=%.3f p95=%.3f ms",stage,n,sorted[(n-1)/2]/1e6,sorted[(int)Math.ceil(n*.95)-1]/1e6));
            }
            for (Counter counter : Counter.values()) {
                long total=0; for(int i=0;i<size;i++) total+=counts[counter.ordinal()][slot(i)];
                result.append("\n  ").append(counter).append('=').append(total);
            }
            return result.toString();
        }
    }
}
