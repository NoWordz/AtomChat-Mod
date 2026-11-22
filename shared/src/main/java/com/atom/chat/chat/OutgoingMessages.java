package com.atom.chat.chat;

import com.atom.chat.reaction.ReactionClient;

import java.util.*;

/** Bounded in-flight sends. Sender and conversation proof precede content matching. */
public final class OutgoingMessages {
    public enum State { RECEIVED, PENDING, UNCONFIRMED, CONFIRMED, FAILED }
    public static final long TIMEOUT_MS = 10_000;
    private static final long CORRELATION_MS = 120_000;
    private record Pending(ChatMessage local, PlayerRef partner, String wire, long sentAt) {}
    private static final Deque<Pending> sends = new ArrayDeque<>();
    private static long correlationBlockedUntil;
    private OutgoingMessages() {}
    public static synchronized void sent(ChatMessage local, PlayerRef partner, String wire) {
        local.delivery(State.PENDING);
        sends.addLast(new Pending(local, partner, wire, System.currentTimeMillis()));
        while (sends.size() > 32) {
            Pending lost = sends.removeFirst();
            lost.local().delivery(State.UNCONFIRMED);
            correlationBlockedUntil = Math.max(correlationBlockedUntil, lost.sentAt() + CORRELATION_MS);
        }
    }
    public static synchronized boolean reconcile(ChatMessage received, PlayerRef partner, boolean provenSender, String originalBody) {
        long now = System.currentTimeMillis();
        expire(now);
        if (!provenSender || received.getSenderUuid() == null || now <= correlationBlockedUntil) return false;
        Pending match = null;
        for (Pending candidate : sends) {
            if (!sameConversation(candidate.partner(), partner)
                    || !received.getSenderUuid().equals(candidate.local().getSenderUuid())) continue;
            boolean sameBody = originalBody != null ? Objects.equals(candidate.wire(), originalBody)
                    : Objects.equals(candidate.wire(), received.getContentText())
                    || Objects.equals(candidate.local().getContentText(), received.getContentText());
            // A delayed identical send can still be the source of the echo.
            if (!sameBody) continue;
            if (match != null) return false;
            match = candidate;
        }
        if (match == null || originalBody == null && now - match.sentAt() > TIMEOUT_MS) return false;
        ChatMessage corrected = match.local().withServerEcho(received);
        ReactionClient.bind(received);
        ReactionClient.bind(corrected);
        boolean replaced = partner == null ? ChatStore.get().replace(match.local().getId(), corrected)
                : PrivateChatStore.replace(partner, match.local().getId(), corrected);
        if (!replaced) return false;
        sends.remove(match); return true;
    }
    private static boolean sameConversation(PlayerRef a, PlayerRef b) {
        return Objects.equals(a, b) && (a == null || a.uuid() == null || b.uuid() == null || a.uuid().equals(b.uuid()));
    }
    public static synchronized void expire(long now) {
        for (var it = sends.iterator(); it.hasNext();) {
            Pending p = it.next();
            if (now - p.sentAt() > TIMEOUT_MS) p.local().delivery(State.UNCONFIRMED);
            if (now - p.sentAt() > CORRELATION_MS) it.remove();
        }
    }
    public static synchronized void clear() {
        for (Pending p : sends) p.local().delivery(State.UNCONFIRMED);
        sends.clear();
        correlationBlockedUntil = 0;
    }
}
