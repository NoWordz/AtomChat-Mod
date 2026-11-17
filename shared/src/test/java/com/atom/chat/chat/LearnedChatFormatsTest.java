package com.atom.chat.chat;

import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LearnedChatFormatsTest {
    @BeforeEach void reset() { SeenPlayers.clear(); LearnedChatFormats.clear(); }
    @AfterEach void clean() { reset(); }
    @Test void confirmedEnvelopeRecognizesANicknameWithoutAnOnlineEntry() {
        UUID id = UUID.randomUUID();
        SeenPlayers.remember(id, "Alice", "BlueFox");
        LearnedChatFormats.observe("[Global] BlueFox: first", "BlueFox", "first");
        var parsed = LearnedChatFormats.match("[Global] BlueFox: second").orElseThrow();
        assertEquals("Alice", parsed.account());
        assertEquals("[Global] BlueFox", parsed.display());
        assertEquals("second", parsed.content());
        assertEquals(id, SeenPlayers.findUuid("BlueFox"));
        assertTrue(LearnedChatFormats.match("[Global] Unknown: second").isEmpty());
        assertTrue(LearnedChatFormats.match("[Notice] BlueFox: second").isEmpty());
    }
    @Test void ambiguityAndUuidConflictsDoNotTurnIntoIdentityBindings() {
        SeenPlayers.remember(UUID.randomUUID(), "Alice", "BlueFox");
        SeenPlayers.remember(UUID.randomUUID(), "Bob", "BlueFox");
        LearnedChatFormats.observe("BlueFox: one", "BlueFox", "one");
        assertTrue(LearnedChatFormats.match("BlueFox: two").isEmpty());
        assertNull(SeenPlayers.findUuid("BlueFox"));
        SeenPlayers.remember(UUID.randomUUID(), "Alice", "NewNick");
        assertNull(SeenPlayers.findUuid("Alice"));
        assertTrue(LearnedChatFormats.snapshot().isEmpty());
    }
    @Test void proseAndAmbiguousBodyCannotTrainAnEnvelope() {
        LearnedChatFormats.observe("Alice joined", "Alice", "joined");
        LearnedChatFormats.observe("Alice: hello hello", "Alice", "hello");
        assertTrue(LearnedChatFormats.snapshot().isEmpty());
    }
    @Test void persistedRelationshipsRoundTripWithoutReplacingLiveIdentity() {
        UUID oldId = UUID.randomUUID(), liveId = UUID.randomUUID();
        SeenPlayers.remember(oldId, "Alice", "BlueFox");
        LearnedChatFormats.observe("[G] BlueFox: one", "BlueFox", "one");
        var identities = SeenPlayers.snapshot(); var formats = LearnedChatFormats.snapshot();
        reset();
        SeenPlayers.remember(liveId, "Alice", "NewNick");
        SeenPlayers.restore(identities); LearnedChatFormats.restore(formats);
        assertEquals(liveId, SeenPlayers.findUuid("Alice"));
        assertNull(SeenPlayers.findUuid("BlueFox"));
        assertTrue(LearnedChatFormats.match("[G] BlueFox: two").isEmpty());
    }
    @Test void learnedPrivateEnvelopeNeverBecomesPublicChat() {
        SeenPlayers.remember(UUID.randomUUID(),"Alice","BlueFox");
        LearnedChatFormats.observe("[BlueFox -> You] secret","BlueFox","secret",true);
        var match=LearnedChatFormats.match("[BlueFox -> You] next").orElseThrow();
        assertTrue(match.whisper());assertEquals("next",match.content());assertEquals("Alice",match.account());
    }
}
