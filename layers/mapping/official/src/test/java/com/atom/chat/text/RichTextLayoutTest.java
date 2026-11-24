package com.atom.chat.text;

import com.atom.chat.text.RichTextLayout.RichLine;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextLayoutTest {
    private static final TextMeasurer CHAR_10 = s -> s.length() * 10f;

    @Test
    void wrapSplitsWithoutBreakingStyles() {
        RichText text = RichText.literal("abcdef");
        List<RichLine> lines = RichTextLayout.wrap(text, CHAR_10, 30f);
        assertEquals(2, lines.size());
        assertEquals("abc", lines.get(0).getPlainText());
        assertEquals("def", lines.get(1).getPlainText());
        assertEquals(3, lines.get(1).textStart());
    }

    @Test
    void charAtMapsLocalXToIndex() {
        RichLine line = RichTextLayout.wrap(RichText.literal("abc"), CHAR_10, 100f).get(0);
        assertEquals(1, RichTextLayout.charAt(line, CHAR_10, 15f));
    }

    @Test
    void wrapSlicesRunsPreservingStyles() {
        Style red = Style.EMPTY.withColor(0xFF0000);
        Style blue = Style.EMPTY.withColor(0x0000FF);
        RichText text = RichText.of(Component.literal("ab").setStyle(red).append(Component.literal("cdef").setStyle(blue)));
        List<RichLine> lines = RichTextLayout.wrap(text, CHAR_10, 30f);

        assertEquals("abc", lines.get(0).getPlainText());
        assertEquals(2, lines.get(0).runs().size());
        assertEquals(red, lines.get(0).runs().get(0).style());
        assertEquals("ab", lines.get(0).runs().get(0).text());
        assertEquals(blue, lines.get(0).runs().get(1).style());
        assertEquals("c", lines.get(0).runs().get(1).text());
        assertEquals(1, lines.get(1).runs().size());
        assertEquals("def", lines.get(1).runs().get(0).text());
        assertEquals(blue, lines.get(1).runs().get(0).style());
    }

    @Test
    void wrapTreatsNewlineAsHardLineBreak() {
        List<RichLine> lines = RichTextLayout.wrap(RichText.literal("a\nb"), CHAR_10, 100f);

        assertEquals(2, lines.size());
        assertEquals("a", lines.get(0).getPlainText());
        assertEquals("b", lines.get(1).getPlainText());
        assertEquals(0, lines.get(0).textStart());
        assertEquals(1, lines.get(0).textEnd());
        assertEquals(2, lines.get(1).textStart());
        assertEquals(3, lines.get(1).textEnd());
    }

    @Test
    void wrapDoesNotEmitNewlineAsVisibleGlyph() {
        List<RichLine> lines = RichTextLayout.wrap(RichText.literal("a\nb"), CHAR_10, 15f);

        assertEquals(2, lines.size());
        assertTrue(lines.stream().noneMatch(l -> l.getPlainText().contains("\n")));
        assertEquals("a", lines.get(0).getPlainText());
        assertEquals("b", lines.get(1).getPlainText());
    }

    @Test
    void wrapSkipsEmptyNewlineLines() {
        List<RichLine> lines = RichTextLayout.wrap(RichText.literal("a\n\nb"), CHAR_10, 100f);

        assertEquals(2, lines.size());
        assertEquals("a", lines.get(0).getPlainText());
        assertEquals("b", lines.get(1).getPlainText());
    }

    @Test
    void wrapDropsSpaceAtLineBreak() {
        RichText text = RichText.literal("abc def");
        List<RichLine> lines = RichTextLayout.wrap(text, CHAR_10, 30f);

        assertEquals(2, lines.size());
        assertEquals("abc", lines.get(0).getPlainText());
        assertEquals("def", lines.get(1).getPlainText());
        assertEquals(3, lines.get(0).textEnd());
        assertEquals(4, lines.get(1).textStart());
    }

    @Test
    void wrapKeepsSpacesThatFitInsideLine() {
        RichText text = RichText.literal("a b c");
        List<RichLine> lines = RichTextLayout.wrap(text, CHAR_10, 100f);

        assertEquals(1, lines.size());
        assertEquals("a b c", lines.get(0).getPlainText());
        assertEquals(0, lines.get(0).textStart());
        assertEquals(5, lines.get(0).textEnd());
    }

    @Test
    void wrapDoesNotSplitSurrogatePair() {
        RichText text = RichText.literal("a\uD83D\uDE00b");
        List<RichLine> lines = RichTextLayout.wrap(text, CHAR_10, 30f);

        assertEquals("a\uD83D\uDE00", lines.get(0).getPlainText());
        assertEquals(3, lines.get(0).textEnd());
        assertEquals("b", lines.get(1).getPlainText());
        assertEquals(3, lines.get(1).textStart());
    }

    @Test
    void wrapAllowsOversizedCodePointOnEmptyLine() {
        RichText text = RichText.literal("\uD83D\uDE00");
        List<RichLine> lines = RichTextLayout.wrap(text, CHAR_10, 15f);

        assertEquals(1, lines.size());
        assertEquals("\uD83D\uDE00", lines.get(0).getPlainText());
    }

    @Test
    void wrapEmptyTextProducesNoLines() {
        assertTrue(RichTextLayout.wrap(RichText.empty(), CHAR_10, 100f).isEmpty());
        assertTrue(RichTextLayout.wrap(RichText.literal(""), CHAR_10, 100f).isEmpty());
    }

    @Test
    void charAtReturnsAbsoluteIndexOnLaterLine() {
        List<RichLine> lines = RichTextLayout.wrap(RichText.literal("abcdef"), CHAR_10, 30f);
        assertEquals(4, RichTextLayout.charAt(lines.get(1), CHAR_10, 15f));
    }

    @Test
    void charAtClampsToLineBounds() {
        List<RichLine> lines = RichTextLayout.wrap(RichText.literal("abcdef"), CHAR_10, 30f);
        RichLine first = lines.get(0);
        RichLine second = lines.get(1);

        assertEquals(0, RichTextLayout.charAt(first, CHAR_10, -10f));
        assertEquals(3, RichTextLayout.charAt(first, CHAR_10, 100f));
        assertEquals(3, RichTextLayout.charAt(second, CHAR_10, -10f));
        assertEquals(6, RichTextLayout.charAt(second, CHAR_10, 100f));
    }

    @Test
    void charAtDoesNotReturnSecondSurrogateHalf() {
        RichText text = RichText.literal("\uD83D\uDE00b");
        RichLine line = RichTextLayout.wrap(text, CHAR_10, 100f).get(0);

        // Emoji occupies x [0, 20), b occupies x [20, 30).
        assertEquals(0, RichTextLayout.charAt(line, CHAR_10, 15f));
        assertEquals(2, RichTextLayout.charAt(line, CHAR_10, 20f));
        assertEquals(2, RichTextLayout.charAt(line, CHAR_10, 25f));
    }
    @Test void backwardsAdvanceKeepsNativeSpacingAndLastPaintedHit() {
        TextMeasurer nativeSpacing=new TextMeasurer(){
            public float measure(String text){float x=0;for(char cp:text.toCharArray())x+=cp==' '?-8:10;return x;}
            public boolean collapsibleSpace(RichText.RichRun source){return false;}
        };
        var line=RichTextLayout.wrap(RichText.literal("A B"),nativeSpacing,100).get(0);
        assertEquals("A B",line.getPlainText());
        assertEquals(12,RichTextLayout.width(line,nativeSpacing));
        assertEquals(2,RichTextLayout.advanceTo(line,nativeSpacing,2));
        assertEquals(2,RichTextLayout.charAt(line,nativeSpacing,3),"later glyph over the negative space wins");
        assertEquals(2,RichTextLayout.caretAt(line,nativeSpacing,2));
        var wrapped=RichTextLayout.wrap(RichText.literal("A  B"),nativeSpacing,10);
        assertTrue(wrapped.stream().anyMatch(value->value.getPlainText().contains("  ")));
    }
}
