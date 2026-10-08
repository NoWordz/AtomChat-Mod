package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionHighlighterTest {
    private static final int ORANGE = 0xFF8800;

    private static RichText decorated() {
        return RichText.of(Text.literal("[称号]").setStyle(Style.EMPTY.withColor(ORANGE))
                .append(Text.literal("E33EPUS").setStyle(Style.EMPTY.withColor(ORANGE))));
    }

    private static boolean hasColouredRun(RichText text, String snippet) {
        return text.runs().stream().anyMatch(run -> run.text().contains(snippet)
                && run.style().getColor() != null
                && run.style().getColor().getRgb() == ORANGE);
    }

    @Test
    void expandsProfileNameMentionWithDecoratedColour() {
        RichText result = MentionHighlighter.highlightLocal(
                RichText.literal("hi @E33EPUS!"), "E33EPUS", decorated());
        assertEquals("hi @[称号]E33EPUS!", result.getString());
        assertTrue(hasColouredRun(result, "E33EPUS"));
    }

    @Test
    void recoloursDecoratedPlainToken() {
        RichText result = MentionHighlighter.highlightLocal(
                RichText.literal("hi @[称号]E33EPUS!"), "E33EPUS", decorated());
        assertEquals("hi @[称号]E33EPUS!", result.getString());
        assertTrue(hasColouredRun(result, "E33EPUS"));
    }

    @Test
    void stylesEveryOccurrenceCaseInsensitively() {
        RichText result = MentionHighlighter.highlightLocal(
                RichText.literal("@E33EPUS and @e33epus"), "E33EPUS", decorated());
        assertEquals("@[称号]E33EPUS and @[称号]E33EPUS", result.getString());
        assertEquals(2, result.runs().stream()
                .filter(run -> run.style().getColor() != null).count());
    }

    @Test
    void leavesLongerNamesAndEmailShapesAlone() {
        assertEquals("@E33EPUS2", MentionHighlighter.highlightLocal(
                RichText.literal("@E33EPUS2"), "E33EPUS", decorated()).getString());
        assertEquals("x@E33EPUS", MentionHighlighter.highlightLocal(
                RichText.literal("x@E33EPUS"), "E33EPUS", decorated()).getString());
        assertEquals("no mention", MentionHighlighter.highlightLocal(
                RichText.literal("no mention"), "E33EPUS", decorated()).getString());
    }

    @Test
    void keepsSurroundingRunStylesAndLeavesAnExplicitlyColouredMentionAlone() {
        // The wire carried its own colour for the @Name run: a locally known
        // decoration must not repaint it (only colourless mentions are restored
        // with the local decorated name).
        Text content = Text.literal("red ").setStyle(Style.EMPTY.withColor(0xFF0000))
                .append(Text.literal("@E33EPUS").setStyle(Style.EMPTY.withColor(0x00FF00)));
        RichText result = MentionHighlighter.highlightLocal(RichText.of(content), "E33EPUS", decorated());
        assertEquals("red @E33EPUS", result.getString());
        assertEquals(0xFF0000, result.runs().get(0).style().getColor().getRgb());
        assertEquals(0x00FF00, result.runs().get(1).style().getColor().getRgb());
        assertFalse(hasColouredRun(result, "E33EPUS"));
    }

    @Test
    void leavesAMentionAloneWhenOnlyPartOfItIsColoured() {
        // The token spans two runs and one of them carries a colour: the whole
        // mention is left as the server sent it, so no half-recoloured token.
        RichText content = RichText.of(Text.literal("@E33E").setStyle(Style.EMPTY.withColor(0x00FF00))
                .append(Text.literal("PUS")));
        RichText result = MentionHighlighter.highlightLocal(content, "E33EPUS", decorated());
        assertEquals("@E33EPUS", result.getString());
        assertFalse(hasColouredRun(result, "E33EPUS"));
    }

    @Test
    void atSeparatorKeepsDefaultColourSemantics() {
        // The inserted "@" must stay colourless: the render layer's fallback
        // (bubble colour) is what paints it, never a hardcoded colour.
        RichText result = MentionHighlighter.highlightLocal(
                RichText.literal("hi @E33EPUS"), "E33EPUS", decorated());
        assertEquals("hi @[称号]E33EPUS", result.getString());
        // Everything ahead of the decorated name (the text plus the inserted "@")
        // stays colourless, so the render fallback paints the separator.
        assertTrue(result.runs().stream()
                .takeWhile(run -> !run.text().contains("[称号]"))
                .allMatch(run -> run.style().getColor() == null));
        assertTrue(hasColouredRun(result, "E33EPUS"));
    }

    @Test
    void leavesClickableLinkMentionsAlone() {
        Style link = Style.EMPTY.withColor(0x8888FF).withClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.com/@E33EPUS"));
        RichText content = RichText.of(Text.literal("see https://example.com/@E33EPUS now").setStyle(link));
        RichText result = MentionHighlighter.highlightLocal(content, "E33EPUS", decorated());
        assertEquals("see https://example.com/@E33EPUS now", result.getString());
        assertFalse(result.runs().stream().anyMatch(run -> run.style().getColor() != null
                && run.style().getColor().getRgb() == ORANGE));
    }

    @Test
    void bareNameWithoutDecorationStaysPlain() {
        RichText result = MentionHighlighter.highlightLocal(
                RichText.literal("@E33EPUS"), "E33EPUS", RichText.literal("E33EPUS"));
        assertEquals("@E33EPUS", result.getString());
        assertFalse(result.hasColor());
    }
}
