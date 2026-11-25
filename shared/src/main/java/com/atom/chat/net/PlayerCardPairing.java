package com.atom.chat.net;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.ShareCard;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Matches one validated card intent with a subsequent native chat message from that connection. */
final class PlayerCardPairing<Peer> {
    private static final int MAX_PENDING = 512;
    private static final long LIFETIME_NS = 5_000_000_000L;
    static final String NATIVE_BODY = "atomchat:native_share_body";
    private final Map<Peer, Pending> pending;
    private final ThreadLocal<Delivery<Peer>> activeDelivery = new ThreadLocal<>();
    private volatile long generation;

    PlayerCardPairing() { this(new IdentityHashMap<>()); }

    private PlayerCardPairing(Map<Peer, Pending> pending) { this.pending = pending; }

    static PlayerCardPairing<java.util.UUID> byUuid() {
        return new PlayerCardPairing<>(new java.util.HashMap<>());
    }

    private record Pending(String body, ShareCard card, long receivedAt) {}

    private static final class Delivery<Peer> {
        final Peer peer;
        final String body;
        ShareCard card;

        Delivery(Peer peer, String body, ShareCard card) {
            this.peer = peer;
            this.body = body;
            this.card = card;
        }
    }

    synchronized void arm(Peer peer, String nativeBody, ShareCard validated, long now) {
        if (nativeBody == null || nativeBody.isBlank()) return;
        pending.values().removeIf(value -> expired(value, now));
        if (pending.size() >= MAX_PENDING && !pending.containsKey(peer)) return;
        pending.put(peer, new Pending(nativeBody, validated, now));
    }

    synchronized ShareCard take(Peer peer, String nativeBody, long now) {
        var intent = pending.get(peer);
        if (intent == null) return null;
        if (expired(intent, now)) {
            pending.remove(peer);
            return null;
        }
        if (!intent.body().equals(nativeBody)) return null;
        pending.remove(peer);
        return intent.card();
    }

    /** Claim at native intake, then expose the card only during that message's display callback. */
    BooleanSupplier defer(Peer peer, String nativeBody, long now, BooleanSupplier callback) {
        long session = generation;
        ShareCard card = take(peer, nativeBody, now);
        if (card == null) return callback;
        var delivery = new Delivery<>(peer, nativeBody, card);
        return () -> {
            if (session != generation) return callback.getAsBoolean();
            var previous = activeDelivery.get();
            activeDelivery.set(delivery);
            try {
                return callback.getAsBoolean();
            } finally {
                delivery.card = null;
                if (previous == null || session != generation) activeDelivery.remove();
                else activeDelivery.set(previous);
            }
        };
    }

    ShareCard takeVisible(Peer peer, String nativeBody) {
        var delivery = activeDelivery.get();
        if (delivery == null || !delivery.peer.equals(peer) || !delivery.body.equals(nativeBody)) return null;
        ShareCard card = delivery.card;
        delivery.card = null;
        return card;
    }

    synchronized void discard(Predicate<Peer> disconnected) {
        pending.keySet().removeIf(disconnected);
    }

    synchronized void clear() {
        generation++;
        pending.clear();
        activeDelivery.remove();
    }

    private static boolean expired(Pending intent, long now) {
        return now - intent.receivedAt() >= LIFETIME_NS;
    }

    static String nativeText(ShareCard card) {
        String body;
        if (card.template() == ShareCard.Template.LOCATION && card.location() != null) {
            var location = card.location();
            String name = BuiltInCardParsers.sanitizeLabel(ComposerLocationCard.nativeName(card), 32)
                    .replace('\"', (char) 39).replace('[', '(').replace(']', ')');
            body = "[name:\"" + name + "\", x:" + location.blockX()
                    + ", y:" + location.blockY() + ", z:" + location.blockZ()
                    + ", dim:" + BuiltInCardParsers.normalizeDimension(location.dimension()) + "]";
        } else if (card.template() == ShareCard.Template.ITEM && card.item() != null) {
            var item = card.item();
            String label = BuiltInCardParsers.sanitizeLabel(item.displayName(), 48);
            String summary = BuiltInCardParsers.sanitizeLabel(item.summary(), 80);
            body = "[装备] " + label + (item.count() > 1 ? " ×" + item.count() : "")
                    + (summary.isBlank() ? "" : " (" + summary + ")");
        } else return null;
        return BuiltInCardParsers.sanitizeLabel(body, 240).replaceAll("\\s+", " ");
    }
}
