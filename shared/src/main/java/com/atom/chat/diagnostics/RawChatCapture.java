package com.atom.chat.diagnostics;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Session-only permission to include the visible chat line in diagnostic output. */
public final class RawChatCapture {
    private static final long WINDOW_NANOS = Duration.ofSeconds(60).toNanos();
    private static final RawChatCapture INSTANCE = new RawChatCapture(System::nanoTime);

    private final LongSupplier clock;
    private volatile long deadline;

    RawChatCapture(LongSupplier clock) {
        this.clock = clock;
    }

    public static RawChatCapture get() {
        return INSTANCE;
    }

    public boolean toggle() {
        long now = clock.getAsLong();
        boolean starting = !activeAt(now);
        deadline = starting ? now + WINDOW_NANOS : 0;
        return starting;
    }

    public boolean active() {
        return activeAt(clock.getAsLong());
    }

    private boolean activeAt(long now) {
        long until = deadline;
        return until != 0 && until - now > 0;
    }

    public void stop() {
        deadline = 0;
    }
}
