package com.atom.chat.chat;

/**
 * Compact message grouping: consecutive non-system messages from the same
 * speaker within five minutes are one visual group. Only the first row keeps
 * the avatar/name band; later rows get tighter spacing (Discord/Telegram
 * style). Pure logic so render/measure/hit-test can share one answer.
 */
public final class MessageGrouping {
    private MessageGrouping() {
    }

    /** Same-sender run window, ported from e33chat. */
    public static final long GROUP_TIME_MS = 5 * 60_000L;

    /**
     * Whether {@code current} is the continuation of {@code previous} (i.e. it
     * should be drawn without the name/avatar band and with grouped spacing).
     */
    public static boolean isSameGroup(ChatMessage previous, ChatMessage current) {
        if (previous == null || current == null) {
            return false;
        }
        if (previous.isSystem() || current.isSystem()) {
            return false;
        }
        if (previous.isOwn() != current.isOwn()) {
            return false;
        }
        if (!MessageMerge.sameSpeaker(previous, current)) {
            return false;
        }
        long dt = current.getTimestamp() - previous.getTimestamp();
        return dt >= 0L && dt <= GROUP_TIME_MS;
    }

    public static boolean continues(ChatMessage previous, ChatMessage current, int timestampIntervalMinutes) {
        return isSameGroup(previous, current) && !hasTimeDivider(previous, current, timestampIntervalMinutes);
    }

    /** A chronological list begins with a divider when timestamps are enabled. */
    public static boolean hasTimeDivider(ChatMessage previous, ChatMessage current, int minutes) {
        return minutes > 0 && (previous == null
                || current.getTimestamp() - previous.getTimestamp() >= minutes * 60_000L);
    }

    /** Tight vertical gap between two rows inside the same compact group. */
    public static float groupedGap(float listGap, float minDesignPx) {
        return Math.max(minDesignPx, listGap / 3.0F);
    }
}
