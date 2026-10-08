package com.atom.chat.page;

/**
 * The visible window of a conversation's retained messages — the arithmetic
 * behind the "load earlier" fold.
 *
 * <p>A conversation keeps its retained rows (up to the store's cap) in memory;
 * the list initially shows only the newest {@code batch} of them and reveals
 * one more batch per activation. This keeps the per-frame list walk
 * proportional to what is on screen rather than to everything retained.</p>
 *
 * <p>It is a plain value model with no GUI and no Minecraft types, so the
 * batch arithmetic and the "nothing left to load" edge are unit-testable
 * without a window or a game.</p>
 */
public final class HistoryWindow {
    private final int batch;
    private int total;
    private int visibleStart;

    public HistoryWindow(int total, int batch) {
        if (batch <= 0) {
            throw new IllegalArgumentException("batch must be positive");
        }
        this.batch = batch;
        reset(total);
    }

    /**
     * Re-derives the window for a new total. Keeps the newest batch visible —
     * a conversation that shrinks (clear, history reload) starts folded again
     * rather than pointing past its end.
     */
    public void reset(int total) {
        this.total = Math.max(0, total);
        this.visibleStart = Math.max(0, this.total - batch);
    }

    /**
     * Follows a total that changed in place (a message arrived, the store
     * evicted its oldest row) without moving the fold: the same number of rows
     * stays hidden at the top, so an unfolded conversation does not snap back
     * to its newest batch on every incoming message. Only clamps when the
     * window would fall outside the list.
     */
    public void syncTotal(int total) {
        this.total = Math.max(0, total);
        this.visibleStart = Math.min(this.visibleStart, this.total);
    }

    /** Index of the first visible row; rows before it are folded away. */
    public int visibleStart() {
        return visibleStart;
    }

    /** How many rows are currently visible. */
    public int visibleCount() {
        return total - visibleStart;
    }

    /** The total the window was last derived from. */
    public int total() {
        return total;
    }

    /** How many retained rows are still folded away above the window. */
    public int remaining() {
        return visibleStart;
    }

    /** Whether a further "load earlier" would reveal anything. */
    public boolean canLoadEarlier() {
        return visibleStart > 0;
    }

    /**
     * Reveals one more batch, clamped at the oldest row. Returns whether
     * anything was revealed (false at the top of the conversation), so a caller
     * can skip the scroll-anchor compensation.
     */
    public boolean loadEarlier() {
        if (visibleStart <= 0) {
            return false;
        }
        visibleStart = Math.max(0, visibleStart - batch);
        return true;
    }
}
