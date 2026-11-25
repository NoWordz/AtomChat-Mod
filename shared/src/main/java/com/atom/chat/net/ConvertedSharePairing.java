package com.atom.chat.net;

import com.atom.chat.api.card.ShareCard;

import java.util.UUID;

/** A receipt belongs to the next system delivery on this client connection. */
public final class ConvertedSharePairing {
    static final String FINGERPRINT = "atomchat:system_share_packet";
    private static final long LIFETIME_NS = 5_000_000_000L;

    public record Share(UUID author, String name, String body, ShareCard card) {}
    private record Receipt(UUID fingerprint, Share share, long receivedAt) {}
    private record Delivery(Object component, Share share) {}

    private Receipt pending;
    private Delivery delivering;
    private long generation;

    void arm(UUID fingerprint, Share share, long now) {
        pending = new Receipt(fingerprint, share, now);
    }

    void deliver(UUID fingerprint, Object component, long now, Runnable callback) {
        var receipt = pending;
        pending = null;
        var previous = delivering;
        long session = generation;
        delivering = receipt != null && receipt.fingerprint().equals(fingerprint)
                && now - receipt.receivedAt() < LIFETIME_NS ? new Delivery(component, receipt.share()) : null;
        try {
            callback.run();
        } finally {
            delivering = session == generation ? previous : null;
        }
    }

    Share takeVisible(Object component) {
        var current = delivering;
        if (current == null || current.component() != component) return null;
        delivering = null;
        return current.share();
    }

    Share current() { return delivering == null ? null : delivering.share(); }

    void discardPending() { pending = null; }

    void clear() {
        generation++;
        pending = null;
        delivering = null;
    }
}
