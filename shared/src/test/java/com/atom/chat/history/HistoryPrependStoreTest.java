package com.atom.chat.history;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.LocalEcho;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.fun.ChatEffectEvents;
import com.atom.chat.text.RichText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class HistoryPrependStoreTest {
    @BeforeEach void reset() {
        ChatStore.reset();
        PrivateChatStore.reset();
    }

    @Test void publicHistoryPrecedesLiveWithoutResettingUnreadMentionsOrTriggeringEffects() {
        ChatMessage live = message("live"), old = message("old");
        ChatStore.get().add(live);
        ChatStore.noteMention();
        var before = ChatStore.get().snapshot();
        long generation = ChatStore.generation();
        AtomicInteger effects = new AtomicInteger();
        BiConsumer<ChatMessage, PlayerRef> listener = (message, partner) -> effects.incrementAndGet();
        ChatEffectEvents.install(listener);
        try { ChatStore.get().prependLoaded(List.of(old)); }
        finally { ChatEffectEvents.uninstall(listener); }
        assertEquals(List.of(old, live), ChatStore.get().snapshot());
        assertEquals(1, ChatStore.publicUnread());
        assertEquals(1, ChatStore.mentionUnread());
        assertEquals(0, effects.get());
        assertTrue(ChatStore.generation() > generation);
        assertNotSame(before, ChatStore.get().snapshot());
        ChatStore.setPublicActive(true);
        ChatStore.get().prependLoaded(List.of(message("older")));
        ChatStore.get().add(message("next live"));
        assertEquals(0, ChatStore.publicUnread(), "publication must preserve the active public page");
    }

    @Test void privateHistoryPreservesEveryConversationUnreadAndItsActivePartner() {
        PlayerRef alice = PlayerRef.of(UUID.randomUUID(), "Alice");
        PlayerRef bob = PlayerRef.of(UUID.randomUUID(), "Bob");
        ChatMessage liveAlice = message("Alice live"), liveBob = message("Bob live");
        ChatMessage oldAlice = message("Alice old"), oldBob = message("Bob old");
        PrivateChatStore.setActive(alice);
        PrivateChatStore.addIncoming(alice, liveAlice);
        PrivateChatStore.addIncoming(bob, liveBob);
        var before = PrivateChatStore.messages(bob);
        long generation = PrivateChatStore.generation();
        AtomicInteger effects = new AtomicInteger();
        BiConsumer<ChatMessage, PlayerRef> listener = (message, partner) -> effects.incrementAndGet();
        ChatEffectEvents.install(listener);
        try {
            PrivateChatStore.prependLoaded(alice, List.of(oldAlice));
            PrivateChatStore.prependLoaded(bob, List.of(oldBob));
        } finally { ChatEffectEvents.uninstall(listener); }
        assertEquals(List.of(oldAlice, liveAlice), PrivateChatStore.messages(alice));
        assertEquals(List.of(oldBob, liveBob), PrivateChatStore.messages(bob));
        assertEquals(0, PrivateChatStore.unread(alice));
        assertEquals(1, PrivateChatStore.unread(bob));
        assertEquals(1, PrivateChatStore.totalUnread());
        assertSame(liveBob, PrivateChatStore.latest(bob));
        assertEquals(0, effects.get());
        assertTrue(PrivateChatStore.generation() > generation);
        assertNotSame(before, PrivateChatStore.messages(bob));
        PrivateChatStore.addIncoming(alice, message("Alice next"));
        assertEquals(0, PrivateChatStore.unread(alice));
        PrivateChatStore.addIncoming(bob, message("Bob next"));
        assertEquals(2, PrivateChatStore.unread(bob));
    }

    @Test void capsDiscardOldestLoadedRowsAndRetainAllNewerLiveRows() {
        List<ChatMessage> loaded = new ArrayList<>();
        for (int i = 0; i < 500; i++) loaded.add(message("old " + i));
        ChatMessage firstLive = message("live 1"), secondLive = message("live 2");
        PlayerRef partner = PlayerRef.of(null, "peer");
        ChatStore.get().addLoaded(List.of(firstLive, secondLive));
        PrivateChatStore.addLoaded(partner, List.of(firstLive, secondLive));
        ChatStore.get().prependLoaded(loaded);
        PrivateChatStore.prependLoaded(partner, loaded);
        for (List<ChatMessage> messages : List.of(ChatStore.get().snapshot(), PrivateChatStore.messages(partner))) {
            assertEquals(500, messages.size());
            assertSame(loaded.get(2), messages.get(0));
            assertSame(firstLive, messages.get(498));
            assertSame(secondLive, messages.get(499));
        }
        ChatStore.get().prependLoaded(List.of(message("too old")));
        PrivateChatStore.prependLoaded(partner, List.of(message("too old")));
        assertSame(loaded.get(2), ChatStore.get().snapshot().get(0));
        assertSame(loaded.get(2), PrivateChatStore.messages(partner).get(0));
    }

    private static ChatMessage message(String content) {
        ChatMessage echo = LocalEcho.build(content, content, null, null, null, "Tester", RichText.literal("Tester"));
        return new ChatMessage(echo.getComponent(), false);
    }
}
