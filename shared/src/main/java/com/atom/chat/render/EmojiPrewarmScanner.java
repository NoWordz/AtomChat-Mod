package com.atom.chat.render;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure scanner for the emoji codepoints worth prewarming.
 *
 * <p>It is deliberately independent of Skija and of the message model: the
 * caller hands it plain strings, sizes and colours. That keeps the selection
 * rule testable without a native library and keeps the actual glyph work in
 * {@link SkiaFontRenderer}.
 *
 * <p><b>Why single codepoints.</b> {@code SkiaFontRenderer.fontFor} resolves a
 * font per codepoint and caches it per codepoint, so a run that spans two
 * different emoji is split before it ever reaches the glyph cache. Prewarming
 * one codepoint at a time therefore matches the cache key the draw path will
 * actually use. Surrogate pairs are kept intact.
 */
final class EmojiPrewarmScanner {

    /** Upper bound for one warmup pass. 3 sizes x 5 colours x 12 glyphs fits. */
    static final int MAX_SPECS = 192;

    private EmojiPrewarmScanner() {
    }

    static List<EmojiPrewarmSpec> collect(Iterable<String> texts, float[] sizes, int[] colors) {
        LinkedHashSet<EmojiPrewarmSpec> out = new LinkedHashSet<>();
        if (texts == null || sizes == null || colors == null || sizes.length == 0 || colors.length == 0) {
            return List.of();
        }
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            for (int i = 0; i < text.length() && out.size() < MAX_SPECS; ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (!SkiaFontRenderer.isEmojiCodepoint(cp)) {
                    continue;
                }
                String glyph = new String(Character.toChars(cp));
                for (float size : sizes) {
                    for (int rgb : colors) {
                        if (out.size() >= MAX_SPECS) {
                            break;
                        }
                        out.add(new EmojiPrewarmSpec(size, glyph, rgb & 0xFFFFFF));
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }
}
