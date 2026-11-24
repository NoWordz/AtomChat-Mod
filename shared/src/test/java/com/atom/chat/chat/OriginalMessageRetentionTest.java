package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OriginalMessageRetentionTest {
    private static final UUID ALICE = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final PlayerRef PARTNER = PlayerRef.of(ALICE, "Alice");
    private boolean merge;

    @BeforeEach void reset() {
        ChatStore.reset();
        PrivateChatStore.reset();
        MessageMerge.antiSpamEnabledSupplier = () -> merge;
        merge = true;
    }

    @AfterEach void cleanup() {
        MessageMerge.antiSpamEnabledSupplier = () -> false;
        ChatStore.reset();
        PrivateChatStore.reset();
    }

    @Test void disablingAntiSpamRevealsOriginalPublicAndPrivateMessages() {
        var first = message("same", 1000);
        var second = message("same", 2000);
        ChatStore.get().add(first);
        assertTrue(ChatStore.get().add(second));
        PrivateChatStore.addIncoming(PARTNER, first);
        assertTrue(PrivateChatStore.addIncoming(PARTNER, second));
        assertEquals(2, ChatStore.get().snapshot().get(0).getDuplicateCount());
        assertEquals(1, ChatStore.publicUnread());
        assertEquals(1, PrivateChatStore.unread(PARTNER));
        merge = false;
        assertEquals(List.of(first, second), ChatStore.get().snapshot());
        assertEquals(List.of(first, second), PrivateChatStore.messages(PARTNER));
        merge = true;
        assertEquals(1, ChatStore.get().snapshot().size());
        assertEquals(1, PrivateChatStore.messages(PARTNER).size());
    }

    @Test void capCountsOriginalsEvenWhenEveryMessageDisplaysInOneRow() {
        var originals = new ArrayList<ChatMessage>();
        for (int i = 0; i < 501; i++) {
            var message = message("same", 1000 + i);
            originals.add(message);
            ChatStore.get().add(message);
            PrivateChatStore.addOutgoing(PARTNER, message);
        }
        assertEquals(500, ChatStore.get().snapshot().get(0).getDuplicateCount());
        assertEquals(500, PrivateChatStore.messages(PARTNER).get(0).getDuplicateCount());
        merge = false;
        assertEquals(originals.subList(1, 501), ChatStore.get().snapshot());
        assertEquals(originals.subList(1, 501), PrivateChatStore.messages(PARTNER));
    }

    @Test void replacingOneOriginalDoesNotReplaceOrLoseItsIdenticalNeighbours() {
        var first = message("same", 1000);
        var second = message("same", 2000);
        var third = message("same", 3000);
        for (var message : List.of(first, second, third)) {
            ChatStore.get().add(message);
            PrivateChatStore.addIncoming(PARTNER, message);
        }
        var correction = second.withServerEcho(message("changed", 2000));
        assertTrue(ChatStore.get().replace(second.getId(), correction));
        assertTrue(PrivateChatStore.replace(PARTNER, second.getId(), correction));
        assertEquals(List.of(first, correction, third), ChatStore.get().snapshot());
        assertEquals(List.of(first, correction, third), PrivateChatStore.messages(PARTNER));
        assertEquals(1, ChatStore.publicUnread());
        assertEquals(1, PrivateChatStore.unread(PARTNER));
    }

    @Test void persistenceSelectsOriginalsWhileDisplaySnapshotsRemainCached() {
        var first = message("same", 1000);
        var second = message("same", 2000);
        ChatStore.get().add(first);
        ChatStore.get().add(second);
        PrivateChatStore.addIncoming(PARTNER, first);
        PrivateChatStore.addIncoming(PARTNER, second);
        assertSame(ChatStore.get().snapshot(), ChatStore.get().snapshot());
        assertSame(ChatStore.get().rawSnapshot(), ChatStore.get().rawSnapshot());
        assertSame(PrivateChatStore.messages(PARTNER), PrivateChatStore.messages(PARTNER));
        assertSame(PrivateChatStore.rawMessages(PARTNER), PrivateChatStore.rawMessages(PARTNER));
        var selected = com.atom.chat.history.HistorySnapshot.capture(ChatStore.get().rawSnapshot(),
                List.of(PARTNER), PrivateChatStore::rawMessages, (partner, message) -> message);
        assertEquals(List.of(first, second, first, second), selected);
        for (var original : selected) assertEquals(1, original.getDuplicateCount());
        assertThrows(UnsupportedOperationException.class, () -> ChatStore.get().rawSnapshot().clear());
    }

    private static ChatMessage message(String text, long time) {
        var echo = LocalEcho.build(text, text, null, null, ALICE, "Alice", RichText.literal("Alice"));
        return new ChatMessage(echo.getComponent(), false, false, null, null, ALICE, "Alice", "Alice", text, time);
    }
}
