package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichChatPartsTest {
    @Test
    void slicesDecoratedLine() {
        Text line = Text.literal("[萌新]player>>谁能给我钻石？")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg player ")));
        RichChatParts parts = ChatPipeline.sliceRichText(line, new SenderMeta(null, "player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals("[萌新]player", parts.sender().getString());
        assertEquals("谁能给我钻石？", parts.content().getString());
    }

    @Test
    void slicePreservesSenderStylesFromDecoratedLine() {
        Style click = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg player "));
        Text line = Text.literal("[萌新]player>>谁能给我钻石？").setStyle(click);
        RichChatParts parts = ChatPipeline.sliceRichText(line, new SenderMeta(null, "player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals(click, parts.sender().runs().get(0).style());
    }

    @Test
    void slicesWhenMetaCarriesDecoratedSenderName() {
        Text line = Text.literal("[萌新]player>>谁能给我钻石？");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "[萌新]player", "player", "谁能给我钻石？", false))
                .orElseThrow();
        assertEquals("[萌新]player", parts.sender().getString());
        assertEquals("谁能给我钻石？", parts.content().getString());
    }

    @Test
    void sliceKeepsBracketSuffixDecorationInSender() {
        Text line = Text.literal("[VIP]Steve[AFK] >> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve[AFK]", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceKeepsParenthesizedSuffixDecorationInSender() {
        Text line = Text.literal("Steve(VIP) : hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve(VIP)", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceContentLinkifiesBareUrls() {
        Text line = Text.literal("[萌新]player>>see https://example.com/x now");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "player", "player", "see https://example.com/x now", false))
                .orElseThrow();
        assertEquals("see https://example.com/x now", parts.content().getString());
        assertTrue(parts.content().runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == ClickEvent.Action.OPEN_URL));
    }

    @Test
    void sliceAngleLineSenderDropsVanillaAngleBrackets() {
        Text line = Text.literal("<Steve> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceAngleLineSenderKeepsRunStyles() {
        // The angle branch used to synthesise a literal label, dropping the
        // line's colours — senders all rendered in the plain text colour.
        Style green = Style.EMPTY.withColor(0x55FF55);
        Text line = Text.literal("<")
                .append(Text.literal("Steve").setStyle(green))
                .append(Text.literal("> hi"));
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve", parts.sender().getString());
        assertTrue(parts.sender().runs().stream().anyMatch(r -> green.equals(r.style())));
    }

    @Test
    void slicePrefixedAngleLineSenderKeepsPrefixOnly() {
        Text line = Text.literal("[VIP]<Steve> hi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void sliceTeamDecoratedAngleLineStripsBracketsKeepsStyles() {
        // "<[称号]E33EPUS> 453": the wrapping pair is dropped, the inner team
        // colour survives the slice (e33chat cleanNameArea parity).
        Style orange = Style.EMPTY.withColor(0xFFA500);
        Text line = Text.literal("<")
                .append(Text.literal("[称号]").setStyle(orange))
                .append(Text.literal("E33EPUS"))
                .append(Text.literal("> 453"));
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "E33EPUS", "E33EPUS", "453", false))
                .orElseThrow();
        assertEquals("[称号]E33EPUS", parts.sender().getString());
        assertEquals("453", parts.content().getString());
        assertTrue(parts.sender().runs().stream().anyMatch(r -> orange.equals(r.style())));
    }

    @Test
    void slicesLegacyFormattedAngleLineUsingVisibleOffsets() {
        Text line = Text.literal("§a<Steve> §bhi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void slicesLegacyDecoratedPrefixLineUsingVisibleOffsets() {
        Text line = Text.literal("§7[VIP]§rSteve>>§ahi");
        RichChatParts parts = ChatPipeline.sliceRichText(line,
                        new SenderMeta(null, "Steve", "Steve", "hi", false))
                .orElseThrow();
        assertEquals("[VIP]Steve", parts.sender().getString());
        assertEquals("hi", parts.content().getString());
    }

    @Test
    void quoteBodySlicesStyledReplyBody() {
        Style orange = Style.EMPTY.withColor(0xFF8800);
        Text line = Text.literal("「引用 @Steve: hello」")
                .append(Text.literal("@[称号]E33EPUS").setStyle(orange))
                .append(Text.literal(" got it"));
        RichText body = ChatPipeline.quoteBodyRich(RichText.of(line), "@[称号]E33EPUS got it");
        assertEquals("@[称号]E33EPUS got it", body.getString());
        assertTrue(body.runs().stream().anyMatch(r -> r.style().getColor() != null
                && r.style().getColor().getRgb() == 0xFF8800));
    }

    @Test
    void quoteBodyDoesNotLeakSenderStyles() {
        Style orange = Style.EMPTY.withColor(0xFF8800);
        Text line = Text.literal("[VIP]")
                .append(Text.literal("Steve").setStyle(orange))
                .append(Text.literal("> 「引用 @Bob: yo」body text"));
        RichText body = ChatPipeline.quoteBodyRich(RichText.of(line), "body text");
        assertEquals("body text", body.getString());
        assertTrue(body.runs().stream().allMatch(r -> r.style().getColor() == null));
    }

    @Test
    void quoteBodyFallsBackWhenVisibleTextDiffers() {
        RichText source = RichText.literal("「引用 @Steve: hello」server text");
        RichText body = ChatPipeline.quoteBodyRich(source, "client text");
        assertEquals("client text", body.getString());
        assertTrue(body.runs().stream().allMatch(r -> r.style().getColor() == null));
    }

    @Test
    void quoteBodyTrimsSurroundingWhitespace() {
        RichText source = RichText.literal("「引用 @Steve: hello」   reply   ");
        assertEquals("reply", ChatPipeline.quoteBodyRich(source, "reply").getString());
    }

    @Test
    void quoteBodyWithoutPrefixFallsBackToLiteral() {
        assertEquals("plain body",
                ChatPipeline.quoteBodyRich(RichText.literal("no quote at all"), "plain body").getString());
    }

    @Test
    void quoteBodyKeepsUrlLinks() {
        RichText source = RichText.literal("「引用 @Steve: hello」see https://example.com/x now");
        RichText body = ChatPipeline.quoteBodyRich(source, "see https://example.com/x now");
        assertTrue(body.runs().stream().anyMatch(r -> r.style().getClickEvent() != null));
    }

    @Test
    void quoteBodyHandlesMissingRichSource() {
        assertEquals("body", ChatPipeline.quoteBodyRich(null, "body").getString());
    }

    @Test
    void emptyWhenMetaHasNoUsableName() {
        Text line = Text.literal("[系统]公告: 欢迎");
        assertTrue(ChatPipeline.sliceRichText(line, new SenderMeta(null, null, null, null, true)).isEmpty());
    }

    // ------------------------------------------------------------- quote capsule parts

    @Test
    void quotePartsSliceStyledNameAndTextFromTheOriginalLine() {
        Style orange = Style.EMPTY.withColor(0xFF8800);
        Text line = Text.literal("[VIP]Steve> 「引用 @")
                .append(Text.literal("[称号]E33EPUS").setStyle(orange))
                .append(Text.literal(": hello」got it"));
        ChatPipeline.QuoteRichParts parts =
                ChatPipeline.quotePartsRich(RichText.of(line), "[称号]E33EPUS", "hello");
        assertEquals("[称号]E33EPUS", parts.name().getString());
        assertEquals("hello", parts.text().getString());
        assertTrue(parts.name().runs().stream().anyMatch(r -> orange.equals(r.style())));
    }

    @Test
    void quotePartsKeepAnExplicitColourOnTheQuotedText() {
        Style red = Style.EMPTY.withColor(0xFF0000);
        Text line = Text.literal("「引用 @Steve: ")
                .append(Text.literal("hello").setStyle(red))
                .append(Text.literal("」body"));
        ChatPipeline.QuoteRichParts parts = ChatPipeline.quotePartsRich(RichText.of(line), "Steve", "hello");
        assertTrue(parts.text().runs().stream().anyMatch(r -> red.equals(r.style())));
    }

    @Test
    void quotePartsLeaveColourlessRunsColourless() {
        // Nothing explicit on the wire: the draw layer's capsule colour is the
        // fallback, so the data layer must not invent white here.
        ChatPipeline.QuoteRichParts parts = ChatPipeline.quotePartsRich(
                RichText.literal("「引用 @Steve: hello」body"), "Steve", "hello");
        assertFalse(parts.name().hasColor());
        assertFalse(parts.text().hasColor());
    }

    @Test
    void quotePartsTakeTheTextInsideThePrefixNotTheBody() {
        // "hi" appears twice — as the quoted text and as the reply body. Only the
        // run inside 「」 belongs to the capsule.
        Text line = Text.literal("「引用 @Steve: ")
                .append(Text.literal("hi").setStyle(Style.EMPTY.withColor(0xFF0000)))
                .append(Text.literal("」hi"));
        ChatPipeline.QuoteRichParts parts = ChatPipeline.quotePartsRich(RichText.of(line), "Steve", "hi");
        assertEquals("hi", parts.text().getString());
        assertTrue(parts.text().runs().stream().anyMatch(r -> r.style().getColor() != null));
    }

    @Test
    void quotePartsFallBackWhenThePlainTextDrifted() {
        assertEquals(null, ChatPipeline.quotePartsRich(
                RichText.literal("「引用 @Steve: hello」body"), "Bob", "hello"));
        assertEquals(null, ChatPipeline.quotePartsRich(
                RichText.literal("「引用 @Steve: hello」body"), "Steve", "other text"));
    }

    @Test
    void quotePartsFallBackWithoutASource() {
        assertEquals(null, ChatPipeline.quotePartsRich(null, "Steve", "hello"));
        assertEquals(null, ChatPipeline.quotePartsRich(RichText.literal("no quote here"), "Steve", "hello"));
    }

    @Test
    void quotePartsFallBackOnAHardNewline() {
        // A capsule is one line: a wire that put a newline inside the quote keeps
        // the plain string pill instead of spilling a second line.
        assertEquals(null, ChatPipeline.quotePartsRich(
                RichText.literal("「引用 @Steve: hel\nlo」body"), "Steve", "hel\nlo"));
    }
}
