package com.atom.chat.net;

import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.diagnostics.AtomChatLog;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Selects one share delivery and keeps local data only for an incoming native echo. */
final class ClientShareSubmission {
    private static final int MAX_LOCAL = 8;

    record LocalShare(UUID author, String body, ShareCard card, long sentAt, long receipt) {}

    private final Deque<LocalShare> localShares = new ArrayDeque<>();
    private final PlayerCardPairing<UUID> nativeShares = PlayerCardPairing.byUuid();

    boolean send(ShareCard card, boolean paired, UUID localAuthor,
                 Consumer<byte[]> upload, Consumer<String> nativeChat) {
        String body = PlayerCardPairing.nativeText(card);
        if (body == null || body.isBlank()) return false;
        long receipt = AtomChatConfig.diagnosticsEnabled() ? AtomChatLog.nextReceipt() : 0;
        boolean uploaded = false;
        if (paired) {
            ShareCard toUpload = card;
            // The companion reconstructs the held stack on the server.
            if (card.item() != null && !card.item().itemSnbt().isEmpty()) {
                var item = card.item();
                toUpload = card.toBuilder().itemPayload(new ShareCard.ItemPayload(item.itemId(), "",
                        item.displayName(), item.summary(), item.rarityLabel(), item.accentColor(), item.count())).build();
            }
            byte[] encoded = CardWireCodec.encodeCard(toUpload);
            if (encoded.length <= CardWireCodec.MAX_CARD_BYTES) {
                upload.accept(encoded);
                uploaded = true;
            }
        }
        LocalShare local = null;
        if (localAuthor != null) {
            long now = System.nanoTime();
            discardExpired(now);
            local = new LocalShare(localAuthor, body, card, now, receipt);
            if (localShares.size() == MAX_LOCAL) localShares.removeFirst();
            localShares.addLast(local);
        }
        try {
            traceSend(receipt, card, uploaded ? "paired" : "native");
            nativeChat.accept(body);
        } catch (RuntimeException failure) {
            if (local != null) localShares.removeLastOccurrence(local);
            throw failure;
        }
        return true;
    }

    void armNative(UUID sender, String body, ShareCard card, long now) {
        nativeShares.arm(sender, body, card, now);
    }

    BooleanSupplier deferNative(UUID sender, String body, boolean unfiltered, long now, BooleanSupplier callback) {
        if (sender == null) return callback;
        LocalShare local = takeLocal(sender, body, now);
        if (!unfiltered) {
            nativeShares.take(sender, body, now);
            return callback;
        }
        return nativeShares.defer(sender, body, now, local == null ? null : local.card(),
                local == null ? 0 : local.receipt(), callback);
    }

    boolean hasLocalShares(long now) {
        discardExpired(now);
        return !localShares.isEmpty();
    }

    LocalShare takeLocal(UUID sender, String body, long now) {
        discardExpired(now);
        if (localShares.isEmpty()) return null;
        for (var it = localShares.iterator(); it.hasNext(); ) {
            var share = it.next();
            if (share.author().equals(sender) && share.body().equals(body)) {
                it.remove();
                if (share.receipt() != 0 && AtomChatConfig.diagnosticsEnabled()) {
                    AtomChatLog.diagnostic("card.echo", "receipt=" + share.receipt()
                            + " local_match=true template=" + share.card().template());
                }
                return share;
            }
        }
        return null;
    }

    ShareCard takeNative(UUID sender, String originalBody, String visibleBody, long receiveReceipt) {
        return nativeShares.takeVisible(sender, originalBody, visibleBody, receiveReceipt == 0 ? null
                : sendReceipt -> correlate(sendReceipt, receiveReceipt));
    }

    static void correlate(long sendReceipt, long receiveReceipt) {
        if (sendReceipt != 0 && receiveReceipt != 0) {
            AtomChatLog.diagnostic("card.pair", "receipt=" + receiveReceipt + " send_receipt=" + sendReceipt);
        }
    }

    void clear() {
        localShares.clear();
        nativeShares.clear();
    }

    private static void traceSend(long receipt, ShareCard card, String delivery) {
        if (receipt != 0) {
            AtomChatLog.diagnostic("card.send", "receipt=" + receipt
                    + " delivery=" + delivery + " template=" + card.template());
        }
    }

    private void discardExpired(long now) {
        while (!localShares.isEmpty() && now - localShares.getFirst().sentAt() >= PlayerCardPairing.LIFETIME_NS) {
            localShares.removeFirst();
        }
    }
}
