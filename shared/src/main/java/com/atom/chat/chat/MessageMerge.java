package com.atom.chat.chat;

import com.atom.chat.reaction.ReactionClient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Anti-spam merge predicates, pure and unit-testable.
 *
 * <p>Two consecutive non-system messages merge when they come from the same
 * speaker, carry the same body text and have the same quote block. The caller
 * owns the actual list mutation; this class only says whether the messages are
 * merge candidates and how to produce the merged row.
 */
public final class MessageMerge {
    /**
     * Config seam. Production sets this to read {@code AtomChatConfig}; tests
     * leave it at {@code false} (the historical no-merge behaviour) or point it
     * at a local boolean so they never touch Fabric's config directory.
     */
    public static volatile BooleanSupplier antiSpamEnabledSupplier = () -> false;

    /**
     * Repeats older than this window open a fresh row instead of growing the
     * count: "gg" three hours after the last "gg" is a new message, not a
     * duplicate. Same window as the compact visual grouping.
     */
    public static final long MERGE_WINDOW_MS = MessageGrouping.GROUP_TIME_MS;

    private MessageMerge() {
    }

    public static boolean antiSpamEnabled() {
        return antiSpamEnabledSupplier.getAsBoolean();
    }

    /** Whether {@code next} can be folded into the last stored message. */
    public static boolean canMerge(ChatMessage last, ChatMessage next) {
        return Objects.equals(ReactionClient.key(last), ReactionClient.key(next)) && sameMessage(last, next);
    }

    /** The view can collapse separate delivery IDs while both rows have no distinct reaction to show. */
    public static boolean canMergeForDisplay(ChatMessage last, ChatMessage next) {
        if (!sameMessage(last, next)) return false;
        var left = ReactionClient.key(last);
        var right = ReactionClient.key(next);
        return Objects.equals(left, right)
                || (ReactionClient.entries(left).isEmpty() && ReactionClient.entries(right).isEmpty());
    }

    private static boolean sameMessage(ChatMessage last, ChatMessage next) {
        if (last == null || next == null) {
            return false;
        }
        if (last.hasCard() || next.hasCard()) {
            return false;
        }
        if (unsettled(last) || unsettled(next)) return false;
        if (last.isSystem() || next.isSystem()) {
            return false;
        }
        if (last.deliveryState() != next.deliveryState()) return false;
        if (next.isOwn() != last.isOwn()) {
            return false;
        }
        long dt = next.getTimestamp() - last.getTimestamp();
        if (dt < 0L || dt > MERGE_WINDOW_MS) {
            return false;
        }
        if (!Objects.equals(last.getQuoteName(), next.getQuoteName())
                || !Objects.equals(last.getQuoteText(), next.getQuoteText())) {
            return false;
        }
        String a = last.getDisplayText();
        String b = next.getDisplayText();
        if (!Objects.equals(a, b)) {
            return false;
        }
        return sameSpeaker(last, next);
    }

    private static boolean unsettled(ChatMessage message) {
        var state = message.deliveryState();
        return state == OutgoingMessages.State.PENDING || state == OutgoingMessages.State.UNCONFIRMED
                || state == OutgoingMessages.State.FAILED;
    }

    /** Merges {@code next} into {@code last}, preserving the newer timestamp. */
    public static ChatMessage merge(ChatMessage last, ChatMessage next) {
        return last.withDuplicateCount(sumCounts(last, next), next.getTimestamp());
    }

    /** Actions on a display-only group target the newest original delivery. */
    public static ChatMessage mergeForDisplay(ChatMessage last, ChatMessage next) {
        return next.withDuplicateCount(sumCounts(last, next));
    }

    private static int sumCounts(ChatMessage last, ChatMessage next) {
        return (int) Math.min(Integer.MAX_VALUE, (long) last.getDuplicateCount() + next.getDuplicateCount());
    }

    /** A proven echo can merge only after it has replaced its pending row. */
    static void replaceConfirmed(List<ChatMessage> messages, int index, ChatMessage corrected) {
        messages.set(index, corrected);
        if (!antiSpamEnabled()) return;
        if (index > 0 && canMerge(messages.get(index - 1), corrected)) {
            corrected = merge(messages.get(index - 1), corrected);
            messages.remove(index);
            index--;
            messages.set(index, corrected);
        }
        if (index + 1 < messages.size() && canMerge(corrected, messages.get(index + 1))) {
            messages.set(index, merge(corrected, messages.remove(index + 1)));
        }
    }

    /** Whether two messages come from the same speaker (UUID first, name fallback). */
    public static boolean sameSpeaker(ChatMessage a, ChatMessage b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.isIdentityUnknown() || b.isIdentityUnknown()) return false;
        UUID au = a.getSenderUuid();
        UUID bu = b.getSenderUuid();
        if (au != null && bu != null) {
            return au.equals(bu);
        }
        String aName = nameKey(a);
        String bName = nameKey(b);
        return aName != null && aName.equals(bName);
    }

    private static String nameKey(ChatMessage m) {
        String profile = m.getProfileName();
        if (profile != null && !profile.isBlank()) {
            return profile.toLowerCase(java.util.Locale.ROOT);
        }
        String sender = m.getSenderName();
        return sender != null && !sender.isBlank()
                ? sender.toLowerCase(java.util.Locale.ROOT) : null;
    }
}
