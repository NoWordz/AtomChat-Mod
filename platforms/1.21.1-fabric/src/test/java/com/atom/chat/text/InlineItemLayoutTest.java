package com.atom.chat.text;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.RichTextRenderer;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Font;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InlineItemLayoutTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapVanilla() {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    private static final Font FONT = FontManager.font(16);

    private RichText item(String text) {
        return RichText.of(Text.literal(text).setStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(new ItemStack(Items.DIAMOND))))));
    }

    @Test
    void anItemHoverReservesAnIconSlotWithoutChangingText() {
        RichText text = item("ab");
        var line = RichTextRenderer.wrapFor(text, FONT, 200).get(0);
        assertTrue(RichTextRenderer.width(FONT, line) >= SkiaFontRenderer.getStringWidth(FONT, "ab") + 16);
        assertEquals("ab", line.getPlainText());
        assertEquals("ab", text.getString());
    }

    @Test
    void quarkPaddingBecomesOneIconInsteadOfThreeVisibleSpaces() {
        var plain = RichTextRenderer.wrapFor(item("ab"), FONT, 200).get(0);
        var padded = RichTextRenderer.wrapFor(item("   ab"), FONT, 200).get(0);
        assertEquals(RichTextRenderer.width(FONT, plain), RichTextRenderer.width(FONT, padded), 0.01);
        assertEquals("   ab", padded.getPlainText());
    }

    @Test
    void wrappingAccountsForTheIconBeforeChoosingTheLine() {
        var lines = RichTextRenderer.wrapFor(item("abcd"), FONT,
                SkiaFontRenderer.getStringWidth(FONT, "abcd") + 0.1f);
        assertTrue(lines.size() > 1);
        assertEquals("abcd", lines.stream().map(RichTextLayout.RichLine::getPlainText).reduce("", String::concat));
    }

    @Test
    void contiguousColourChangesDoNotDuplicateTheItem() {
        var hover = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(new ItemStack(Items.DIAMOND)));
        RichText text = RichText.of(Text.literal("   a").setStyle(Style.EMPTY.withHoverEvent(hover))
                .append(Text.literal("b").setStyle(Style.EMPTY.withHoverEvent(hover).withColor(0x123456))));
        var line = RichTextRenderer.wrapFor(text, FONT, 200).get(0);
        assertEquals(1, line.parts().stream().filter(RichTextLayout.Part::isItem).count());
        assertEquals(RichTextRenderer.width(FONT, RichTextRenderer.wrapFor(item("ab"), FONT, 200).get(0)),
                RichTextRenderer.width(FONT, line), 0.01);
    }

    @Test
    void separatedItemOccurrencesGetIndependentSlots() {
        RichText text = RichText.concat(RichText.concat(item("a"), RichText.literal(" / ")), item("b"));
        var line = RichTextRenderer.wrapFor(text, FONT, 200).get(0);
        assertEquals(2, line.parts().stream().filter(RichTextLayout.Part::isItem).count());
        assertEquals("a / b", line.getPlainText());
    }

    @Test
    void iconHitTestingAndSelectionUseTheOriginalPaddedOffsets() {
        var line = RichTextLayout.wrapWithItems(item("   ab"), s -> s.length() * 10f, 100, 12).get(0);
        assertEquals(0, RichTextLayout.charAt(line, s -> s.length() * 10f, 6));
        assertEquals(3, RichTextLayout.charAt(line, s -> s.length() * 10f, 14));
        assertEquals(4, RichTextLayout.caretAt(line, s -> s.length() * 10f, 20));
        assertEquals(12, RichTextLayout.advanceTo(line, s -> s.length() * 10f, 3));
        assertEquals(22, RichTextLayout.advanceTo(line, s -> s.length() * 10f, 4));
        assertEquals(32, RichTextLayout.width(line, s -> s.length() * 10f));
    }

    @Test
    void wrappingMovesTheSlotAndFirstLabelCharacterTogether() {
        RichText text = RichText.concat(RichText.literal("xx "), item("   ab"));
        var lines = RichTextLayout.wrapWithItems(text, s -> s.length() * 10f, 40, 12);
        assertEquals(2, lines.size());
        assertEquals("xx", lines.get(0).getPlainText());
        assertEquals("   ab", lines.get(1).getPlainText());
        assertEquals(3, lines.get(1).textStart());
        assertEquals(6, RichTextLayout.charAt(lines.get(1), s -> s.length() * 10f, 14));
    }

    @Test
    void originalTextAndHoverSurviveSlicingAndRoundTrip() {
        RichText source = item("   ab");
        RichText restored = RichText.of(source.toText()).slice(3, 5);
        assertEquals("ab", restored.getString());
        assertEquals(1, RichTextRenderer.wrapFor(restored, FONT, 200).get(0).parts().stream()
                .filter(RichTextLayout.Part::isItem).count());
        assertEquals("   ab", source.getString());
    }

    @Test
    void narrowLinesStillKeepAnOversizedSlotWithItsFirstCharacter() {
        var lines = RichTextLayout.wrapWithItems(item("   ab"), s -> s.length() * 10f, 15, 12);
        assertEquals("   a", lines.get(0).getPlainText());
        assertEquals("b", lines.get(1).getPlainText());
    }
}
