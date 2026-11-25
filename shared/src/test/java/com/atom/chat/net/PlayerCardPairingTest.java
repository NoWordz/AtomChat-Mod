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
}
