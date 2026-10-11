package com.atom.chat.chat;

import com.atom.chat.nav.NavPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SendPipeline}'s pure half, offline: normalization, abbreviation, quote
 * previews and sender names.
 *
 * <p><b>Scope, stated up front:</b> the two send methods are NOT covered here.
 * They need a live client (a player connection, the chat HUD, a UUID), and
 * faking one is a red line — they stay on the real-machine probe list
 * ({@code /atomchat test banner|poke} covers the receive side; the send side is
 * the composer's Enter / send button). What is asserted here is everything that
 * decides what those methods would put on the wire.
 *
 * <p>The stub host exists only so a pipeline instance can be built; none of its
 * members is exercised by these tests — a deliberate signal that the send chain
 * itself is untested here rather than accidentally covered.
 */
class SendPipelineTest {
    private static final SendPipeline.Host NO_HOST = new SendPipeline.Host() {
        @Override
        public MinecraftClient client() {
            return null;
        }

        @Override
        public NavPage topNav() {
            return null;
        }

        @Override
        public ReplySlotStore replySlots() {
            return new ReplySlotStore();
        }

        @Override
        public void clearComposer() {
        }

        @Override
        public void focusComposer() {
        }

        @Override
        public void stickToBottom() {
        }
    };

    private static SendPipeline pipeline() {
        return new SendPipeline(NO_HOST);
    }

    @Test
    void normalizeInputTrimsAndCollapsesWhitespace() {
        assertEquals("hello world", SendPipeline.normalizeInput("  hello   world  "));
        assertEquals("hello world", SendPipeline.normalizeInput("hello\n\tworld"));
        assertEquals("", SendPipeline.normalizeInput("   "));
        assertEquals("", SendPipeline.normalizeInput(""));
        // A command keeps its slash — normalization must not eat it.
        assertEquals("/msg Bob hi", SendPipeline.normalizeInput("  /msg   Bob   hi "));
    }

    @Test
    void normalizeInputAppliesTheVanillaChatLengthLimit() {
        String longText = "x".repeat(400);
        String normalized = SendPipeline.normalizeInput(longText);
        assertTrue(normalized.length() < 400, "the chat limit truncates instead of sending 400 chars");
        assertTrue(normalized.length() <= 256, "the wire limit is vanilla's 256");
    }

    @Test
    void abbreviateOnlyShortensWhatIsTooLong() {
        assertEquals("abc", SendPipeline.abbreviate("abc", 5));
        assertEquals("abcde", SendPipeline.abbreviate("abcde", 5), "exactly at the limit is untouched");
        assertEquals("abc…", SendPipeline.abbreviate("abcdef", 3));
        assertEquals("…", SendPipeline.abbreviate("a", 0));
    }

    @Test
    void quoteTextForUsesTheImageStandInInsteadOfTheUrl() {
        String url = "https://example.com/a.png";
        ChatMessage image = new ChatMessage(Text.literal(
                "[[CICode,url=" + url + ",name=pic,w=100,h=50]]"), false);
        String quote = SendPipeline.quoteTextFor(image);
        assertNotNull(quote);
        assertNotEquals(url, quote, "an image message quotes as a stand-in, not as its URL");
        assertFalse(quote.contains("http"), "no URL fragment survives into the quote label");
        assertFalse(quote.contains("CICode"), "nor the CICode itself");
    }

    @Test
    void quoteTextForKeepsPlainTextAndAbbreviatesIt() {
        ChatMessage plain = new ChatMessage(Text.literal("a short line"), false);
        assertEquals("a short line", SendPipeline.quoteTextFor(plain));

        String longLine = "y".repeat(80);
        String quote = SendPipeline.quoteTextFor(new ChatMessage(Text.literal(longLine), false));
        assertEquals(31, quote.length(), "30 chars plus the ellipsis");
        assertTrue(quote.endsWith("…"));
        assertNull(SendPipeline.quoteTextFor(null));
    }

    @Test
    void senderNamePrefersTheCapturedNameAndFallsBackForUnknownSenders() {
        ChatMessage named = new ChatMessage(Text.literal("hi"), false, false, null, null,
                null, "Alice", "Alice", "hi", 1L);
        assertEquals("Alice", pipeline().messageSenderName(named));

        ChatMessage anonymous = new ChatMessage(Text.literal("hi"), false, false, null, null,
                null, null, null, "hi", 1L);
        String fallback = pipeline().messageSenderName(anonymous);
        assertNotNull(fallback);
        assertFalse(fallback.isBlank(), "an unknown sender still gets a label");
    }
}