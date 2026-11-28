package com.atom.chat.diagnostics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncDiagnosticOutputTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void keepsAcceptedMessagesInOrderWhenTheSinkStalls() throws InterruptedException {
        var started = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var lines = new CopyOnWriteArrayList<String>();
        Thread caller = Thread.currentThread();
        var output = new AsyncDiagnosticOutput(2, line -> {
            assertNotSame(caller, Thread.currentThread());
            if (lines.isEmpty()) {
                started.countDown();
                await(unblock);
            }
            lines.add(line);
        });

        try {
            output.offer("capture", "first");
            await(started);
            output.offer("echo", "second");
            output.offer("capture", "third");
            output.offer("render", "fourth");
        } finally {
            unblock.countDown();
            assertTrue(output.finish(TIMEOUT));
        }

        assertEquals(List.of("[AtomChat][capture] first", "[AtomChat][echo] second", "[AtomChat][capture] third"),
                lines.stream().filter(line -> !line.startsWith("[AtomChat][diagnostics]")).toList());
        assertTrue(lines.contains("[AtomChat][diagnostics] discarded=1"));
    }

    @Test
    void continuesWhenTheSinkRejectsOneEntry() throws InterruptedException {
        var lines = new CopyOnWriteArrayList<String>();
        var output = new AsyncDiagnosticOutput(4, line -> {
            if (line.endsWith("unwritable")) throw new IllegalStateException("test sink failure");
            lines.add(line);
        });

        output.offer("capture", "unwritable");
        output.offer("echo", "later");

        assertTrue(output.finish(TIMEOUT));
        assertTrue(lines.contains("[AtomChat][echo] later"));
        assertTrue(lines.contains("[AtomChat][diagnostics] discarded=1"));
    }

    @Test
    void shutdownTimeoutCancelsAndCountsMessagesThatCannotDrain() throws InterruptedException {
        var started = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var lines = new CopyOnWriteArrayList<String>();
        var output = new AsyncDiagnosticOutput(2, line -> {
            started.countDown();
            try {
                if (unblock.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) lines.add(line);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            output.offer("capture", "running");
            await(started);
            output.offer("capture", "pending");
            org.junit.jupiter.api.Assertions.assertFalse(output.finish(Duration.ZERO));
            assertEquals(1, output.discardPending());
            assertTrue(output.finish(TIMEOUT));
            assertTrue(lines.isEmpty());
        } finally {
            unblock.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
