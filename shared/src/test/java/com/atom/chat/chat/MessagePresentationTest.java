package com.atom.chat.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagePresentationTest {
    @Test
    void parsesAngleBracketChat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<Steve> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().playerName());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void prefixedAngleBracketDisplayLabelKeepsPrefix() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[VIP]<Steve> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[VIP]Steve", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void parsesColonChat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve: hello world", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().playerName());
        assertEquals("hello world", line.get().content());
    }

    @Test
    void parsesBracketDecoratedColonChat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[VIP]Steve: hello", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[VIP]Steve", line.get().displayLabel());
        assertEquals("hello", line.get().content());
    }

    @Test
    void includesBracketSuffixDecorationInDisplayLabel() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[VIP]Steve[AFK] >> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[VIP]Steve[AFK]", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void includesParenthesizedSuffixDecorationInDisplayLabel() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve(VIP) : hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve(VIP)", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void longestNameWinsOverSubstring() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve2: hi", List.of("Steve", "Steve2"));
        assertTrue(line.isPresent());
        assertEquals("Steve2", line.get().playerName());
    }

    @Test
    void broadcastSentenceHasWhitespaceGap() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve joined the game", List.of("Steve"));
        assertTrue(line.isPresent());
        assertTrue(MessagePresentation.isWhitespaceOnlyGap(
                "Steve joined the game", line.get().nameEnd(), line.get().contentStart()));
    }

    @Test
    void broadcastLabelPrefixIsRejected() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[系统]Steve: hi", List.of("Steve"));
        assertTrue(line.isEmpty());
    }

    @Test
    void unknownNameIsNotParsed() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine("Alice: hi", List.of("Steve")).isEmpty());
    }

    @Test
    void hyphenatedLongerNameDoesNotMatchShortCandidate() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "Steve-Master: hi", List.of("Steve")).isEmpty());
    }

    @Test
    void suffixInsideAngleBracketsDoesNotMatchShortCandidate() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "<Notch> hi", List.of("tch")).isEmpty());
    }

    @Test
    void bracketOnlyKaomojiIsContentNotLabel() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<Steve> (￣▽￣)", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("(￣▽￣)", line.get().content());
    }

    @Test
    void bracketOnlyKaomojiColonFormat() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("Steve: (≧▽≦)", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("(≧▽≦)", line.get().content());
    }

    @Test
    void fullwidthBracketOnlyKaomojiIsContent() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<Steve> 【滑稽】", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("【滑稽】", line.get().content());
    }

    // ---- G3: names split by § color pairs inside the text ----

    @Test
    void colorCodeSplitNameStillMatches() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("S§6t§beve: hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().playerName());
        assertEquals("hi", line.get().content());
    }

    @Test
    void colorCodeSplitNameOffsetsStayRaw() {
        String text = "[VIP] S§6t§beve§r: hi there";
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine(text, List.of("Steve"));
        assertTrue(line.isPresent());
        // Offsets must be raw-line coordinates so RichText slicing keeps working.
        assertEquals('S', text.charAt(line.get().nameStart()));
        assertEquals('e', text.charAt(line.get().nameEnd() - 1));
        assertEquals("hi there", text.substring(line.get().contentStart()));
        assertEquals("[VIP] S§6t§beve§r", line.get().displayLabel());
    }

    @Test
    void colorCodeSplitNameLongerCandidateWinsOverSuffix() {
        // "Ste§6ve2" must not be claimed by candidate "Steve" (next-char check).
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "Ste§6ve2: hi", List.of("Steve")).isEmpty());
    }

    @Test
    void colorCodeSplitNameInsideAngleBrackets() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<S§6t§beve> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("Steve", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    // ---- e33chat cleanNameArea parity: whole decorated name inside <> ----

    @Test
    void teamDecoratedNameInsideAngleBracketsDropsWrappingPair() {
        // FTB Teams-style titles wrap the decorated name: "<[称号]E33EPUS> 453"
        // must read "[称号]E33EPUS", not "<[称号]E33EPUS" (0.2.5 hunt).
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<[称号]E33EPUS> 453", List.of("E33EPUS"));
        assertTrue(line.isPresent());
        assertEquals("[称号]E33EPUS", line.get().displayLabel());
        assertEquals("453", line.get().content());
    }

    @Test
    void latinDecoratedNameInsideAngleBracketsDropsWrappingPair() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("<[VIP]Steve> hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[VIP]Steve", line.get().displayLabel());
        assertEquals("hi", line.get().content());
    }

    @Test
    void fakeSeparatorPrefixInsideAnglesIsStillRejected() {
        // Broadcast-spoof guard wins: "<sys>E33EPUS: hi" (a separator before the
        // name) is not claimed as E33EPUS's line even though the angle shape fits.
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "<sys>E33EPUS: hi", List.of("E33EPUS")).isEmpty());
    }

    // ---- prose guard: plugin sentences about a player are not chat lines ----

    @Test
    void trChatChannelChatStillParses() {
        // TrChat Global/Normal flatten to "[Server/World] Name: message".
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("[生存]Steve: 大家好", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("[生存]Steve", line.get().displayLabel());
        assertEquals("大家好", line.get().content());
    }

    @Test
    void proseBeforeNameIsRejected() {
        // A plugin invitation sentence: the prose used to ride into the sender
        // label and only the tail reached the bubble (TrChat server report).
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "[公会] 你邀请了 Steve: 点击接受", List.of("Steve")).isEmpty());
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "欢迎来到服务器, Steve: 点此传送", List.of("Steve")).isEmpty());
    }

    @Test
    void englishProseBeforeNameIsRejected() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
                "You invited Steve: click to accept", List.of("Steve")).isEmpty());
    }

    @Test
    void singleStyledTagBeforeNameStaysDecoration() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("§6§lVIP §eSteve: hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("hi", line.get().content());
    }

    @Test
    void fullwidthParenDecorationStaysDecoration() {
        Optional<MessagePresentation.PlayerLine> line =
                MessagePresentation.parseDecoratedPlayerLine("（VIP）Steve: hi", List.of("Steve"));
        assertTrue(line.isPresent());
        assertEquals("hi", line.get().content());
    }
}
