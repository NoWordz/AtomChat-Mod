package com.atom.chat.poke;

/**
 * Local dedupe for the poke cue (sound + toast on the receiving side, sound on
 * the sending side). Same shape as {@link com.atom.chat.notification.NotificationSoundGate}
 * but a <em>separate</em> instance: a poke must not suppress a mention sound and
 * a mention must not suppress a poke.
 *
 * <p>Pure and offline-testable — the server owns the real rate limit; this only
 * stops a double-click burst from stacking audio on one client.</p>
 */
public final class PokeGate {
    /** Minimum gap between two pokes from one source, in ms. */
    public static final long MIN_INTERVAL_MS = 500L;

    private long lastMs = Long.MIN_VALUE;

    public PokeGate() {
        this(MIN_INTERVAL_MS);
    }

    public PokeGate(long intervalMs) {
        this.interval = intervalMs;
    }

    private final long interval;

    /**
     * @return whether a cue is allowed now. The first call always passes;
     *         afterwards the gap must reach the interval (edge accepted).
     */
    public boolean allow(long now) {
        if (lastMs == Long.MIN_VALUE || now - lastMs >= interval) {
            lastMs = now;
            return true;
        }
        return false;
    }
}
