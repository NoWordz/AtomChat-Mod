package com.atom.chat.diagnostics;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class FrameProfileJfrTest {
    @Test void recordingContainsTheActualPopupLabelAndRenderDuration(@TempDir Path dir) throws Exception {
        FrameProfile.reset(); FrameProfile.setEnabled(true);
        try (var recording = new Recording()) {
            recording.enable("com.atomchat.UiFrame").withThreshold(Duration.ZERO);
            recording.start();
            FrameProfile.surface(FrameProfile.Surface.EMOJI);
            FrameProfile.frame(System.nanoTime() - 1_000_000, -1);
            FrameProfile.frame(System.nanoTime() - 1_000_000, -1);
            recording.stop(); var file = dir.resolve("popup.jfr"); recording.dump(file);
            var events = RecordingFile.readAllEvents(file).stream()
                    .filter(e -> e.getEventType().getName().equals("com.atomchat.UiFrame")).toList();
            assertEquals(2, events.size());
            assertEquals("EMOJI", events.get(0).getString("surface"));
            assertEquals("OTHER", events.get(1).getString("surface"), "unlabelled HUD draws cannot inherit an old popup");
            assertTrue(events.get(0).getLong("renderNanos") > 0);
            assertEquals(0L, events.get(0).getLong("frameId"), "unassociated samples must be explicit");
        } finally { FrameProfile.setEnabled(false); FrameProfile.reset(); }
    }
    @Test void uiFrameAndPanelSegmentsShareTheSuppliedId(@TempDir Path dir) throws Exception {
        FrameProfile.reset(); FrameProfile.setEnabled(true);
        try (var recording = new Recording()) {
            recording.enable("com.atomchat.UiFrame").withThreshold(Duration.ZERO);
            recording.enable("com.atomchat.PanelSegments").withThreshold(Duration.ZERO);
            recording.start();
            long id = FrameSegments.begin(true);
            FrameProfile.surface(FrameProfile.Surface.CHAT);
            FrameSegments.finish(id, true);
            FrameProfile.frame(System.nanoTime() - 1_000_000, -1, id);
            recording.stop(); var file = dir.resolve("paired.jfr"); recording.dump(file);
            var events = RecordingFile.readAllEvents(file);
            var ui = events.stream().filter(e -> e.getEventType().getName().equals("com.atomchat.UiFrame")).toList();
            var segments = events.stream().filter(e -> e.getEventType().getName().equals("com.atomchat.PanelSegments")).toList();
            assertEquals(1, ui.size()); assertEquals(1, segments.size());
            assertTrue(id > 0); assertEquals(id, ui.get(0).getLong("frameId"));
            assertEquals(id, segments.get(0).getLong("frameId"));
        } finally { FrameProfile.setEnabled(false); FrameProfile.reset(); }
    }

    @Test void eitherEventCanBeMissingWithoutReusingAnId(@TempDir Path dir) throws Exception {
        FrameProfile.reset(); FrameProfile.setEnabled(true);
        try {
            long previous = 0;
            for (String eventName : new String[]{"com.atomchat.UiFrame", "com.atomchat.PanelSegments"}) {
                try (var recording = new Recording()) {
                    recording.enable(eventName).withThreshold(Duration.ZERO);
                    String disabled = eventName.endsWith("UiFrame") ? "com.atomchat.PanelSegments" : "com.atomchat.UiFrame";
                    recording.disable(disabled);
                    recording.start();
                    long id = FrameSegments.begin(true);
                    FrameSegments.finish(id, true);
                    FrameProfile.frame(System.nanoTime() - 1_000_000, -1, id);
                    recording.stop(); var file = dir.resolve(eventName + ".jfr"); recording.dump(file);
                    var all = RecordingFile.readAllEvents(file);
                    var events = all.stream().filter(e -> e.getEventType().getName().equals(eventName)).toList();
                    assertEquals(0, all.stream().filter(e -> e.getEventType().getName().equals(disabled)).count());
                    assertEquals(1, events.size()); assertTrue(id > previous);
                    assertEquals(id, events.get(0).getLong("frameId")); previous = id;
                }
            }
        } finally { FrameProfile.setEnabled(false); FrameProfile.reset(); }
    }

}
