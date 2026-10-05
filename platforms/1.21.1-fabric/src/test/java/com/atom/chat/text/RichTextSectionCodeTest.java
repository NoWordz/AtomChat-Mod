package com.atom.chat.text;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The section-sign cases where the Fabric and Forge parsers disagreed. The
 * agreed reference is Forge behaviour, so these are the values both layers must
 * produce.
 *
 * <p>Why this exists: the older tests only covered "§aHello §rWorld" and
 * "§aA§cB", and both parsers happen to agree on those, so the drift below had
 * no guard. A parser that swallows an unrecognised code, drops a trailing lone
 * "§", or treats a colour code as an exclusive style reset passes those two
 * tests while rendering a different message than the Forge targets do.
 *
 * <p>The literal "§" is used rather than {@code Formatting.FORMATTING_CODE_PREFIX}
 * because it is what arrives from the server as chat content.
 */
class RichTextSectionCodeTest {

    @Test
    void unknownSectionCodeIsKeptLiterally() {
        RichText rich = RichText.of(Text.literal("A§zB"));

        assertEquals("A§zB", rich.getString());
        // The unrecognised pair is plain text in the style already in effect, so
        // it must not become a run of its own.
        assertEquals(1, rich.runs().size());
    }

    @Test
    void unknownSectionCodeKeepsTheColourInEffect() {
        RichText rich = RichText.of(Text.literal("§aA§zB"));

        assertEquals("A§zB", rich.getString());
        assertEquals(1, rich.runs().size());
        assertEquals(Formatting.GREEN.getColorValue().intValue(),
                rich.runs().get(0).style().getColor().getRgb());
    }

    @Test
    void loneTrailingSectionSignIsKept() {
        RichText rich = RichText.of(Text.literal("done§"));

        assertEquals("done§", rich.getString());
        assertEquals(1, rich.runs().size());
    }

    @Test
    void colourCodeKeepsTheFormatFlagsSetBeforeIt() {
        RichText rich = RichText.of(Text.literal("§l§aX"));

        assertEquals("X", rich.getString());
        assertEquals(1, rich.runs().size());
        Style style = rich.runs().get(0).style();
        assertEquals(Formatting.GREEN.getColorValue().intValue(), style.getColor().getRgb());
        assertTrue(style.isBold(), "a colour code must not clear the bold flag set before it");
    }

    @Test
    void formatCodeKeepsTheColourSetBeforeIt() {
        RichText rich = RichText.of(Text.literal("§a§lX"));

        assertEquals("X", rich.getString());
        assertEquals(1, rich.runs().size());
        Style style = rich.runs().get(0).style();
        assertEquals(Formatting.GREEN.getColorValue().intValue(), style.getColor().getRgb());
        assertTrue(style.isBold());
    }
}
