package com.atom.chat.net;

import com.atom.chat.api.card.ShareCard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerCardPairingTest {
    private final ShareCard card = ShareCard.location("Base", 7, 64, 12, "minecraft:overworld").build();

    @Test void onlyTheOriginalConnectionAndExactNativeBodyCanClaimTheValidatedCard() {
        var pairing = new PlayerCardPairing<Object>();
        Object player = new Object();
        Object other = new Object();
        String body = PlayerCardPairing.nativeText(card);
        assertNotNull(body);
        assertTrue(body.contains("x:7"));
        pairing.arm(player, body, card, 1);

        assertNull(pairing.take(other, body, 2));
        assertNull(pairing.take(player, "ordinary message", 3));
        assertSame(card, pairing.take(player, body, 4));
        assertNull(pairing.take(player, body, 5), "the same share intent cannot be replayed");
    }

    @Test void expiredAndDisconnectedIntentsNeverConsumeLaterNativeChat() {
        var pairing = new PlayerCardPairing<Object>();
        Object player = new Object();
        String body = PlayerCardPairing.nativeText(card);

        pairing.arm(player, body, card, 1);
        assertNull(pairing.take(player, body, 5_000_000_002L));

        pairing.arm(player, body, card, 6_000_000_000L);
        pairing.discard(peer -> peer == player);
        assertNull(pairing.take(player, body, 6_000_000_001L));
    }

    @Test void delayedNativeDeliveryKeepsTheMatchedItemWithoutLeakingToAnotherRow() {
        var pairing = PlayerCardPairing.byUuid();
        var sender = java.util.UUID.randomUUID();
        ShareCard item = ShareCard.item("Sword").build();
        String body = PlayerCardPairing.nativeText(item);
        pairing.arm(sender, body, item, 1);
        var callback = pairing.defer(sender, body, 2, () -> {
            assertNull(pairing.takeVisible(sender, "unrelated message"));
            assertSame(item, pairing.takeVisible(sender, body));
            assertNull(pairing.takeVisible(sender, body));
            return true;
        });
        assertNull(pairing.take(sender, body, 6_000_000_000L));
        assertNull(pairing.takeVisible(sender, body));
        assertTrue(callback.getAsBoolean(), "the native chat queue may delay display longer than metadata expiry");
        assertNull(pairing.takeVisible(sender, body));
    }

    @Test void aNativeMessageThatDoesNotReachTheHudCannotAttachToLaterChat() {
        var pairing = PlayerCardPairing.byUuid();
        var sender = java.util.UUID.randomUUID();
        String body = PlayerCardPairing.nativeText(card);
        pairing.arm(sender, body, card, 1);
        assertFalse(pairing.defer(sender, body, 2, () -> false).getAsBoolean());
        assertFalse(pairing.defer(sender, body, 3, () -> {
            assertNull(pairing.takeVisible(sender, body));
            return false;
        }).getAsBoolean());

        pairing.arm(sender, body, card, 4);
        var previousSession = pairing.defer(sender, body, 5, () -> {
            assertNull(pairing.takeVisible(sender, body));
            return false;
        });
        pairing.clear();
        previousSession.getAsBoolean();
    }

    @Test void acceptedNativeDeliveryLinksItsSendReceiptExactlyOnce() {
        var pairing = PlayerCardPairing.byUuid();
        var sender = java.util.UUID.randomUUID();
        var receipts = new java.util.ArrayList<Long>();
        String body = PlayerCardPairing.nativeText(card);
        pairing.arm(sender, body, card, 1);

        assertTrue(pairing.defer(sender, body, 2, card, 41, () -> {
            assertNull(pairing.takeVisible(sender, "another message", body, receipts::add));
            assertSame(card, pairing.takeVisible(sender, body, body, receipts::add));
            assertNull(pairing.takeVisible(sender, body, body, receipts::add));
            return true;
        }).getAsBoolean());
        assertEquals(java.util.List.of(41L), receipts);
    }

    @Test void anEarlierDeferredBroadcastCannotClaimALaterIntent() {
        var pairing = new PlayerCardPairing<Object>();
        Object player = new Object();
        for (ShareCard share : java.util.List.of(card, ShareCard.item("Sword").build())) {
            String body = PlayerCardPairing.nativeText(share);
            // Equal values stand in for different native signed-body objects.
            Object first = new String(body);
            Object second = new String(body);
            pairing.bindAccepted(player, first, body, 1);
            pairing.arm(player, body, share, 2);
            pairing.bindAccepted(player, second, body, 3);

            assertNull(pairing.takeAccepted(player, first, 4));
            assertNull(pairing.takeAccepted(new Object(), second, 4));
            assertSame(share, pairing.takeAccepted(player, second, 4));
            assertNull(pairing.takeAccepted(player, second, 5));
        }
    }

    @Test void aCancelledOrDeclinedBroadcastNeverDonatesItsAttachmentToARepeat() {
        var pairing = new PlayerCardPairing<Object>();
        Object player = new Object();
        String body = PlayerCardPairing.nativeText(card);
        Object cancelled = new Object();
        pairing.arm(player, body, card, 1);
        pairing.bindAccepted(player, cancelled, body, 2);

        Object repeat = new Object();
        pairing.bindAccepted(player, repeat, body, 3);
        assertNull(pairing.takeAccepted(player, repeat, 4));

        ShareCard next = ShareCard.location("Base", 7, 64, 12, "minecraft:overworld").build();
        pairing.arm(player, body, next, 5);
        Object nextShare = new Object();
        pairing.bindAccepted(player, nextShare, body, 6);
        assertSame(next, pairing.takeAccepted(player, nextShare, 7));
        assertNull(pairing.takeAccepted(player, cancelled, 1 + PlayerCardPairing.LIFETIME_NS));
    }

    @Test void intakeAndBroadcastUseOriginalIntentTimeoutAndDisconnectDropsBothStages() {
        var pairing = new PlayerCardPairing<Object>();
        Object player = new Object();
        Object accepted = new Object();
        Object expired = new Object();
        String body = PlayerCardPairing.nativeText(card);
        long limit = PlayerCardPairing.LIFETIME_NS;
        pairing.arm(player, body, card, 1);
        pairing.bindAccepted(player, expired, body, 1 + limit);
        assertNull(pairing.takeAccepted(player, expired, 1 + limit));

        pairing.arm(player, body, card, limit + 2);
        pairing.bindAccepted(player, accepted, body, 2 * limit + 1);
        assertNull(pairing.takeAccepted(player, accepted, 2 * limit + 2));

        pairing.arm(player, body, card, 2 * limit + 3);
        pairing.bindAccepted(player, accepted, body, 2 * limit + 4);
        pairing.arm(player, body, card, 2 * limit + 5);
        pairing.discard(peer -> peer == player);
        pairing.bindAccepted(player, expired, body, 2 * limit + 6);
        assertNull(pairing.takeAccepted(player, accepted, 2 * limit + 6));
        assertNull(pairing.takeAccepted(player, expired, 2 * limit + 6));
    }
}
