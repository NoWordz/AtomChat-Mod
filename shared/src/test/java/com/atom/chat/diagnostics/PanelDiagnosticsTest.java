package com.atom.chat.diagnostics;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Failing-first coverage for the per-frame panel diagnosis events and the GPU query ring. */
class PanelDiagnosticsTest {

    private static List<RecordedEvent> events(Path file, String name) throws Exception {
        return RecordingFile.readAllEvents(file).stream()
                .filter(e -> e.getEventType().getName().equals(name)).toList();
    }

    private static Recording recording(String... names) {
        var rec = new Recording();
        for (String name : names) rec.enable(name).withThreshold(Duration.ZERO);
        rec.start();
        return rec;
    }

    @Test
    void panelSegmentsCarryEveryStageOnOneFrameId(@TempDir Path dir) throws Exception {
        FrameProfile.reset();
        FrameProfile.setEnabled(true);
        try (var rec = recording("com.atomchat.PanelSegments", "com.atomchat.UiFrame")) {
            long id = FrameSegments.begin(true);
            FrameSegments.record(id, FrameSegments.Stage.PRE_UI, 100);
            FrameSegments.record(id, FrameSegments.Stage.BLUR, 20);
            FrameSegments.record(id, FrameSegments.Stage.ICONS, 30);
            FrameSegments.record(id, FrameSegments.Stage.BRIDGE_ENTER, 40);
            FrameSegments.record(id, FrameSegments.Stage.DRAW, 50);
            FrameSegments.record(id, FrameSegments.Stage.DOTS, 10);
            FrameSegments.record(id, FrameSegments.Stage.LIST, 15);
            FrameSegments.record(id, FrameSegments.Stage.BRIDGE_EXIT, 60);
            FrameSegments.finish(id, true);
            FrameProfile.frame(System.nanoTime() - 1_000_000, -1, id);
            rec.stop();
            Path file = dir.resolve("stages.jfr");
            rec.dump(file);
            var seg = events(file, "com.atomchat.PanelSegments");
            var ui = events(file, "com.atomchat.UiFrame");
            assertEquals(1, seg.size());
            var e = seg.get(0);
            assertTrue(id > 0);
            assertEquals(id, e.getLong("frameId"));
            assertEquals(id, ui.get(0).getLong("frameId"), "both events pair on one nonzero frameId");
            assertEquals(100, e.getLong("preUiNanos"));
            assertEquals(20, e.getLong("blurNanos"));
            assertEquals(30, e.getLong("iconsNanos"));
            assertEquals(40, e.getLong("bridgeEnterNanos"));
            assertEquals(50, e.getLong("drawNanos"));
            assertEquals(10, e.getLong("dotsNanos"));
            assertEquals(15, e.getLong("listNanos"));
            assertEquals(60, e.getLong("bridgeExitNanos"));
            assertTrue(e.getLong("panelNanos") >= e.getLong("drawNanos"),
                    "the outer wall-clock interval must cover the nested draw");
            assertEquals(0, e.getInt("depth"), "nested rows are not extra frames");
            assertTrue(e.getBoolean("success"));
        } finally {
            FrameProfile.setEnabled(false);
            FrameProfile.reset();
        }
    }

    @Test
    void missingStageIsExplicitMinusOneNotZero(@TempDir Path dir) throws Exception {
        FrameProfile.setEnabled(true);
        try (var rec = recording("com.atomchat.PanelSegments")) {
            long id = FrameSegments.begin(true);
            FrameSegments.record(id, FrameSegments.Stage.DRAW, 7);
            FrameSegments.finish(id, true);
            rec.stop();
            Path file = dir.resolve("partial.jfr");
            rec.dump(file);
            var e = events(file, "com.atomchat.PanelSegments").get(0);
            assertEquals(7, e.getLong("drawNanos"));
            assertEquals(-1L, e.getLong("bridgeEnterNanos"), "untimed stage must not look like zero work");
            assertEquals(-1L, e.getLong("dotsNanos"));
        } finally {
            FrameProfile.setEnabled(false);
            FrameProfile.reset();
        }
    }

