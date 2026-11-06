package com.atom.chat.diagnostics;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class FrameSegmentsJfrTest {
    @Test void successfulFrameCarriesSameIdAndBridgeIntervals(@TempDir Path dir) throws Exception {
        try (var rec = new Recording()) {
            rec.enable("com.atomchat.PanelSegments").withThreshold(Duration.ZERO);
            rec.start();
            FrameProfile.setEnabled(true);
            FrameProfile.surface(FrameProfile.Surface.CHAT);
            long id = FrameSegments.begin(true);
            FrameSegments.record(id, FrameSegments.Stage.BRIDGE_ENTER, 123);
            FrameSegments.record(id, FrameSegments.Stage.DRAW, 456);
            FrameSegments.record(id, FrameSegments.Stage.BRIDGE_EXIT, 789);
            FrameSegments.finish(id, true);
            long failed = FrameSegments.begin(true);
            FrameSegments.finish(failed, false);
            long disabled = FrameSegments.begin(false);
            FrameSegments.finish(disabled, true);
            rec.stop(); Path path = dir.resolve("segments.jfr"); rec.dump(path);
            var events = RecordingFile.readAllEvents(path).stream().filter(e -> e.getEventType().getName().equals("com.atomchat.PanelSegments")).toList();
            assertEquals(1, events.size());
            var event = events.get(0);
            assertEquals(id, event.getLong("frameId"));
            assertEquals("CHAT", event.getString("surface"));
            assertEquals(123, event.getLong("bridgeEnterNanos"));
            assertEquals(456, event.getLong("drawNanos"));
            assertEquals(789, event.getLong("bridgeExitNanos"));
        } finally { FrameProfile.setEnabled(false); FrameProfile.reset(); }
    }
}
