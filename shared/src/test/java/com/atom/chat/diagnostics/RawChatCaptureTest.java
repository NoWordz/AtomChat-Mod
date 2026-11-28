package com.atom.chat.diagnostics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawChatCaptureTest {
    @Test
    void startsOnlyOnExplicitRequestAndExpires() {
        var now = new AtomicLong(123);
        var capture = new RawChatCapture(now::get);

        assertFalse(capture.active());
        assertTrue(capture.toggle());
        assertTrue(capture.active());
        now.addAndGet(Duration.ofSeconds(60).toNanos());
        assertFalse(capture.active());
    }

    @Test
    void manualAndSessionStopsClearTheWindow() {
        var capture = new RawChatCapture(() -> 123L);

        assertTrue(capture.toggle());
        assertFalse(capture.toggle());
        assertFalse(capture.active());
        assertTrue(capture.toggle());
        capture.stop();
        assertFalse(capture.active());
    }
}
