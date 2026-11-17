package com.atom.chat.chat;

import java.util.*;

/** Bounded in-flight sends. Sender and conversation proof precede content matching. */
public final class OutgoingMessages {
    public enum State { RECEIVED, PENDING, UNCONFIRMED, CONFIRMED, FAILED }
    public static final long TIMEOUT_MS = 10_000;
    private static final long CORRELATION_MS = 120_000;
    private record Pending(ChatMessage local, PlayerRef partner, String wire, long sentAt) {}
    private static final Deque<Pending> sends = new ArrayDeque<>();
    private OutgoingMessages() {}
    public static synchronized void sent(ChatMessage local, PlayerRef partner, String wire) {
        local.delivery(State.PENDING);
        sends.addLast(new Pending(local, partner, wire, System.currentTimeMillis()));
        while (sends.size() > 32) sends.removeFirst().local().delivery(State.UNCONFIRMED);
    }
    public static synchronized boolean reconcile(ChatMessage received, PlayerRef partner, boolean provenSender, String originalBody) {
        expire(System.currentTimeMillis());
        if (!provenSender || received.getSenderUuid() == null) return false;
        List<Pending> candidates = sends.stream().filter(p -> Objects.equals(p.partner(), partner)
                && received.getSenderUuid().equals(p.local().getSenderUuid())).toList();
        Pending match = null;
        if (originalBody != null) {
            // An explicit original body is stronger than the renderer's rewritten body.
            for (Pending p : candidates) if (Objects.equals(p.wire(),originalBody)) {match=p;break;}
        } else {
            long now = System.currentTimeMillis();
            for (Pending p : candidates) if (now-p.sentAt() <= TIMEOUT_MS
                    && (Objects.equals(p.wire(),received.getContentText())
                    || Objects.equals(p.local().getContentText(),received.getContentText()))) {match=p;break;}
        }
        if (match == null) return false;
        ChatMessage corrected = match.local().withServerEcho(received);
        boolean replaced = partner == null ? ChatStore.get().replace(match.local().getId(), corrected)
                : PrivateChatStore.replace(partner, match.local().getId(), corrected);
        if (!replaced) return false;
        sends.remove(match); return true;
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
    }
}
