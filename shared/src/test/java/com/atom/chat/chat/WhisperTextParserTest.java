package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperTextParserTest {
    private static final String OWN = "Notch";

    private static WhisperTextParser.WhisperHit parse(String line) {
        return WhisperTextParser.tryParse(line, OWN);
    }

    // ---- arrow family ----

    @Test
    void essentialsIncomingWithMePlaceholder() {
        var hit = parse("[Steve -> me] hello there");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hello there", hit.content());
    }

    @Test
    void essentialsIncomingWithOwnName() {
        var hit = parse("[Steve -> Notch] hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    @Test
    void outgoingArrowFromOwnName() {
        var hit = parse("[Notch -> Steve] hey");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hey", hit.content());
    }

    @Test
    void deluxeColonArrowIncoming() {
        var hit = parse("Steve -> me : hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hi", hit.content());
    }

    @Test
    void decoratedSideIsStripped() {
        var hit = parse("[§6Steve§r -> me] hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    // ---- CMI family ----

    @Test
    void cmiFromIsIncoming() {
        var hit = parse("[/msg from [Steve]] hello");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("hello", hit.content());
    }

    @Test
    void cmiSentIsOutgoing() {
        var hit = parse("[/msg sent -> [Steve]] hello");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    // ---- keyword family ----

    @Test
    void chineseKeywordIncoming() {
        var hit = parse("Steve 悄悄地对你说: 你好");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("你好", hit.content());
    }

    @Test
    void englishKeywordIncoming() {
        var hit = parse("Steve whispers to you: hi");
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    @Test
    void chineseKeywordOutgoingBeatsIncomingKeyword() {
        // The outgoing shape must not be mis-claimed as incoming from "你对X".
        var hit = parse("你对Steve悄悄地说：在吗");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("在吗", hit.content());
    }

    @Test
    void englishKeywordOutgoing() {
        var hit = parse("You whisper to Steve: brb");
        assertTrue(hit != null && !hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
    }

    // ---- rejection guards ----

    @Test
    void thirdPartyArrowIsIgnored() {
        assertNull(parse("[Alice -> Bob] secret channel talk"));
    }

    @Test
    void normalChatIsNotClaimed() {
        assertNull(parse("Steve: hello world"));
        assertNull(parse("<Steve> hello world"));
    }

    /**
     * The keyword family must not claim ordinary banter that merely contains a
     * whisper word. Reported from a real session: the server's own reply to the
     * {@code /atomchat test} command — "Sent a simulated whisper banner to your
     * client" — was routed into the private panel and popped a banner, because
     * the comma after the keyword was optional and the sender capture swallowed
     * the whole prefix. Nothing about a whisper shape survives without either a
     * colon or a resolvable sender on a single token.
     */
    @Test
    void whisperWordInOrdinaryTextIsNotClaimed() {
        assertNull(parse("Sent a simulated whisper banner to your client"));
        assertNull(parse("Sent a simulated quote banner to your client"));
        assertNull(parse("The whisper mechanic is broken"));
        assertNull(parse("已发送模拟私聊横幅（客户端）"));
        assertNull(parse("有人问我悄悄话是什么意思"));
    }

    @Test
    void keywordNeedsAColonBeforeTheBody() {
        // No colon: the "sender" would be a whole prose phrase, never a name.
        assertNull(parse("Steve whispers to you hi"));
        assertNull(parse("Steve 悄悄地对你说 你好"));
    }

    /**
     * Multi-word and decorated senders stay claimable — the tightening forbids a
     * separator inside the sender, not whitespace. These are real formats (a
     * nickname with a space, a plugin's tag after the name), and the anchored
     * fallback cannot rescue them for a first-time sender.
     */
    @Test
    void keywordStillClaimsDecoratedSenders() {
        var spaced = parse("Steve the Great whispers to you: hi");
        assertTrue(spaced != null && spaced.incoming());
        assertEquals("Steve the Great", spaced.partnerDisplay());
        assertEquals("hi", spaced.content());

        var tagged = parse("Steve [Admin] 悄悄地对你说: 你好");
        assertTrue(tagged != null && tagged.incoming());
        assertEquals("你好", tagged.content());
    }

    /**
     * The anchored family must not claim a keyword-bearing line that has no
     * separator at all: doing so used to hand the keyword itself back as part of
     * the message body, so a colon-less line arrived in the private panel with
     * "悄悄地对你说" glued to the front of its text.
     */
    @Test
    void anchoredRejectsAKeywordLineWithNoSeparator() {
        assertNull(WhisperTextParser.tryParseAnchored("Steve 悄悄地对你说 你好", java.util.List.of("Steve")));
        assertTrue(WhisperTextParser.tryParseAnchored("Steve 悄悄地对你说: 你好", java.util.List.of("Steve")) != null,
                "with a colon the anchored family still claims it, with clean content");
    }

    @Test
    void broadcastIsNotClaimed() {
        assertNull(parse("Steve joined the game"));
        assertNull(parse("[服务器] 维护公告"));
    }

    @Test
    void nullAndOversizedLinesAreIgnored() {
        assertNull(WhisperTextParser.tryParse(null, OWN));
        assertNull(WhisperTextParser.tryParse("x".repeat(300), OWN));
        assertFalse(WhisperTextParser.tryParse("[Steve -> me] hi", null) == null);
    }

    // ---- anchored family (e33chat WhisperDetector port) ----

    @Test
    void anchoredKeywordFamilyClaimsIncoming() {
        var hit = WhisperTextParser.tryParseAnchored("[VIP] Steve 私聊说: 你好",
                java.util.List.of("Alice", "Steve"));
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve", hit.partnerDisplay());
        assertEquals("你好", hit.content());
    }

    @Test
    void anchoredEnglishKeywordWorks() {
        var hit = WhisperTextParser.tryParseAnchored("Steve whispers: hi",
                java.util.List.of("Steve"));
        assertTrue(hit != null && hit.incoming());
        assertEquals("hi", hit.content());
    }

    @Test
    void anchoredRequiresKeyword() {
        assertNull(WhisperTextParser.tryParseAnchored("Steve: hello world",
                java.util.List.of("Steve")));
    }

    @Test
    void anchoredRequiresNameNearStart() {
        assertNull(WhisperTextParser.tryParseAnchored(
                "x".repeat(30) + "Steve 悄悄地对你说: hi",
                java.util.List.of("Steve")));
    }

    @Test
    void anchoredPrefersLongestName() {
        var hit = WhisperTextParser.tryParseAnchored("Steve2 悄悄地对你说: hi",
                java.util.List.of("Steve", "Steve2"));
        assertTrue(hit != null && hit.incoming());
        assertEquals("Steve2", hit.partnerDisplay());
    }
}
