package com.atom.chat.diagnostics;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Render-thread wall-clock segments. Nested rows overlap and must not be summed. */
public final class FrameSegments {
    public enum Stage { PRE_UI, BLUR, ICONS, DRAW, DOTS, LIST, BRIDGE_ENTER, BRIDGE_EXIT }
    public enum Counter { ICON_RESOURCE, ICON_COLD, BLUR_RESOURCE }
    private static final Recorder GLOBAL = new Recorder(4096);
    private static final AtomicLong ACTION_IDS = new AtomicLong();
    // P1: one independent GL_TIME_ELAPSED ring (capacity 4, bounded) plus a
    // bounded rolling window of completed GPU intervals. No GL call happens
    // at class init; without a live context the ring simply skips frames.
    private static final GpuTimerQuery GPU = new GpuTimerQuery(new GlGpuTimer(), 4);
    private static final int GPU_CAPACITY = 4096;
    private static final long[] GPU_NANOS = new long[GPU_CAPACITY];
    private static int gpuNext, gpuSize;
    private FrameSegments() {}
    /**
     * Begin a scope on the render thread. Every call must be paired with finish,
     * including when the returned ID is zero. Nested scopes are not sampled:
     * current and counters stay disabled until the matching finish(0, ...) call.
     * An outer wall-clock interval can still include time spent in nested work.
     */
    public static long begin(boolean enabled) {
        long id = GLOBAL.begin(enabled);
        if (id != 0) {
            pollGpu();            // next-frame, non-blocking read of earlier results
            GPU.beginFrame(id);   // glBeginQuery around this frame's GL commands
        }
        return id;
    }
    public static long current() { return GLOBAL.current(); }
    public static long start(long id) { return id != 0 && GLOBAL.current() == id ? System.nanoTime() : 0; }
    public static void end(long id, Stage stage, long started) {
        if (started != 0) GLOBAL.add(id, stage, Math.max(0L, System.nanoTime() - started));
    }
    public static void record(long id, Stage stage, long nanos) { GLOBAL.add(id, stage, nanos); }
    public static void count(Counter counter) { GLOBAL.count(counter); }
    public static void finish(long id, boolean success) {
        if (id != 0) GPU.endFrame();   // glEndQuery; never leave a query dangling
        GLOBAL.finish(id, success);
    }
    public static String summary() {
        return GLOBAL.summary() + gpuSummary() + FrameProfile.pageSummary();
    }

    private static void pollGpu() {
        for (GpuTimerQuery.Sample sample : GPU.poll()) {
            GPU_NANOS[gpuNext] = sample.gpuNanos();
            gpuNext = (gpuNext + 1) % GPU_CAPACITY;
            if (gpuSize < GPU_CAPACITY) gpuSize++;
        }
    }

    /** Bounded, stable, machine-parseable GPU diagnostic row (debug=on). */
    public static String gpuSummary() {
        StringBuilder sb = new StringBuilder("\n  gpu_panel n=").append(gpuSize);
        if (gpuSize > 0) {
            long[] sorted = Arrays.copyOf(GPU_NANOS, gpuSize);
            Arrays.sort(sorted);
            sb.append(String.format(Locale.ROOT, " p50=%.4f p95=%.4f ms",
                    sorted[(gpuSize - 1) / 2] / 1e6,
                    sorted[(int) Math.ceil(gpuSize * 0.95) - 1] / 1e6));
        }
        sb.append(" completed=").append(GPU.completed())
          .append(" dropped=").append(GPU.dropped())
          .append(" expired=").append(GPU.expired())
          .append(" skipped=").append(GPU.skipped())
          .append(" stalled=").append(GPU.stalled())
          .append(" pending=").append(GPU.pending());
        return sb.toString();
    }

    /** Test / session isolation for the GPU ring and its rolling window. */
    public static void resetDiagnostics() {
        GPU.reset();
        gpuNext = 0;
        gpuSize = 0;
    }

