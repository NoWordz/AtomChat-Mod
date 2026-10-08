package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageTest {
    @Test
    void capturedContentThatLooksLikeAngleChatIsNotStripped() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "<Alice> hi");
        assertEquals("<Alice> hi", msg.getContentText());
    }

    @Test
    void rawFallbackStillStripsVanillaSenderPrefix() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", null);
        assertEquals("hi", msg.getContentText());
    }

    @Test
    void richPartsAreStored() {
        RichText sender = RichText.literal("Alice");
        RichText content = RichText.literal("hi").linkifyUrls();
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "hi", sender, content);
        assertEquals("Alice", msg.getSenderRich().getString());
        assertEquals("hi", msg.getContentRich().getString());
    }

    @Test
    void legacyConstructorBuildsPlainRichParts() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "hi");
        assertEquals("Alice", msg.getSenderRich().getString());
        assertEquals("hi", msg.getContentRich().getString());
    }

    @Test
    void legacyConstructorLinkifiesSynthesizedContent() {
        ChatMessage msg = new ChatMessage(Component.literal("see https://example.com/x now"), false, false,
                null, null, null, "Alice", "Alice", "see https://example.com/x now");
        assertTrue(msg.getContentRich().runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == net.minecraft.network.chat.ClickEvent.Action.OPEN_URL));
        assertEquals("see https://example.com/x now", msg.getContentRich().getString());
    }

    @Test
    void getSenderNamePrefersRichSenderText() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "hi",
                RichText.literal("[VIP]Alice"), RichText.literal("hi"));
        assertEquals("[VIP]Alice", msg.getSenderName());
    }

    @Test
    void systemMessageHasEmptySenderRich() {
        ChatMessage msg = new ChatMessage(Component.literal("Server: hello"), false, true,
                null, null, null, null, null, null);
        assertTrue(msg.getSenderRich().isEmpty());
        assertEquals("Server: hello", msg.getContentRich().getString());
    }

    @Test
    void fullConstructorForcesEmptySenderRichForSystemMessages() {
        RichText sender = RichText.literal("Server");
        RichText content = RichText.literal("Server: hello");
        ChatMessage msg = new ChatMessage(Component.literal("Server: hello"), false, true,
                null, null, null, "Server", "Server", "Server: hello", sender, content);
        assertTrue(msg.getSenderRich().isEmpty());
    }

    @Test
    void displayTextComesFromRichContent() {
        ChatMessage msg = new ChatMessage(Component.literal("prefix"), false, false,
                null, null, null, "Alice", "Alice", "plain", RichText.literal("Alice"),
                RichText.literal("rich hi"));
        assertEquals("rich hi", msg.getDisplayText());
    }

    @Test
    void legacyQuotedConstructorPreservesDisplayText() {
        ChatMessage msg = new ChatMessage(Component.literal("「引用 @Alice: hi」hello"), true, "Alice", "hi");
        assertEquals("hello", msg.getDisplayText());
    }

    @Test
    void messagesHaveDistinctIds() {
        ChatMessage a = new ChatMessage(Component.literal("a"), false, false, null, null,
                null, "Alice", "Alice", "a");
        ChatMessage b = new ChatMessage(Component.literal("b"), false, false, null, null,
                null, "Alice", "Alice", "b");
        assertTrue(a.getId() != b.getId());
        assertTrue(a.sameAs(a));
        assertFalse(a.sameAs(b));
    }

    @Test
    void mergeCopyKeepsIdentity() {
        ChatMessage msg = new ChatMessage(Component.literal("hi"), false, false, null, null,
                null, "Alice", "Alice", "hi");
        ChatMessage merged = msg.withDuplicateCount(2, 999L);
        assertTrue(merged.sameAs(msg));
        assertTrue(msg.sameAs(merged));
        assertEquals(2, merged.getDuplicateCount());
        assertEquals(999L, merged.getTimestamp());
    }

    @Test
    void sameAsRejectsNull() {
        ChatMessage msg = new ChatMessage(Component.literal("a"), false, false, null, null,
                null, "Alice", "Alice", "a");
        assertFalse(msg.sameAs(null));
    }

    // ------------------------------------------------------- quote capsule rich parts

    @Test
    void quoteRichPartsAreSlicedFromTheOriginalLine() {
        Style orange = Style.EMPTY.withColor(0xFF8800);
        Component line = Component.literal("<Alice> 「引用 @")
                .append(Component.literal("Bob").setStyle(orange))
                .append(Component.literal(": hi」hello"));
        ChatMessage msg = new ChatMessage(line, false, false, "Bob", "hi",
                null, "Alice", "Alice", "hello");
        assertEquals("Bob", msg.getQuoteNameRich().getString());
        assertEquals("hi", msg.getQuoteTextRich().getString());
        assertTrue(msg.getQuoteNameRich().runs().stream().anyMatch(r -> orange.equals(r.style())));
    }

    @Test
    void quoteRichPartsStayColourlessWhenTheWireCarriedNoColour() {
        // The draw layer's capsule colour is the fallback; the data layer must
        // not turn "no explicit colour" into white.
        Component line = Component.literal("<Alice> 「引用 @Bob: hi」hello");
        ChatMessage msg = new ChatMessage(line, false, false, "Bob", "hi",
                null, "Alice", "Alice", "hello");
        assertEquals("Bob", msg.getQuoteNameRich().getString());
        assertFalse(msg.getQuoteNameRich().hasColor());
        assertFalse(msg.getQuoteTextRich().hasColor());
    }

    @Test
    void quoteRichPartsAreEmptyWithoutAQuotePrefix() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "hi");
        assertTrue(msg.getQuoteNameRich().isEmpty());
        assertTrue(msg.getQuoteTextRich().isEmpty());
    }

    @Test
    void mergeCopyKeepsTheQuoteRichParts() {
        Component line = Component.literal("<Alice> 「引用 @Bob: hi」hello");
        ChatMessage msg = new ChatMessage(line, false, false, "Bob", "hi",
                null, "Alice", "Alice", "hello");
        ChatMessage merged = msg.withDuplicateCount(3, 999L);
        assertEquals("Bob", merged.getQuoteNameRich().getString());
        assertEquals("hi", merged.getQuoteTextRich().getString());
        assertTrue(merged.sameAs(msg));
    }

    // ----------------------------------------------- legacy/history colour preservation

    @Test
    void legacyReloadKeepsTheOriginalContentColour() {
        // Persisted history rehydrates through the legacy constructor: the
        // component is restored from JSON, so its explicit run colour must
        // survive instead of collapsing into a plain literal.
        Style red = Style.EMPTY.withColor(0xFF0000);
        Component line = Component.literal("<Alice> ").append(Component.literal("hello").setStyle(red));
        ChatMessage msg = new ChatMessage(line, false, false, null, null,
                null, "Alice", "Alice", "hello", 12345L);
        assertEquals("hello", msg.getContentRich().getString());
        assertTrue(msg.getContentRich().runs().stream().anyMatch(r -> red.equals(r.style())));
    }

    @Test
    void legacyReloadKeepsTheOriginalSenderColour() {
        Style green = Style.EMPTY.withColor(0x55FF55);
        Component line = Component.literal("[VIP]").append(Component.literal("Alice").setStyle(green))
                .append(Component.literal("> hi"));
        ChatMessage msg = new ChatMessage(line, false, false, null, null,
                null, "Alice", "Alice", "hi", 12345L);
        assertTrue(msg.getSenderRich().runs().stream().anyMatch(r -> green.equals(r.style())));
    }

    @Test
    void legacyReloadInventsNoColourWhenTheLineHasNone() {
        Component line = Component.literal("<Alice> hi");
        ChatMessage msg = new ChatMessage(line, false, false, null, null,
                null, "Alice", "Alice", "hi", 12345L);
        assertFalse(msg.getSenderRich().hasColor());
        assertFalse(msg.getContentRich().hasColor());
    }

    @Test
    void legacyReloadDoesNotBorrowAColourFromAnAmbiguousMatch() {
        // "hi" appears twice in the line, so the content cannot be attributed to
        // one region: it falls back to a plain literal (the render layer's bubble
        // colour) instead of grabbing the "[hi]" decoration's colour.
        Style red = Style.EMPTY.withColor(0xFF0000);
        Component line = Component.literal("[")
                .append(Component.literal("hi").setStyle(red))
                .append(Component.literal("]Steve> hi"));
        ChatMessage msg = new ChatMessage(line, false, false, null, null,
                null, "Steve", "Steve", "hi", 12345L);
        assertEquals("hi", msg.getContentRich().getString());
        assertFalse(msg.getContentRich().hasColor());
    }

    @Test
    void suppliedColourlessRichPartsStayColourless() {
        // The private-chat text fallback hands over plain literals; nothing may
        // resurrect a colour that never existed.
        ChatMessage msg = new ChatMessage(Component.literal("x"), false, false, null, null,
                null, "Steve", "Steve", "hi", RichText.literal("Steve"), RichText.literal("hi"));
        assertFalse(msg.getSenderRich().hasColor());
        assertFalse(msg.getContentRich().hasColor());
    }
}
