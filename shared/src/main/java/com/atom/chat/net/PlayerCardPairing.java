package com.atom.chat.net;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.ShareCard;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/** Matches one validated card intent with a subsequent native chat message from that connection. */
final class PlayerCardPairing<Peer> {
    private static final int MAX_PENDING = 512;
    static final long LIFETIME_NS = 5_000_000_000L;
    static final String NATIVE_BODY = "atomchat:native_share_body";
    private final Map<Peer, Pending> pending;
    private final Map<Object, Accepted<Peer>> accepted = new IdentityHashMap<>();
    private final ThreadLocal<Delivery<Peer>> activeDelivery = new ThreadLocal<>();
    private volatile long generation;

    PlayerCardPairing() { this(new IdentityHashMap<>()); }

    private PlayerCardPairing(Map<Peer, Pending> pending) { this.pending = pending; }

    static PlayerCardPairing<java.util.UUID> byUuid() {
        return new PlayerCardPairing<>(new java.util.HashMap<>());
    }

    private record Pending(String body, ShareCard card, long receivedAt) {}
    private record Accepted<Peer>(Peer peer, Pending intent) {}

    private static final class Delivery<Peer> {
        final Peer peer;
        final String body;
        final boolean localFallback;
        final long sendReceipt;
        ShareCard card;

        Delivery(Peer peer, String body, ShareCard card, boolean localFallback, long sendReceipt) {
            this.peer = peer;
            this.body = body;
            this.card = card;
            this.localFallback = localFallback;
            this.sendReceipt = sendReceipt;
        }
    }

    synchronized void arm(Peer peer, String nativeBody, ShareCard validated, long now) {
        if (nativeBody == null || nativeBody.isBlank()) return;
        pending.values().removeIf(value -> expired(value, now));
        if (pending.size() >= MAX_PENDING && !pending.containsKey(peer)) return;
        pending.put(peer, new Pending(nativeBody, validated, now));
    }

    synchronized ShareCard take(Peer peer, String nativeBody, long now) {
        Pending intent = takeIntent(peer, nativeBody, now);
        return intent == null ? null : intent.card();
    }

    private Pending takeIntent(Peer peer, String nativeBody, long now) {
        var intent = pending.get(peer);
        if (intent == null) return null;
        if (expired(intent, now)) {
            pending.remove(peer);
            return null;
        }
        if (!intent.body().equals(nativeBody)) return null;
        pending.remove(peer);
        return intent;
    }

    /** Server intake: each decoded packet owns a distinct signed body through filtering and decoration. */
    synchronized void bindAccepted(Peer peer, Object signedBody, String nativeBody, long now) {
        if (accepted.containsKey(signedBody)) return;
        Pending intent = takeIntent(peer, nativeBody, now);
        if (intent == null) return;
        accepted.values().removeIf(entry -> expired(entry.intent(), now));
        if (accepted.size() < MAX_PENDING) {
            accepted.put(signedBody, new Accepted<>(peer, intent));
        }
    }

    /** Consume once at broadcast. Cancelled messages can never donate their attachment to later chat. */
    synchronized ShareCard takeAccepted(Peer peer, Object signedBody, long now) {
        var message = accepted.get(signedBody);
        if (message == null || message.peer() != peer) return null;
        accepted.remove(signedBody);
        return expired(message.intent(), now) ? null : message.intent().card();
    }

    /** Claim at native intake, then expose the card only during that message's display callback. */
    BooleanSupplier defer(Peer peer, String nativeBody, long now, BooleanSupplier callback) {
        return defer(peer, nativeBody, now, null, 0, callback);
    }

    BooleanSupplier defer(Peer peer, String nativeBody, long now, ShareCard localFallback,
                          long sendReceipt, BooleanSupplier callback) {
        long session = generation;
        ShareCard paired = take(peer, nativeBody, now);
        ShareCard card = paired != null ? paired : localFallback;
        if (card == null) return callback;
        var delivery = new Delivery<>(peer, nativeBody, card, paired == null, sendReceipt);
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
        return takeVisible(peer, nativeBody, nativeBody);
    }

    ShareCard takeVisible(Peer peer, String nativeBody, String visibleBody) {
        return takeVisible(peer, nativeBody, visibleBody, null);
    }

    ShareCard takeVisible(Peer peer, String nativeBody, String visibleBody, LongConsumer correlate) {
        var delivery = activeDelivery.get();
        if (delivery == null || !delivery.peer.equals(peer) || !delivery.body.equals(nativeBody)) return null;
        ShareCard card = delivery.card;
        delivery.card = null;
        if (card == null || delivery.localFallback && !delivery.body.equals(visibleBody)) return null;
        if (correlate != null && delivery.sendReceipt != 0) correlate.accept(delivery.sendReceipt);
        return card;
    }

    synchronized void discard(Predicate<Peer> disconnected) {
        pending.keySet().removeIf(disconnected);
        accepted.values().removeIf(message -> disconnected.test(message.peer()));
    }

    synchronized void clear() {
        generation++;
        pending.clear();
        accepted.clear();
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
