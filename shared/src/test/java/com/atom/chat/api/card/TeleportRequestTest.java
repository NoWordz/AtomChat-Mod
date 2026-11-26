package com.atom.chat.api.card;

import java.util.List;
import org.junit.jupiter.api.Test;

import static com.atom.chat.api.card.ShareCard.ActionKind.RUN_COMMAND;
import static com.atom.chat.api.card.ShareCard.ActionKind.SUGGEST_COMMAND;
import static org.junit.jupiter.api.Assertions.*;

class TeleportRequestTest {
    @Test
    void preservesHuskHomesButtonCommandsAndTheirClickKinds() {
        var signals = signals("Options: [Accept] [Decline]", true,
                new UniversalCardMatcher.ClickActionSpec("[Accept]", RUN_COMMAND, "/huskhomes:tpaccept Alice"),
                new UniversalCardMatcher.ClickActionSpec("[Decline]", SUGGEST_COMMAND, "/huskhomes:tpdeny Alice"));
        var card = UniversalCardMatcher.matchTeleportRequest(signals, "", false, root -> false);
        assertEquals(SpecialCardPresentation.Kind.TELEPORT_REQUEST, SpecialCardPresentation.kind(card));
        assertEquals("/huskhomes:tpaccept Alice", card.actions().get(0).value());
        assertEquals("[Accept]", card.actions().get(0).label());
        assertEquals(SUGGEST_COMMAND, card.actions().get(1).kind());
        assertFalse(card.isNativeClickCommand("/huskhomes:tpdeny Alice"));
    }

    @Test
    void essentialsUsesOnlyTargetedCommandsAndNeedsAConfirmedCommandRoot() {
        var signals = signals("[LabRank]Alice请求传送到你这里。", true);
        assertTrue(UniversalCardMatcher.matchTeleportRequest(signals, "", false, root -> false).actions().isEmpty());
        var card = UniversalCardMatcher.matchTeleportRequest(signals, "", false, root -> root.startsWith("essentials:"));
        assertEquals("/essentials:tpaccept Alice", card.actions().get(0).value());
        assertEquals("/essentials:tpdeny Alice", card.actions().get(1).value());
        assertNull(UniversalCardMatcher.matchTeleportRequest(signals("Alice has requested to teleport to you.", false),
                "", false, root -> true));
        assertNull(UniversalCardMatcher.matchTeleportRequest(signals("Someone said Alice has requested to teleport to you.", true),
                "", false, root -> true));
    }

    @Test
    void ambiguousOrMismatchedNativeCommandsStayReadOnly() {
        var request = signals("Alice has requested to teleport to you.", true,
                new UniversalCardMatcher.ClickActionSpec("Accept", RUN_COMMAND, "/tpaccept Bob"),
                new UniversalCardMatcher.ClickActionSpec("Deny", RUN_COMMAND, "/tpdeny"));
        assertTrue(UniversalCardMatcher.matchTeleportRequest(request, "", false, root -> true).actions().isEmpty());
    }

    @Test
    void stellarDedicatedServerLiteralKeepsSourceButtonsAndReceipt() {
        var literal = signals("[StellarRTP] Alice 请求传送到你这里。 [接受] [拒绝]", true,
                new UniversalCardMatcher.ClickActionSpec("[接受]", RUN_COMMAND, "/tpyes Alice"),
                new UniversalCardMatcher.ClickActionSpec("[拒绝]", RUN_COMMAND, "/tpno Alice"));
        var card = UniversalCardMatcher.matchTeleportRequest(literal, "", false, root -> false);
        assertEquals("StellarRTP", card.sourceLabel());
        assertEquals("Alice 请求传送到你这里。", card.title());
        assertTrue(card.isNativeClickCommand("/tpyes Alice"));
        TeleportRequestSession.reset();
        try {
            TeleportRequestSession.receive(card);
            assertTrue(TeleportRequestSession.beginAction(card));
            TeleportRequestSession.observe("[StellarRTP] 传送请求已拒绝。", "", "");
            assertEquals("已拒绝传送请求", CardStateStore.get(card.cardId()).statusText());
        } finally {
            TeleportRequestSession.reset();
        }
        var translated = signals("[StellarRTP] Alice wants you to teleport to them.", true);
        assertEquals("StellarRTP",
                UniversalCardMatcher.matchTeleportRequest(translated, "", false, root -> false).sourceLabel());
    }

    @Test
    void namedOutOfOrderReceiptsAndExpiryUpdateOnlyTheirOwnCards() {
        var alice = husk("Alice");
        var bob = husk("Bob");
        TeleportRequestSession.reset();
        try {
            TeleportRequestSession.receive(alice);
            TeleportRequestSession.receive(bob);
            assertTrue(TeleportRequestSession.beginAction(alice));
            assertTrue(TeleportRequestSession.beginAction(bob));
            TeleportRequestSession.observe("You declined Bob's teleport request!", "", "");
            assertEquals("已拒绝传送请求", CardStateStore.get(bob.cardId()).statusText());
            assertEquals("已发送传送回应", CardStateStore.get(alice.cardId()).statusText());
            TeleportRequestSession.observe("Error: Your pending teleport request has expired.", "", "");
            assertEquals("传送请求已过期", CardStateStore.get(alice.cardId()).statusText());
            assertTrue(CardStateStore.get(alice.cardId()).disableActions());
            assertFalse(TeleportRequestSession.beginAction(alice));

            var old = husk("Alice");
            var replacement = husk("Alice");
            TeleportRequestSession.receive(old);
            TeleportRequestSession.receive(replacement);
            assertFalse(TeleportRequestSession.beginAction(old));
            TeleportRequestSession.reset();
            assertFalse(TeleportRequestSession.beginAction(replacement));
        } finally {
            TeleportRequestSession.reset();
        }
    }

