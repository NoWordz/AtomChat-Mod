package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;

/**
 * Central gate for decorative motion.
 *
 * <p>The decorative setting controls entrances and transitions. Performance
 * mode additionally makes pointer feedback, scroll and typing feedback
 * immediate. Media playback has its own clock.</p>
 */
public final class Animations {
    private Animations() {
    }

    /** Whether decorative motion is allowed at all. */
    public static boolean enabled() {
        return AtomChatConfig.get().effectiveAnimationEnabled();
    }

    /**
     * Returns {@code durationMs}, or 0 when decorative motion is off. A zero
     * duration must be honoured as "land on the target this frame", never as a
     * division by zero.
     */
    public static long ms(long durationMs) {
        return enabled() ? durationMs : 0L;
    }

    /** Callers show the caret only while the field is focused. */
    public static boolean caretVisible(long millis) {
        return AtomChatConfig.performanceModeActive() || (millis / 500L) % 2L == 0L;
    }

    /**
     * Whether the QQ-style message entrance should play. Subordinate to the
     * master switch: turning decorative motion off also stops entrances even
     * if this specific option is still on.
     */
    public static boolean messageEntry() {
        return enabled() && AtomChatConfig.get().messageEntryAnimation;
    }

    /** Whether double-clicking another player's avatar pokes them. */
    public static boolean avatarPoke() {
        var config = AtomChatConfig.get();
        return !config.performanceMode && config.avatarPokeEnabled;
    }
}