    @Test
    void failedFrameEmitsNothingAndDoesNotShiftTheNextId(@TempDir Path dir) throws Exception {
        FrameProfile.setEnabled(true);
        try (var rec = recording("com.atomchat.PanelSegments", "com.atomchat.UiFrame")) {
            // Mirrors the real SkiaGraphics path: a throwing renderer propagates out of
            // the try/finally, so FrameProfile.frame() after it is never reached.
            long failed = FrameSegments.begin(true);
            FrameSegments.record(failed, FrameSegments.Stage.DRAW, 999);
            FrameSegments.finish(failed, false);
            long good = FrameSegments.begin(true);
            FrameSegments.record(good, FrameSegments.Stage.DRAW, 11);
            FrameSegments.finish(good, true);
            FrameProfile.frame(System.nanoTime() - 1_000_000, -1, good);
            rec.stop();
            Path file = dir.resolve("failure.jfr");
            rec.dump(file);
            var seg = events(file, "com.atomchat.PanelSegments");
            var ui = events(file, "com.atomchat.UiFrame");
            assertEquals(1, seg.size(), "a failed segment must not emit a PanelSegments event");
            assertEquals(good, seg.get(0).getLong("frameId"));
            assertEquals(11, seg.get(0).getLong("drawNanos"), "failed frame data must not leak forward");
            assertTrue(good > failed);
            assertEquals(1, ui.size(), "the failed frame never reaches FrameProfile.frame");
            assertEquals(good, ui.get(0).getLong("frameId"));
        } finally {
            FrameProfile.setEnabled(false);
            FrameProfile.reset();
        }
    }

    @Test
    void navigationOutsideFrameHasZeroIdAndMonotonicActionId(@TempDir Path dir) throws Exception {
        FrameProfile.setEnabled(true);
        try (var rec = recording("com.atomchat.Navigation")) {
            FrameSegments.navigation("push", "WORLD_CHAT");
            rec.stop();
            Path file = dir.resolve("nav.jfr");
            rec.dump(file);
            var nav = events(file, "com.atomchat.Navigation");
            assertEquals(1, nav.size());
            assertEquals(0L, nav.get(0).getLong("frameId"));
            assertEquals("push", nav.get(0).getString("action"));
            assertTrue(nav.get(0).getLong("actionId") > 0);
            assertDoesNotThrow(() -> nav.get(0).getLong("monotonicNanos"),
                    "nanoTime may be negative; only field presence is asserted");
        } finally {
            FrameProfile.setEnabled(false);
            FrameProfile.reset();
        }
    }

    @Test
    void screenLifecycleRecordsStageElapsedAndOutOfFrameId(@TempDir Path dir) throws Exception {
        FrameProfile.setEnabled(true);
        try (var rec = recording("com.atomchat.ScreenLifecycle")) {
            long token = FrameProfile.screenStage("CONFIG_RELOAD");
            assertTrue(token > 0);
            FrameProfile.screenStageEnd("CONFIG_RELOAD", token, true);
            rec.stop();
            Path file = dir.resolve("screen.jfr");
            rec.dump(file);
            var all = events(file, "com.atomchat.ScreenLifecycle");
            assertEquals(1, all.size());
            assertEquals("CONFIG_RELOAD", all.get(0).getString("stage"));
            assertEquals(0L, all.get(0).getLong("frameId"));
            assertTrue(all.get(0).getLong("elapsedNanos") >= 0);
            assertTrue(all.get(0).getBoolean("success"));
            assertDoesNotThrow(() -> all.get(0).getLong("monotonicNanos"));
        } finally {
            FrameProfile.setEnabled(false);
            FrameProfile.reset();
        }
    }

    @Test
    void debugOffCreatesNoAtomChatEvents(@TempDir Path dir) throws Exception {
        FrameProfile.setEnabled(false);
        try (var rec = recording("com.atomchat.Navigation", "com.atomchat.ScreenLifecycle",
                "com.atomchat.PanelSegments", "com.atomchat.UiFrame")) {
            FrameSegments.navigation("root", "CHAT_LIST");
            FrameProfile.screenStageEnd("INIT", FrameProfile.screenStage("INIT"), true);
            // Mirror the production call sites: start() and begin() are gated by the same switch.
            long started = FrameProfile.start();
            long id = FrameSegments.begin(started != 0L);
            FrameSegments.finish(id, true);
            FrameProfile.frame(started, -1, id);
            rec.stop();
            Path file = dir.resolve("off.jfr");
            rec.dump(file);
            long custom = RecordingFile.readAllEvents(file).stream()
                    .filter(e -> e.getEventType().getName().startsWith("com.atomchat.")).count();
            assertEquals(0, custom, "debug off must emit no AtomChat events");
        } finally {
            FrameProfile.reset();
        }
    }

    // ------------------------------------------------------------- GPU ring

    private static final class FakeGl implements GpuTimerQuery.Gl {
        boolean supported = true;
        boolean alive = true;
        boolean conflict;
        boolean failBegin, failEnd, failResult;
        int created, deleted;
        java.util.Set<Integer> ready = new java.util.HashSet<>();
        int nextQuery = 1;

        @Override public boolean timerQuerySupported() { return supported; }
        @Override public boolean contextStillValid() { return alive; }
        @Override public boolean otherQueryActive() { return conflict; }
        @Override public int createQuery() { created++; return nextQuery++; }
        @Override public void deleteQuery(int query) { deleted++; ready.remove(query); }
        @Override public void beginQuery(int query) {
            if (failBegin) throw new IllegalStateException("GL begin failed");
        }
        @Override public void endQuery() {
            if (failEnd) throw new IllegalStateException("GL end failed");
        }
        @Override public boolean resultAvailable(int query) { return ready.contains(query); }
        @Override public long resultNanos(int query) {
            if (failResult) throw new IllegalStateException("GL result failed");
            return query * 1_000_000L;
        }
    }

