package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Vanilla-style obfuscation keeps glyphs in equal-advance cells and reuses single-glyph blobs. */
final class ObfuscatedText {
    private static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!?%&*@#永恒星河天地玄黄日月山川风雨雷电";
    private static final Map<Font, Map<Integer, List<String>>> POOLS = new java.util.IdentityHashMap<>();
    static LongSupplier clock = () -> System.currentTimeMillis() / 50L;

    private ObfuscatedText() { }

    static float width(Font font, String text) {
        float width = 0F;
        for (int offset = 0; offset < text.length();) {
            int end = offset + Character.charCount(text.codePointAt(offset));
            width += cell(font, text.substring(offset, end));
            offset = end;
        }
        return width;
    }

    static void draw(Canvas canvas, Font font, String text, float x, float baseline, int color, long phase) {
        var pool = pool(font);
        int index = 0;
        for (int offset = 0; offset < text.length(); index++) {
            int codepoint = text.codePointAt(offset);
            int end = offset + Character.charCount(codepoint);
            String original = text.substring(offset, end);
            int advance = cell(font, original);
            var choices = Character.isWhitespace(codepoint) ? null : pool.get(advance);
            String glyph = original;
            if (choices != null && !choices.isEmpty()) {
                int selected = Math.floorMod(Long.hashCode(phase) + index * 17 + codepoint, choices.size());
                if (choices.size() > 1 && choices.get(selected).equals(original)) selected = (selected + 1) % choices.size();
                glyph = choices.get(selected);
            }
            if (advance > 0) PlainText.draw(canvas, font, glyph, x, baseline, color);
            x += advance;
            offset = end;
        }
    }

    private static int cell(Font font, String glyph) {
        return (int)Math.ceil(PlainText.width(font, glyph));
    }

    private static Map<Integer, List<String>> pool(Font font) {
        var result = POOLS.get(font);
        if (result != null) return result;
        if (POOLS.size() >= 64) POOLS.remove(POOLS.keySet().iterator().next());
        result = new java.util.HashMap<>();
        for (int offset = 0; offset < SYMBOLS.length();) {
            int end = offset + Character.charCount(SYMBOLS.codePointAt(offset));
            String glyph = SYMBOLS.substring(offset, end);
            result.computeIfAbsent(cell(font, glyph), ignored -> new ArrayList<>()).add(glyph);
            offset = end;
        }
        POOLS.put(font, result);
        return result;
    }
}