    /** Outside a sampled draw, frameId is zero; use the monotonic stamp to align the action. */
    public static void navigation(String action, String page) {
        FrameProfile.page(page);   // P1: pageKey / transition phase / firstOpen (gated inside by enabled)
        if (!FrameProfile.enabled() || !NavigationEvent.TYPE.isEnabled()) return;
        var event = new NavigationEvent();
        event.frameId = current();
        event.action = action;
        event.page = page;
        event.monotonicNanos = System.nanoTime();
        event.actionId = ACTION_IDS.incrementAndGet();
        event.commit();
    }

    @jdk.jfr.Name("com.atomchat.Navigation")
    @jdk.jfr.Label("AtomChat navigation")
    @jdk.jfr.Category("AtomChat")
    @jdk.jfr.StackTrace(false)
    static final class NavigationEvent extends jdk.jfr.Event {
        static final jdk.jfr.EventType TYPE = jdk.jfr.EventType.getEventType(NavigationEvent.class);
        long frameId;
        String action;
        String page;
        long monotonicNanos;
        long actionId;
    }

    /**
     * One complete panel frame. Every recorded stage is carried on the same
     * frameId; -1 marks a stage that was not sampled, never "zero work".
     * PRE_UI/BLUR/ICONS and DRAW/DOTS/LIST remain nested and must not be summed.
     */
    @jdk.jfr.Name("com.atomchat.PanelSegments")
    @jdk.jfr.Label("AtomChat panel segments")
    @jdk.jfr.Category("AtomChat")
    @jdk.jfr.StackTrace(false)
    static final class PanelSegmentsEvent extends jdk.jfr.Event {
        static final jdk.jfr.EventType TYPE = jdk.jfr.EventType.getEventType(PanelSegmentsEvent.class);
        long frameId;
        String surface;
        long startedAtNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long panelNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long preUiNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long blurNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long iconsNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long bridgeEnterNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long drawNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long dotsNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long listNanos;
        @jdk.jfr.Timespan(jdk.jfr.Timespan.NANOSECONDS) long bridgeExitNanos;
        long iconResource;
        long iconCold;
        long blurResource;
        int depth;
        boolean success;
    }

    static final class Recorder {
        private final long[] ids;
        private final long[][] times;
        private final long[][] counts;
        private final long[] pendingTimes = new long[Stage.values().length];
        private final long[] pendingCounts = new long[Counter.values().length];
        private Thread owner;
        private long sequence, active, activeStarted;
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
            activeStarted = System.nanoTime();
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
                long finishedAt = System.nanoTime();
                if (PanelSegmentsEvent.TYPE.isEnabled()) {
                    var event = new PanelSegmentsEvent();
                    event.frameId = id;
                    event.surface = FrameProfile.currentSurface().name();
                    event.startedAtNanos = activeStarted;
                    event.panelNanos = Math.max(0L, finishedAt - activeStarted);
                    event.preUiNanos = pendingTimes[Stage.PRE_UI.ordinal()];
                    event.blurNanos = pendingTimes[Stage.BLUR.ordinal()];
                    event.iconsNanos = pendingTimes[Stage.ICONS.ordinal()];
                    event.bridgeEnterNanos = pendingTimes[Stage.BRIDGE_ENTER.ordinal()];
                    event.drawNanos = pendingTimes[Stage.DRAW.ordinal()];
                    event.dotsNanos = pendingTimes[Stage.DOTS.ordinal()];
                    event.listNanos = pendingTimes[Stage.LIST.ordinal()];
                    event.bridgeExitNanos = pendingTimes[Stage.BRIDGE_EXIT.ordinal()];
                    event.iconResource = pendingCounts[Counter.ICON_RESOURCE.ordinal()];
                    event.iconCold = pendingCounts[Counter.ICON_COLD.ordinal()];
                    event.blurResource = pendingCounts[Counter.BLUR_RESOURCE.ordinal()];
                    event.depth = nestedDepth;
                    event.success = true;
                    event.commit();
                }
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