    @Test
    void gpuRingOnlyReadsAvailableResultsAndNeverBlocks() {
        var gl = new FakeGl();
        try (var ring = new GpuTimerQuery(gl, 3)) {
            assertTrue(ring.beginFrame(101));
            ring.endFrame();
            assertEquals(0, ring.poll().size(), "first frame is not ready yet");
            gl.ready.add(1);
            var samples = ring.poll();
            assertEquals(1, samples.size());
            assertEquals(101, samples.get(0).frameId());
            assertEquals(1_000_000L, samples.get(0).gpuNanos());
            assertTrue(samples.get(0).latencyNanos() >= 0);
            assertEquals(1, ring.completed());
            assertEquals(0, ring.dropped());
        }
    }

    @Test
    void gpuRingDropsWhenFullAndRecyclesQueries() {
        var gl = new FakeGl();
        try (var ring = new GpuTimerQuery(gl, 2)) {
            assertTrue(ring.beginFrame(1)); ring.endFrame();
            assertTrue(ring.beginFrame(2)); ring.endFrame();
            assertFalse(ring.beginFrame(3), "no free slot and no ready result: skip, do not overwrite");
            assertEquals(1, ring.dropped());
            gl.ready.add(1);
            assertEquals(1, ring.poll().size());
            assertTrue(ring.beginFrame(4), "recycled slot becomes available");
            ring.endFrame();
            assertEquals(0, ring.expired());
        }
    }

    @Test
    void gpuRingSkipsUnsupportedConflictingAndLostContexts() {
        var gl = new FakeGl();
        try (var ring = new GpuTimerQuery(gl, 4)) {
            gl.conflict = true;
            assertFalse(ring.beginFrame(1));
            assertEquals(1, ring.skipped());
            gl.conflict = false;
            gl.supported = false;
            assertFalse(ring.beginFrame(2));
            assertEquals(2, ring.skipped());
            gl.supported = true;
            assertTrue(ring.beginFrame(3)); ring.endFrame();
            gl.alive = false;
            assertEquals(0, ring.poll().size(), "lost context must not be read");
            ring.reset();
            assertEquals(1, ring.expired());
            assertEquals(gl.created, gl.deleted, "reset releases every query");
        }
    }

    @Test
    void gpuRingRecoversFromGlFailuresWithoutStalling() {
        var gl = new FakeGl();
        try (var ring = new GpuTimerQuery(gl, 3)) {
            gl.failBegin = true;
            assertFalse(ring.beginFrame(1));
            assertEquals(gl.created, gl.deleted, "a failed begin must release its query");
            gl.failBegin = false;
            gl.failEnd = true;
            assertTrue(ring.beginFrame(2));
            ring.endFrame();
            assertEquals(0, ring.pending(), "a failed end must not enqueue a broken query");
            assertEquals(0, ring.stalled());
            gl.failEnd = false;
            assertTrue(ring.beginFrame(3), "the ring must not be permanently stalled");
            ring.endFrame();
            assertEquals(1, ring.pending(), "the recovered frame is queued for a later poll");
        }
        assertEquals(gl.created, gl.deleted, "close releases the queued query");
    }

    @Test
    void gpuRingReclaimsAStuckHeadWithoutStarvingReadyResults() {
        var gl = new FakeGl();
        var clock = new AtomicLong(1_000L);
        try (var ring = new GpuTimerQuery(gl, 3, clock::get)) {
            assertTrue(ring.beginFrame(1)); ring.endFrame();
            assertTrue(ring.beginFrame(2)); ring.endFrame();
            gl.ready.add(2);
            assertEquals(0, ring.poll().size(), "FIFO: a stuck head blocks later results for now");
            clock.addAndGet(GpuTimerQuery.RESULT_TIMEOUT_NANOS + 1);
            var samples = ring.poll();
            assertEquals(1, samples.size(), "the stuck head is reclaimed, the ready result survives");
            assertEquals(2, samples.get(0).frameId());
            assertEquals(1, ring.expired());
            assertEquals(gl.created, gl.deleted);
        }
    }

    @Test
    void gpuRingCloseReleasesEveryQuery() {
        var gl = new FakeGl();
        var ring = new GpuTimerQuery(gl, 2);
        assertTrue(ring.beginFrame(1)); ring.endFrame();
        assertTrue(ring.beginFrame(2)); ring.endFrame();
        ring.close();
        assertEquals(gl.created, gl.deleted);
        ring.close();
        assertEquals(gl.created, gl.deleted, "close is idempotent");
    }
}
