package com.atom.chat.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Bounded, non-blocking GL_TIME_ELAPSED query ring. A frame begins a query; a
 * later frame reads results only when they are available. No glFinish, no
 * blocking result read, no per-frame logging.
 *
 * <p>All GL calls go through {@link Gl} so the queue can be exercised without a
 * live context. The scope of one query is exactly the commands between
 * {@link #beginFrame(long)} and {@link #endFrame()} -- it is a panel GPU
 * interval, not the whole game frame, and CPU render time is not GPU time.
 *
 * <p><strong>Not wired into the render path.</strong> A real offscreen GL test
 * and a user-authorized in-game re-test are still required before any GPU
 * number from this class may be called a game measurement.
 */
public final class GpuTimerQuery implements AutoCloseable {

    /** A query that never becomes available is reclaimed after this long. */
    static final long RESULT_TIMEOUT_NANOS = 2_000_000_000L;

    public interface Gl {
        boolean timerQuerySupported();
        boolean contextStillValid();
        /** True when the host already owns a query on GL_TIME_ELAPSED. */
        boolean otherQueryActive();
        int createQuery();
        void deleteQuery(int query);
        void beginQuery(int query);
        void endQuery();
        boolean resultAvailable(int query);
        long resultNanos(int query);
    }

    /** latencyNanos is measured from endFrame (submission), not from beginFrame. */
    public record Sample(long frameId, long gpuNanos, long latencyNanos) {}

    private final Gl gl;
    private final LongSupplier clock;
    private final int[] queries;
    private final long[] frameIds;
    private final long[] endedAt;
    private int head;
    private int count;
    private int activeQuery;
    private long activeFrameId;
    private long activeEndedAt;
    private boolean closed;
    private long completed;
    private long dropped;
    private long expired;
    private long skipped;
    private long stalled;

    public GpuTimerQuery(Gl gl, int capacity) { this(gl, capacity, System::nanoTime); }

    public GpuTimerQuery(Gl gl, int capacity, LongSupplier clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.gl = Objects.requireNonNull(gl, "gl");
        this.clock = Objects.requireNonNull(clock, "clock");
        queries = new int[capacity];
        frameIds = new long[capacity];
        endedAt = new long[capacity];
    }

    /** Begins a query for one frame. False means the frame is skipped, not stalled. */
    public boolean beginFrame(long frameId) {
        if (closed) return false;
        if (activeQuery != 0) { stalled++; return false; }
        if (!gl.timerQuerySupported() || !gl.contextStillValid() || gl.otherQueryActive()) {
            skipped++;
            return false;
        }
        if (count == queries.length) { dropped++; return false; }
        int query;
        try {
            query = gl.createQuery();
        } catch (RuntimeException | Error failure) {
            skipped++;
            return false;
        }
        if (query <= 0) { skipped++; return false; }
        try {
            gl.beginQuery(query);
        } catch (RuntimeException | Error failure) {
            safeDelete(query);
            skipped++;
            return false;
        }
        activeQuery = query;
        activeFrameId = frameId;
        return true;
    }

    /** Ends the active query and queues it for a later non-blocking read. */
    public void endFrame() {
        if (activeQuery == 0) return;
        int query = activeQuery;
        try {
            gl.endQuery();
            activeEndedAt = clock.getAsLong();
            int slot = (head + count) % queries.length;
            queries[slot] = query;
            frameIds[slot] = activeFrameId;
            endedAt[slot] = activeEndedAt;
            count++;
        } catch (RuntimeException | Error failure) {
            safeDelete(query);
            dropped++;
        } finally {
            // A failed end must never leave the ring permanently stalled.
            activeQuery = 0;
        }
    }

    /** Reads only ready results, in submission order. Never waits for the GPU. */
    public List<Sample> poll() {
        if (closed) return List.of();
        if (!gl.contextStillValid()) { reset(); return List.of(); }
        List<Sample> samples = new ArrayList<>();
        while (count > 0) {
            int slot = head;
            int query = queries[slot];
            boolean available;
            try {
                available = gl.resultAvailable(query);
            } catch (RuntimeException | Error failure) {
                available = true; // reclaim a broken query on the normal path
            }
            if (!available) {
                // A stuck head must not starve ready results behind it.
                if (clock.getAsLong() - endedAt[slot] <= RESULT_TIMEOUT_NANOS) break;
                safeDelete(query);
                recycleHead();
                expired++;
                continue;
            }
            long nanos;
            try {
                nanos = gl.resultNanos(query);
            } catch (RuntimeException | Error failure) {
                safeDelete(query);
                recycleHead();
                expired++;
                continue;
            }
            samples.add(new Sample(frameIds[slot], nanos,
                    Math.max(0L, clock.getAsLong() - endedAt[slot])));
            safeDelete(query);
            recycleHead();
            completed++;
        }
        return samples;
    }

    /** Context rebuild/loss: drop every pending query and count the loss. */
    public void reset() {
        expired += count + (activeQuery != 0 ? 1 : 0);
        releaseAll();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        expired += count + (activeQuery != 0 ? 1 : 0);
        releaseAll();
    }

    private void recycleHead() {
        queries[head] = 0;
        head = (head + 1) % queries.length;
        count--;
    }

    private void safeDelete(int query) {
        try { gl.deleteQuery(query); } catch (RuntimeException | Error ignored) { }
    }

    private void releaseAll() {
        if (activeQuery != 0) {
            safeDelete(activeQuery);
            activeQuery = 0;
        }
        while (count > 0) {
            safeDelete(queries[head]);
            recycleHead();
        }
    }

    public long completed() { return completed; }
    public long dropped() { return dropped; }
    public long expired() { return expired; }
    public long skipped() { return skipped; }
    public long stalled() { return stalled; }
    public int pending() { return count; }
}
