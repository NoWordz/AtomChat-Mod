package com.atom.chat.chat;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.diagnostics.AtomChatLog;
import com.atom.chat.reaction.ReactionClient;

import java.util.*;

/** Bounded in-flight sends. Sender and conversation proof precede content matching. */
public final class OutgoingMessages {
    public enum State { RECEIVED, PENDING, UNCONFIRMED, CONFIRMED, FAILED }
    public static final long TIMEOUT_MS = 10_000;
    private static final long CORRELATION_MS = 120_000;
    private static final int MAX_PENDING = 32;
    private static final int MAX_EVICTED = 32;

    private record EchoKey(UUID sender, PlayerRef partner, String wire, String visible) {
        boolean matches(UUID sender, PlayerRef partner, String body, boolean original) {
            return sender.equals(this.sender) && sameConversation(this.partner, partner)
                    && (Objects.equals(wire, body) || !original && Objects.equals(visible, body));
        }
    }
    private record Pending(ChatMessage local, EchoKey key, long sentAt) {}
    private record Evicted(EchoKey key, long sentAt) {}
    private static final Deque<Pending> sends = new ArrayDeque<>();
    private static final Deque<Evicted> evicted = new ArrayDeque<>();
    private static long untrackedUntil;
    private OutgoingMessages() {}
    public static synchronized void sent(ChatMessage local, PlayerRef partner, String wire) {
        long now = System.currentTimeMillis();
        local.delivery(State.PENDING);
        var key = new EchoKey(local.getSenderUuid(), partner, wire, local.getContentText());
        sends.addLast(new Pending(local, key, now));
        if (AtomChatConfig.diagnosticsEnabled()) {
            AtomChatLog.diagnostic("chat.send", "message=" + local.getId() + " private=" + (partner != null));
        }
        if (sends.size() > MAX_PENDING) {
            Pending lost = sends.removeFirst();
            lost.local().delivery(State.UNCONFIRMED);
            rememberEviction(lost, now);
        }
    }
    private static void rememberEviction(Pending lost, long now) {
        if (lost.key().sender() == null || now - lost.sentAt() > CORRELATION_MS) return;
        evicted.removeIf(candidate -> now - candidate.sentAt() > CORRELATION_MS);
        if (evicted.size() == MAX_EVICTED) {
            // Without the discarded key, we cannot rule out an identical older send.
            var untracked = evicted.removeFirst();
            untrackedUntil = Math.max(untrackedUntil, untracked.sentAt() + CORRELATION_MS);
        }
        evicted.addLast(new Evicted(lost.key(), lost.sentAt()));
    }
    public static synchronized boolean reconcile(ChatMessage received, PlayerRef partner, boolean provenSender, String originalBody) {
        long now = System.currentTimeMillis();
        expire(now);
        UUID sender = received.getSenderUuid();
        if (!provenSender || sender == null || now <= untrackedUntil) return false;
        boolean original = originalBody != null;
        String body = original ? originalBody : received.getContentText();
        Pending match = null;
        for (Pending candidate : sends) {
            if (!candidate.key().matches(sender, partner, body, original)) continue;
            if (match != null) return false;
            match = candidate;
        }
        if (match == null || !original && now - match.sentAt() > TIMEOUT_MS) return false;
        for (Evicted candidate : evicted) {
            if (candidate.key().matches(sender, partner, body, original)) return false;
        }
        ChatMessage corrected = match.local().withServerEcho(received);
        ReactionClient.bind(received);
        ReactionClient.bind(corrected);
        boolean replaced = partner == null ? ChatStore.get().replace(match.local().getId(), corrected)
                : PrivateChatStore.replace(partner, match.local().getId(), corrected);
        if (AtomChatConfig.diagnosticsEnabled()) {
            AtomChatLog.diagnostic("chat.echo", "message=" + match.local().getId()
                    + " incoming=" + received.getId() + " replaced=" + replaced);
        }
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
        evicted.removeIf(candidate -> now - candidate.sentAt() > CORRELATION_MS);
        if (now > untrackedUntil) untrackedUntil = 0;
    }
    public static synchronized void clear() {
        for (Pending p : sends) p.local().delivery(State.UNCONFIRMED);
        sends.clear();
        evicted.clear();
        untrackedUntil = 0;
    }
}
