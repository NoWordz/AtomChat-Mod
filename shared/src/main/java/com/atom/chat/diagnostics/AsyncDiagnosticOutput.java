package com.atom.chat.diagnostics;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Keeps slow diagnostic logging away from callers and bounds the queued work. */
final class AsyncDiagnosticOutput {
    private final Consumer<String> sink;
    private final ThreadPoolExecutor writer;
    private final AtomicLong discarded = new AtomicLong();

    AsyncDiagnosticOutput(int capacity, Consumer<String> sink) {
        this.sink = sink;
        writer = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity), task -> {
            Thread thread = new Thread(task, "AtomChat diagnostic output");
            thread.setDaemon(true);
            return thread;
        }, (task, executor) -> {
            if (!executor.isShutdown()) discarded.incrementAndGet();
        });
        writer.allowCoreThreadTimeOut(true);
    }

    void offer(String category, String message) {
        writer.execute(() -> {
            long skipped = discarded.getAndSet(0);
            if (skipped > 0 && !write("diagnostics", "discarded=" + skipped)) {
                discarded.addAndGet(skipped);
            }
            if (!write(category, message)) discarded.incrementAndGet();
        });
    }

    private boolean write(String category, String message) {
        try {
            sink.accept(AtomChatLog.message(category, message));
            return true;
        } catch (RuntimeException failure) {
            // A failing destination should neither retry forever nor stall the game.
            return false;
        }
    }

    boolean finish(Duration timeout) throws InterruptedException {
        writer.shutdown();
        return writer.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    long discardPending() {
        return writer.shutdownNow().size() + discarded.getAndSet(0);
    }
}