    @Test
    void lateReceiptDoesNotCloseUnansweredReplacementFromSamePlayer() throws InterruptedException {
        TeleportRequestSession.reset();
        try {
            var old = husk("Alice");
            TeleportRequestSession.receive(old);
            assertTrue(TeleportRequestSession.beginAction(old));
            var replacement = husk("Alice");
            TeleportRequestSession.receive(replacement);
            Thread.sleep(2_150);
            TeleportRequestSession.observe("You declined Alice's teleport request!", "", "");
            assertNull(CardStateStore.get(replacement.cardId()));
            assertTrue(TeleportRequestSession.beginAction(replacement));
            TeleportRequestSession.observe("You declined Alice's teleport request!", "", "");
            assertEquals("已发送传送回应", CardStateStore.get(replacement.cardId()).statusText());
        } finally {
            TeleportRequestSession.reset();
        }
    }

    @Test
    void repeatedNamedReceiptDoesNotResolveTheNextGeneration() {
        TeleportRequestSession.reset();
        try {
            var old = husk("Alice");
            TeleportRequestSession.receive(old);
            assertTrue(TeleportRequestSession.beginAction(old));
            TeleportRequestSession.observe("You declined Alice's teleport request!", "", "");
            assertEquals("已拒绝传送请求", CardStateStore.get(old.cardId()).statusText());

            var replacement = husk("Alice");
            TeleportRequestSession.receive(replacement);
            TeleportRequestSession.observe("You declined Alice's teleport request!", "", "");
            assertNull(CardStateStore.get(replacement.cardId()));
            assertTrue(TeleportRequestSession.beginAction(replacement));
            TeleportRequestSession.observe("You declined Alice's teleport request!", "", "");
            assertEquals("已发送传送回应", CardStateStore.get(replacement.cardId()).statusText());
        } finally {
            TeleportRequestSession.reset();
        }
    }

    @Test
    void timelyUnnamedReceiptsCompleteSuccessiveLocalResponses() throws InterruptedException {
        TeleportRequestSession.reset();
        try {
            var first = essentials("Alice");
            TeleportRequestSession.receive(first);
            assertTrue(TeleportRequestSession.beginAction(first));
            TeleportRequestSession.observe("Teleport request accepted.", "", "");
            assertEquals("已接受传送请求", CardStateStore.get(first.cardId()).statusText());

            var second = essentials("Alice");
            TeleportRequestSession.receive(second);
            Thread.sleep(2_150);
            TeleportRequestSession.observe("Teleport request accepted.", "", "");
            assertNull(CardStateStore.get(second.cardId()), "a duplicate cannot settle an unanswered card");

            assertTrue(TeleportRequestSession.beginAction(second));
            TeleportRequestSession.observe("Teleport request accepted.", "", "");
            assertEquals("已接受传送请求", CardStateStore.get(second.cardId()).statusText());
        } finally {
            TeleportRequestSession.reset();
        }
    }

    @Test
    void recentUnnamedDuplicateDoesNotSettleARespondedReplacement() {
        TeleportRequestSession.reset();
        try {
            var first = essentials("Alice");
            TeleportRequestSession.receive(first);
            assertTrue(TeleportRequestSession.beginAction(first));
            TeleportRequestSession.observe("Teleport request denied.", "", "");
            assertEquals("已拒绝传送请求", CardStateStore.get(first.cardId()).statusText());

            var second = essentials("Alice");
            TeleportRequestSession.receive(second);
            assertTrue(TeleportRequestSession.beginAction(second));
            TeleportRequestSession.observe("Teleport request denied.", "", "");
            assertEquals("已发送传送回应", CardStateStore.get(second.cardId()).statusText());
        } finally {
            TeleportRequestSession.reset();
        }
    }

    @Test
    void ambiguousUnnamedReceiptLeavesRespondedCardsUnconfirmed() {
        TeleportRequestSession.reset();
        try {
            var first = essentials("Alice");
            var second = essentials("Bob");
            TeleportRequestSession.receive(first);
            TeleportRequestSession.receive(second);
            assertTrue(TeleportRequestSession.beginAction(first));
            assertTrue(TeleportRequestSession.beginAction(second));
            TeleportRequestSession.observe("Teleport request accepted.", "", "");
            assertEquals("已发送传送回应", CardStateStore.get(first.cardId()).statusText());
            assertEquals("已发送传送回应", CardStateStore.get(second.cardId()).statusText());
        } finally {
            TeleportRequestSession.reset();
        }
    }

    private static ShareCard essentials(String name) {
        return UniversalCardMatcher.matchTeleportRequest(
                signals(name + " has requested to teleport to you.", true), "", false,
                root -> root.startsWith("essentials:"));
    }

    private static ShareCard husk(String name) {
        var signals = signals("Options: [Accept] [Decline]", true,
                new UniversalCardMatcher.ClickActionSpec("[Accept]", RUN_COMMAND, "/huskhomes:tpaccept " + name),
                new UniversalCardMatcher.ClickActionSpec("[Decline]", RUN_COMMAND, "/huskhomes:tpdeny " + name));
        return UniversalCardMatcher.matchTeleportRequest(signals, "", false, root -> false);
    }

    private static UniversalCardMatcher.MessageSignals signals(String text, boolean system,
                                                                 UniversalCardMatcher.ClickActionSpec... clicks) {
        return new UniversalCardMatcher.MessageSignals(text, "", system, List.of(), List.of(clicks), false, "");
    }
}
