package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SkiaFontWordWrapTest {
    @Test
    void latinTextBreaksAtSpacesBeforeSplittingAWord() {
        List<String> lines = SkiaFontRenderer.wrapWords(
                "message is actually sent, close through", 22.0F,
                s -> (float) s.length());
        assertEquals(List.of("message is actually", "sent, close through"), lines);
        for (String line : lines) {
            assertFalse(line.endsWith("thro"), "a regular word is never severed at the line edge");
        }
    }

    @Test
    void aWordWiderThanTheLineStillWrapsInsteadOfOverflowing() {
        assertEquals(List.of("abc", "def"), SkiaFontRenderer.wrapWords(
                "abcdef", 3.0F, s -> (float) s.length()));
    }

    @Test
    void onlyTheSpaceAtEachLineBoundaryIsElided() {
        String source = "message is actually sent, close through";
        // At widths below the longest word, character splitting is expected;
        // this property is about ordinary word boundaries.
        for (float width = 12.0F; width <= 45.0F; width += 1.0F) {
            List<String> lines = SkiaFontRenderer.wrapWords(
                    source, width, s -> (float) s.length());
            assertEquals(source, String.join(" ", lines),
                    "width " + width + " must not lose an in-word or inter-word space: " + lines);
            for (String line : lines) {
                assertFalse(line.startsWith(" ") || line.endsWith(" "),
                        "a rendered line never carries a boundary-only space: '" + line + "'");
            }
        }
    }
}
