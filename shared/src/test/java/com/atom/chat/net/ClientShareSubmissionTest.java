package com.atom.chat.net;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.ShareCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientShareSubmissionTest {
    private static final UUID SENDER = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);
    private static final ShareCard LOCATION = ComposerLocationCard.create("Fixture", 7, 64, 12, "minecraft:overworld");
    private static final ShareCard ITEM = ShareCard.item("Fixture").itemPayload(
            new ShareCard.ItemPayload("minecraft:stone", "{id:\"minecraft:stone\",count:1}",
                    "Fixture", "minecraft", "", 0, 1)).build();

    @Test void unconfirmedCompanionStillReceivesOneNativeWaypoint() {
        var client = new Delivery();
        assertTrue(new ClientShareSubmission().send(LOCATION, false, null, ignored -> fail(), client::chat));

        assertEquals(List.of(Row.LOCATION_CARD), client.panel);
        assertEquals(List.of(Row.LOCATION_CARD), client.hud);
        assertEquals(1, client.vanilla.size());
    }

    @Test void unconfirmedCompanionStillReceivesOneReadableItemShare() {
        var client = new Delivery();
        assertTrue(new ClientShareSubmission().send(ITEM, false, null, ignored -> fail(), client::chat));

        assertEquals(List.of(Row.TEXT), client.panel);
        assertEquals(List.of(Row.TEXT), client.hud);
        assertEquals(List.of(PlayerCardPairing.nativeText(ITEM)), client.vanilla);
    }

    @Test void pairedNativeAndConvertedShareProduceOneRowAndOneNativeFallback() {
        for (boolean converted : new boolean[]{false, true}) {
            var client = new Delivery();
            var intent = new PlayerCardPairing<UUID>();
            assertTrue(new ClientShareSubmission().send(ITEM, true, null, bytes -> {
                var card = CardWireCodec.decodeCard(bytes);
                assertNotNull(card);
                assertTrue(card.item().itemSnbt().isEmpty());
                intent.arm(SENDER, PlayerCardPairing.nativeText(card), card, 1);
            }, body -> {
                var card = intent.take(SENDER, body, 2);
                assertNotNull(card, "the attachment must precede the native message");
                client.vanilla.add(body);
                if (converted) {
                    var pairing = new ConvertedSharePairing();
                    var packet = new UUID(0, 3);
                    Object component = new Object();
                    pairing.arm(packet, new ConvertedSharePairing.Share(SENDER, "Fixture", body, card), 3);
                    pairing.deliver(packet, component, 4, () -> client.show(pairing.takeVisible(component).card()));
                } else {
                    var pairing = PlayerCardPairing.byUuid();
                    pairing.arm(SENDER, body, card, 3);
                    pairing.defer(SENDER, body, 4, () -> {
                        client.show(pairing.takeVisible(SENDER, body));
                        return true;
                    }).getAsBoolean();
                }
            }));
            assertEquals(List.of(Row.ITEM_CARD), client.panel);
            assertEquals(List.of(Row.ITEM_CARD), client.hud);
            assertEquals(1, client.vanilla.size());
        }
    }

    @Test void absentOrOversizedAttachmentLeavesOneReadableNativeMessage() {
        var onlyText = new Delivery();
        assertTrue(new ClientShareSubmission().send(ITEM, false, null, ignored -> fail(), onlyText::chat));
        assertEquals(List.of(Row.TEXT), onlyText.panel);
        assertEquals(List.of(Row.TEXT), onlyText.hud);
        assertFalse(onlyText.vanilla.get(0).isBlank());

        var oversized = ITEM.toBuilder().putData("fixture", "a".repeat(CardWireCodec.MAX_CARD_BYTES)).build();
        var fallback = new Delivery();
        assertTrue(new ClientShareSubmission().send(oversized, true, null, ignored -> fail(), fallback::chat));
        assertEquals(List.of(Row.TEXT), fallback.panel);
        assertEquals(1, fallback.vanilla.size());
    }

    @Test void repeatedShareAndOtherAuthorsIdenticalChatAreIndependentDeliveries() {
        var client = new Delivery();
        var pairing = PlayerCardPairing.byUuid();
        String body = PlayerCardPairing.nativeText(ITEM);
        pairing.arm(SENDER, body, ITEM, 1);
        for (UUID author : List.of(OTHER, SENDER, SENDER)) {
            pairing.defer(author, body, 2, () -> {
                client.show(pairing.takeVisible(author, body));
                return true;
            }).getAsBoolean();
        }
        assertEquals(List.of(Row.TEXT, Row.ITEM_CARD, Row.TEXT), client.panel);

        for (int i = 0; i < 2; i++) {
            assertTrue(new ClientShareSubmission().send(ITEM, false, null, ignored -> fail(), client::chat));
        }
        assertEquals(List.of(Row.TEXT, Row.ITEM_CARD, Row.TEXT, Row.TEXT, Row.TEXT), client.panel);
    }

    @Test void theSenderKeepsFullLocalDataWithoutCreatingASecondRow() {
        var submission = new ClientShareSubmission();
        var client = new Delivery();
        var nativeLines = new ArrayList<String>();
        for (int i = 0; i < 2; i++) {
            assertTrue(submission.send(ITEM, false, SENDER, ignored -> fail(), nativeLines::add));
        }
        assertTrue(client.panel.isEmpty(), "submitting a share must not insert a speculative row");
        assertEquals(2, nativeLines.size());
        String body = nativeLines.get(0);
        for (UUID author : List.of(OTHER, SENDER, SENDER, SENDER)) {
            submission.deferNative(author, body, true, System.nanoTime(), () -> {
                var card = submission.takeNative(author, body, body, 0);
                if (card != null) assertSame(ITEM, card, "retain the original local item data");
                client.show(card);
                return true;
            }).getAsBoolean();
        }
        assertEquals(List.of(Row.TEXT, Row.ITEM_CARD, Row.ITEM_CARD, Row.TEXT), client.panel);
        assertEquals(client.panel, client.hud);
    }

    @Test void filteredAndRewrittenNativeLinesNeverRestoreLocalData() {
        var submission = new ClientShareSubmission();
        String body = PlayerCardPairing.nativeText(ITEM);
        for (boolean unfiltered : new boolean[]{false, true}) {
            assertTrue(submission.send(ITEM, false, SENDER, ignored -> fail(), ignored -> {}));
            submission.deferNative(SENDER, body, unfiltered, System.nanoTime(), () -> {
                assertNull(submission.takeNative(SENDER, body, unfiltered ? "redacted" : body, 0));
                return true;
            }).getAsBoolean();
        }
        submission.deferNative(SENDER, body, true, System.nanoTime(), () -> {
            assertNull(submission.takeNative(SENDER, body, body, 0));
            return true;
        }).getAsBoolean();
    }

    @Test void aStructuredSystemEchoCanCarryTheLocalCardOnlyForItsProvenAuthor() {
        var submission = new ClientShareSubmission();
        var pairing = new ConvertedSharePairing();
        var client = new Delivery();
        assertTrue(submission.send(ITEM, false, SENDER, ignored -> fail(), ignored -> {}));
        String body = PlayerCardPairing.nativeText(ITEM);
        for (UUID provenAuthor : new UUID[]{null, OTHER, SENDER, SENDER}) {
            var component = new Object();
            long now = System.nanoTime();
            pairing.deliver(UUID.randomUUID(), component, now, paired -> {
                assertNull(paired);
                var local = submission.takeLocal(provenAuthor, body, now);
                return local == null ? null : new ConvertedSharePairing.Share(
                        provenAuthor, "Fixture", body, local.card(), local.receipt());
            }, () -> {
                assertNull(pairing.takeVisible(new Object()));
                var echo = pairing.takeVisible(component);
                client.show(echo == null ? null : echo.card());
            });
        }
        assertEquals(List.of(Row.TEXT, Row.TEXT, Row.ITEM_CARD, Row.TEXT), client.panel);
        assertEquals(client.panel, client.hud);
    }

    @Test void anExpiredLocalShareCannotClaimALaterSystemMessage() {
        var submission = new ClientShareSubmission();
        assertTrue(submission.send(ITEM, false, SENDER, ignored -> fail(), ignored -> {}));
        long expired = System.nanoTime() + PlayerCardPairing.LIFETIME_NS;
        assertFalse(submission.hasLocalShares(expired));
        assertNull(submission.takeLocal(SENDER, PlayerCardPairing.nativeText(ITEM), expired));
    }

    @Test void aNativeCallbackQueuedOnAnOldConnectionCannotClaimTheLocalCard() {
        var submission = new ClientShareSubmission();
        String body = PlayerCardPairing.nativeText(ITEM);
        assertTrue(submission.send(ITEM, false, SENDER, ignored -> fail(), ignored -> {}));
        var echo = submission.deferNative(SENDER, body, true, System.nanoTime(), () -> {
            assertNull(submission.takeNative(SENDER, body, body, 0));
            return true;
        });
        submission.clear();
        assertTrue(echo.getAsBoolean());
    }

    private enum Row { LOCATION_CARD, ITEM_CARD, TEXT }

    private static final class Delivery {
        final List<Row> panel = new ArrayList<>();
        final List<Row> hud = new ArrayList<>();
        final List<String> vanilla = new ArrayList<>();

        void chat(String body) {
            show(BuiltInCardParsers.parseBracketWaypoint(body, "", "JourneyMap"));
            vanilla.add(body);
        }

        void show(ShareCard card) {
            Row row = card == null ? Row.TEXT : card.template() == ShareCard.Template.LOCATION
                    ? Row.LOCATION_CARD : Row.ITEM_CARD;
            panel.add(row);
            hud.add(row);
        }
    }
}
