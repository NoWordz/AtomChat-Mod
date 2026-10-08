package net.minecraft.client.gui.screen;

import com.atom.chat.chat.ChatMessage;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A banner click only dismisses once its message has been located in the target
 * page's feed, so the locate step is the half of that contract a test can still
 * pin without a live {@code MinecraftClient}: {@link AtomChatScreen#findMessageIndex}
 * is a pure scan over the feed.
 *
 * <p>Anti-spam merges replace the stored row object after the banner was queued,
 * so the identity-miss fallback (same content and sender, different object) is
 * what decides whether a jump lands or the banner stays on screen with the
 * message never highlighted.
 */
class AtomChatScreenMessageLookupTest {
    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static ChatMessage message(String content, boolean own, UUID sender, String name) {
        return new ChatMessage(Text.literal(content), own, false, null, null, sender, name, name, content);
    }

    @Test
    void theSameRowObjectResolvesToItsOwnSlot() {
        ChatMessage first = message("hi", false, ALICE, "Alice");
        ChatMessage second = message("yo", false, ALICE, "Alice");
        assertEquals(1, AtomChatScreen.findMessageIndex(List.of(first, second), second));
    }

    @Test
    void mergedCopyFallsBackToTheLastMatchingRow() {
        ChatMessage first = message("hi", false, ALICE, "Alice");
        ChatMessage second = message("hi", false, ALICE, "Alice");
        ChatMessage queued = message("hi", false, ALICE, "Alice");
        assertEquals(1, AtomChatScreen.findMessageIndex(List.of(first, second), queued));
    }

    @Test
    void anotherSendersIdenticalTextIsNotTheTarget() {
        ChatMessage fromAlice = message("hi", false, ALICE, "Alice");
        ChatMessage fromBob = message("hi", false, BOB, "Bob");
        assertEquals(-1, AtomChatScreen.findMessageIndex(List.of(fromAlice), fromBob));
    }

    @Test
    void ownFlagMismatchIsNotTheTarget() {
        ChatMessage own = message("hi", true, ALICE, "Alice");
        ChatMessage other = message("hi", false, ALICE, "Alice");
        assertEquals(-1, AtomChatScreen.findMessageIndex(List.of(own), other));
    }

    @Test
    void quoteMismatchIsNotTheTarget() {
        ChatMessage quoted = new ChatMessage(Text.literal("hi"), false, false, "Bob", "yo",
                ALICE, "Alice", "Alice", "hi");
        ChatMessage plain = message("hi", false, ALICE, "Alice");
        assertEquals(-1, AtomChatScreen.findMessageIndex(List.of(quoted), plain));
    }

    @Test
    void aMessageThatLeftTheFeedReturnsMinusOne() {
        ChatMessage first = message("hi", false, ALICE, "Alice");
        ChatMessage second = message("yo", false, ALICE, "Alice");
        ChatMessage gone = message("gone", false, BOB, "Bob");
        assertEquals(-1, AtomChatScreen.findMessageIndex(List.of(first, second), gone));
    }

    @Test
    void anEmptyFeedReturnsMinusOne() {
        ChatMessage queued = message("hi", false, ALICE, "Alice");
        assertEquals(-1, AtomChatScreen.findMessageIndex(List.of(), queued));
    }
}
