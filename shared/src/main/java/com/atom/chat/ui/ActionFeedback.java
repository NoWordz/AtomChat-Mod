package com.atom.chat.ui;

import com.atom.chat.render.Easing;

import java.util.ArrayList;
import java.util.List;

/**
 * The queue and timing behind the action-feedback toast: one short-lived entry
 * per completed (or failed) action, newest wins, errors surface first.
 *
 * <p>Pure model — no GUI, no fonts, no Minecraft types — so the hold durations,
 * the "same action twice refreshes instead of stacking" rule and the error
 * priority are unit-testable without a window or a game. The renderer
 * ({@code ActionToast}) reads {@link #snapshot} and only draws.</p>
 *
 * <p>Hold times split by outcome: a success is a glance ("cache cleared"), a
 * failure is something the player may need to act on, so it lingers. Both share
 * the notification banner's motion family so the two floating surfaces feel
 * like one system.</p>
 */
public final class ActionFeedback {
    public enum Outcome { SUCCESS, ERROR }

    /** Most toasts on screen at once; older ones are dropped, not queued forever. */
    public static final int MAX_VISIBLE = 3;
    /** Drop-in duration, matching the notification banner. */
    public static final long APPEAR_MS = 220L;
    public static final long SUCCESS_HOLD_MS = 2000L;
    public static final long ERROR_HOLD_MS = 4000L;
    /** Slide-up-and-fade exit, matching the notification banner. */
    public static final long EXIT_MS = 150L;
    /**
     * Rise-in distance. The stack is anchored just above the composer, so the
     * toast comes up from behind it rather than dropping in from the panel top —
     * the same travel the notification banner uses, mirrored for a lower anchor.
     */
    public static final float ENTER_TRAVEL = UiTokens.s(14);

    /**
     * One toast: the dedupe {@code key}, an optional already-resolved
     * {@code label} (for messages that carry an argument, like a poked player's
     * name), the outcome and when it was raised. Identical keys replace rather
     * than pile up, so two copies or two cache clears never stack.
     */
    public record Entry(String key, String label, Outcome outcome, long born) {
        public long holdMs() {
            return outcome == Outcome.ERROR ? ERROR_HOLD_MS : SUCCESS_HOLD_MS;
        }

        public long totalMs() {
            return holdMs() + EXIT_MS;
        }

        public boolean expired(long now) {
            return now - born >= totalMs();
        }

        /** [0..1] opacity envelope: ease-in over {@link #APPEAR_MS}, then fade over {@link #EXIT_MS}. */
        public float alpha(long now) {
            long age = now - born;
            if (age <= APPEAR_MS) {
                return Easing.easeOutQuad(age / (float) APPEAR_MS);
            }
            long left = totalMs() - age;
            return Math.max(0.0F, Math.min(1.0F, left / (float) EXIT_MS));
        }

        /**
         * Top edge of row {@code index} in a stack anchored at {@code bottom},
         * where index 0 is the entry {@link #snapshot} returns first. Pure so the
         * stacking direction — nearest the anchor first, climbing upward — is
         * unit-testable without a canvas.
         */
        public static float rowTop(float bottom, int index, float rowH, float gap, float bottomInset) {
            return bottom - bottomInset - (index + 1) * rowH - index * gap;
        }

        /**
         * Opacity with the decorative-motion switch applied. With motion off the
         * row is simply present or gone: the toast is feedback the player asked
         * for, so it must still appear — only the fade is what the switch buys.
         */
        public float alpha(long now, boolean motion) {
            return motion ? alpha(now) : (expired(now) ? 0.0F : 1.0F);
        }

        /**
         * Downward travel in UI px: eases up into place with a slight overshoot
         * on the way in ({@code easeOutBack}), then sinks back down while fading
         * on the way out. Positive is downward, so the row starts below its slot
         * and rises to it. Zero with motion off.
         */
        public float offset(long now, boolean motion) {
            if (!motion) {
                return 0.0F;
            }
            long age = now - born;
            if (age <= APPEAR_MS) {
                return (1.0F - Easing.easeOutBack(age / (float) APPEAR_MS)) * ENTER_TRAVEL;
            }
            float gone = Math.max(0.0F, Math.min(1.0F, (age - holdMs()) / (float) EXIT_MS));
            return gone * gone * ENTER_TRAVEL;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Raises a toast. An entry with the same key is replaced (its clock
     * restarts) instead of stacked; errors are placed ahead of successes so a
     * failure is never pushed out of the visible window by chatter.
     */
    public void show(String key, Outcome outcome, long now) {
        show(key, null, outcome, now);
    }

    /** As {@link #show(String, Outcome, long)} with a pre-resolved label. */
    public void show(String key, String label, Outcome outcome, long now) {
        entries.removeIf(e -> e.key().equals(key));
        Entry entry = new Entry(key, label, outcome, now);
        if (outcome == Outcome.ERROR) {
            entries.add(0, entry);
        } else {
            entries.add(entry);
        }
    }

    /**
     * The entries still on screen at {@code now}, errors first, capped at
     * {@link #MAX_VISIBLE}. Expired entries are pruned as a side effect.
     */
    public List<Entry> snapshot(long now) {
        if (entries.isEmpty()) {
            return List.of();   // no allocation on the common idle frame
        }
        entries.removeIf(e -> e.expired(now));
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Entry> ordered = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.outcome() == Outcome.ERROR) {
                ordered.add(e);
            }
        }
        for (Entry e : entries) {
            if (e.outcome() == Outcome.SUCCESS) {
                ordered.add(e);
            }
        }
        return ordered.size() <= MAX_VISIBLE ? ordered : new ArrayList<>(ordered.subList(0, MAX_VISIBLE));
    }

    public boolean hasVisible(long now) {
        return !snapshot(now).isEmpty();
    }

    /** Drops everything (disconnect, world switch, panel close). */
    public void clear() {
        entries.clear();
    }
}
