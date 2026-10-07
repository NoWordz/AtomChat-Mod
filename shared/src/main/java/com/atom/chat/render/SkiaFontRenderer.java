package com.atom.chat.render;

import com.atom.chat.diagnostics.FrameProfile;
import com.atom.chat.theme.ThemeService;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Skia text renderer with Minecraft color-code support (ported from Tuui's FontRenderer).
 */
public final class SkiaFontRenderer {
    private static final int[] COLOR_CODE_RGB = new int[]{
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /** Per-codepoint+size cache of resolved fallback fonts (emoji / chars missing from the primary). */
    private static final java.util.Map<String, Font> FALLBACK_FONT_CACHE = new java.util.HashMap<>();

    /**
     * Primary-font glyph hits per Font instance: a set bit means the primary
     * carries the codepoint, so the hot path skips the JNI getUTF32Glyph
     * probe. Draw and measure each walk every codepoint (twice: the run outer
     * loop and the run-split inner loop), so a CJK-heavy frame used to
     * re-probe the same glyphs thousands of times per frame. Keyed by
     * instance, not size: {@code boldFont(size)} and {@code font(size)} are
     * different faces that must not share answers. FontManager caches its
     * Font objects for the process lifetime, so the map holds a handful of
     * entries; render-thread only, like FALLBACK_FONT_CACHE.
     */
    private static final java.util.Map<Font, java.util.BitSet> PRIMARY_GLYPH_HITS =
            new java.util.IdentityHashMap<>();

    private SkiaFontRenderer() {
    }

    /**
     * Bounded string→width cache. Text shaping is by far the hottest CPU path
     * in this UI: every label measures itself every frame (and truncation
     * measures repeatedly), each measurement walks the string code point by
     * code point resolving font fallbacks. Labels repeat across frames, so a
     * small LRU removes most of that work. Two levels — the font's size, then
     * the text — so a cache hit never builds a key: the old
     * {@code text + "@" + size} concat allocated a fresh string on every
     * measurement, hit or miss. Keyed by the font's size (not the instance),
     * exactly like the old key. That is an inherited trade-off, not a claim
     * that a size has one face: regular and bold of a size are distinct Fonts
     * (FontManager caches them in separate maps) with different metrics, so
     * they share a bucket and whichever measures first leaves a few pixels of
     * drift to the other. PRIMARY_GLYPH_HITS keys by instance because its
     * fallback answers are per-face truth.
     */
    private static final int WIDTH_CACHE_MAX = 4096;
    private static final java.util.Map<Float, java.util.Map<String, Float>> WIDTH_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    /** The per-size bucket for {@code font}, created (and the map swept) on first use. */
    private static java.util.Map<String, Float> widthCacheFor(Font font) {
        java.util.Map<String, Float> cache = WIDTH_CACHE.get(font.getSize());
        if (cache != null) {
            return cache;
        }
        // Wholesale clear past 16 sizes, like BLUR_FILTERS: call sites use a
        // handful of UiTokens sizes, so this never trips in practice — it only
        // keeps a pathological caller of arbitrary sizes bounded.
        if (WIDTH_CACHE.size() >= 16) {
            WIDTH_CACHE.clear();
        }
        cache = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(256, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Float> eldest) {
                return size() > WIDTH_CACHE_MAX;
            }
        });
        WIDTH_CACHE.put(font.getSize(), cache);
        return cache;
    }

    public static float getStringWidth(Font font, String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }
        java.util.Map<String, Float> cache = widthCacheFor(font);
        Float cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        // 只量没命中缓存的那条路：塑形是这里最贵的一段，而缓存命中只值一次哈希查找，
        // 把它算进"布局耗时"会把这条刻度稀释成没有意义。
        long startedAt = FrameProfile.start();
        float width = 0.0F;
        for (TextSegment segment : parseColoredText(text)) {
            if (!segment.text.isEmpty()) {
                width += measureRuns(font, segment.text);
            }
        }
        cache.put(text, width);
        FrameProfile.layout(startedAt);
        return width;
    }

    /**
     * Shortens {@code text} with a trailing ellipsis until it fits
     * {@code maxWidth}. Single-line labels only — callers that need wrapping
     * should use {@link #wrap(Font, String, float)} instead.
     */
    public static String truncate(Font font, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F || getStringWidth(font, text) <= maxWidth) {
            return text == null ? "" : text;
        }
        // Binary search over the prefix length: O(log n) measurements instead
        // of the O(n²) walk the per-character loop used to cost.
        String ellipsis = "…";
        int lo = 1;
        int hi = text.length() - 1;
        int best = 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (getStringWidth(font, text.substring(0, mid) + ellipsis) <= maxWidth) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        // Never cut a surrogate pair in half.
        while (best > 1 && Character.isLowSurrogate(text.charAt(best))) {
            best--;
        }
        if (best > 1 && Character.isHighSurrogate(text.charAt(best - 1))) {
            best--;
        }
        return text.substring(0, best) + ellipsis;
    }

    public static float getHeight(Font font) {
        var metrics = font.getMetrics();
        return metrics.getDescent() - metrics.getAscent() + metrics.getLeading();
    }

    public static float textHeight(Font font) {
        var metrics = font.getMetrics();
        return metrics.getDescent() - metrics.getAscent();
    }

    /**
     * Baseline y so that the text visual box is vertically centered at centerY.
     */
    public static float baselineY(Font font, float centerY) {
        var metrics = font.getMetrics();
        return centerY - (metrics.getAscent() + metrics.getDescent()) / 2.0F;
    }

    /**
     * Baseline so the visual glyph body is centered at centerY. Uses capHeight:
     * CJK fonts carry huge ascender space for diacritics, which pushes the plain
     * metrics-center formula visually up inside the bubble.
     */
    public static float centerBaselineY(Font font, float centerY) {
        var metrics = font.getMetrics();
        float capHeight = metrics.getCapHeight();
        if (capHeight > 0.0F) {
            return centerY + capHeight / 2.0F;
        }
        return baselineY(font, centerY);
    }

    /**
     * Centers text on centerX and vertically on centerY using the cap-height
     * baseline (see centerBaselineY) so it matches every other centered label.
     */
    public static void drawTextCentered(Canvas canvas, Font font, String text, float centerX, float centerY, int color) {
        drawTextCentered(canvas, font, text, centerX, centerY, color, false);
    }

    /**
     * Centered variant with the optional soft backing shadow, for capsule
     * labels (time dividers, image placeholders) whose surface tint can sit
     * close to the text colour. See {@link #drawText(Canvas, Font, String,
     * float, float, int, boolean)}.
     */
    public static void drawTextCentered(Canvas canvas, Font font, String text, float centerX, float centerY,
                                        int color, boolean backing) {
        drawText(canvas, font, text, centerX - getStringWidth(font, text) / 2.0F,
                centerBaselineY(font, centerY), color, backing);
    }

    /**
     * Centered variant whose shadow polarity comes from {@code shadowSurfaceArgb}
     * — pass the capsule tint the label sits on, not the panel's polarity.
     */
    public static void drawTextCentered(Canvas canvas, Font font, String text, float centerX, float centerY,
                                        int color, boolean backing, int shadowSurfaceArgb) {
        drawText(canvas, font, text, centerX - getStringWidth(font, text) / 2.0F,
                centerBaselineY(font, centerY), color, backing, shadowSurfaceArgb);
    }

    /**
     * Right-aligns text at rightX, vertically centered on centerY with the same
     * cap-height baseline used by drawTextCentered.
     */
    public static void drawTextRight(Canvas canvas, Font font, String text, float rightX, float centerY, int color) {
        drawText(canvas, font, text, rightX - getStringWidth(font, text),
                centerBaselineY(font, centerY), color);
    }

    public static void drawText(Canvas canvas, Font font, String text, float x, float y, int color) {
        drawText(canvas, font, text, x, y, color, false);
    }

    /**
     * Soft drop shadow behind text, replacing the old hard 1px-offset dark
     * under-copy (which read as a smear on light themes). Still a full
     * pre-pass — the whole block's shadow goes down before any main glyph, so
     * a line's shadow never darkens the previous line's glyphs — but the copy
     * is now Skia-blurred (sigma 1.5, offset dy=1) instead of a solid offset
     * duplicate. Two cached filters, one per surface polarity (a light panel
     * needs less shadow than a dark one), chosen per frame via
     * {@link ThemeService#panelIsLight()}; theme switches follow naturally
     * and nothing is allocated on the draw path.
     *
     * <p>Why makeDropShadowOnly and not makeDropShadow: the plain variant
     * draws the input glyphs too — on a backing pre-pass that would re-paint
     * the hard copy we are replacing. The Only variant emits just the blurred
     * coloured shadow; the Paint colour only shapes the alpha mask.</p>
     */
    private static final io.github.humbleui.skija.ImageFilter SHADOW_ON_LIGHT =
            io.github.humbleui.skija.ImageFilter.makeDropShadowOnly(0.0F, 1.0F, 1.5F, 1.5F, 0x33000000);
    private static final io.github.humbleui.skija.ImageFilter SHADOW_ON_DARK =
            io.github.humbleui.skija.ImageFilter.makeDropShadowOnly(0.0F, 1.0F, 1.5F, 1.5F, 0x59000000);

    /**
     * The soft-shadow filter behind drawText/drawBackingText, public for
     * rich-text pre-passes that manage their own run loop: one filtered layer
     * around the whole block composites exactly like per-run layers (the blur
     * is linear in the glyph mask) at a fraction of the layers.
     */
    public static io.github.humbleui.skija.ImageFilter backingShadow() {
        return backingShadowFor(ThemeService.panelIsLight());
    }

    /**
     * The soft-shadow filter chosen by the polarity of the surface the text
     * actually sits on — a capsule tint can differ in luminance from the panel,
     * so capsule text must ask this with the capsule background, not the panel's
     * polarity. Same two cached filters; nothing is allocated on the draw path.
     */
    public static io.github.humbleui.skija.ImageFilter backingShadowFor(int surfaceArgb) {
        return backingShadowFor(ThemeService.colorIsLight(surfaceArgb));
    }

    private static io.github.humbleui.skija.ImageFilter backingShadowFor(boolean surfaceIsLight) {
        return surfaceIsLight ? SHADOW_ON_LIGHT : SHADOW_ON_DARK;
    }

    /**
     * Draws {@code text} with a soft backing shadow: the glyph mask first
     * goes down once as a blurred drop shadow (a full pre-pass), then the
     * real glyphs go on top. Colour-code segments apply to the main pass
     * only; the shadow is a flat wash so coloured text keeps its hue.
     */
    public static void drawText(Canvas canvas, Font font, String text, float x, float y, int color, boolean backing) {
        if (backing) {
            drawTextPass(canvas, font, text, x, y, 0xFF000000, backingShadow());
        }
        drawTextPass(canvas, font, text, x, y, color, null);
    }

    /**
     * As above, but the shadow's polarity comes from {@code shadowSurfaceArgb}
     * — the tint the text sits on (a capsule background), not the panel's.
     */
    public static void drawText(Canvas canvas, Font font, String text, float x, float y, int color,
                                boolean backing, int shadowSurfaceArgb) {
        if (backing) {
            drawTextPass(canvas, font, text, x, y, 0xFF000000, backingShadowFor(shadowSurfaceArgb));
        }
        drawTextPass(canvas, font, text, x, y, color, null);
    }

    /**
     * One soft-shadow pass at the glyph positions. Used by rich-text backing
     * pre-passes that manage their own run loop (a whole block's shadow goes
     * down before any main glyph goes on top).
     */
    public static void drawBackingText(Canvas canvas, Font font, String text, float x, float y) {
        drawTextPass(canvas, font, text, x, y, 0xFF000000, backingShadow());
    }

    private static void drawTextPass(Canvas canvas, Font font, String text, float x, float y, int color,
                                     io.github.humbleui.skija.ImageFilter shadow) {
        canvas.save();
        try {
            if (shadow != null) {
                // One filtered layer around the whole block: giving every
                // colour segment a filtered paint made each drawRuns call
                // trigger its own implicit blur layer. The filter runs once,
                // when this layer is restored below.
                float pad = 8.0F;
                var metrics = font.getMetrics();
                try (Paint layer = new Paint()) {
                    layer.setImageFilter(shadow);
                    canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                            x - pad, y + metrics.getAscent() - pad,
                            getStringWidth(font, text) + pad * 2.0F,
                            (metrics.getDescent() - metrics.getAscent()) + pad * 2.0F), layer);
                }
            }
            try (Paint paint = new Paint().setColor(color)) {
                int currentColor = color;
                float drawX = x;
                for (TextSegment segment : parseColoredText(text)) {
                    if (segment.colorCode != null) {
                        currentColor = getColorFromCode(segment.colorCode, currentColor, color);
                    }
                    if (!segment.text.isEmpty()) {
                        paint.setColor(currentColor);
                        drawRuns(canvas, segment.text, drawX, y, font, paint);
                        drawX += measureRuns(font, segment.text);
                    }
                }
            } finally {
                if (shadow != null) {
                    // Exactly one restore per saveLayer, before the bare
                    // save's own restore.
                    canvas.restore();
                }
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Draws pre-wrapped lines as one block, vertically centered on centerY.
     * Every line keeps Minecraft color-code support.
     */
    public static void drawLines(Canvas canvas, Font font, java.util.List<String> lines, float x, float centerY, float lineHeight, int color) {
        drawLines(canvas, font, lines, x, centerY, lineHeight, color, false);
    }

    /**
     * Draws pre-wrapped lines as one block, vertically centered on centerY,
     * with the same soft backing shadow as
     * {@link #drawText(Canvas, Font, String, float, float, int, boolean)}. The
     * shadow is one full pre-pass so a line's shadow never lands on the
     * previous line's main glyphs.
     */
    public static void drawLines(Canvas canvas, Font font, java.util.List<String> lines, float x, float centerY, float lineHeight, int color, boolean backing) {
        if (lines.isEmpty()) {
            return;
        }
        float totalH = lines.size() * lineHeight;
        float blockTop = centerY - totalH / 2.0F;
        if (backing) {
            drawLinesPass(canvas, font, lines, x, blockTop, lineHeight, 0xFF000000, backingShadow());
        }
        drawLinesPass(canvas, font, lines, x, blockTop, lineHeight, color, null);
    }

    private static void drawLinesPass(Canvas canvas, Font font, java.util.List<String> lines, float x, float blockTop, float lineHeight, int color,
                                      io.github.humbleui.skija.ImageFilter shadow) {
        canvas.save();
        try (Paint paint = new Paint()) {
            if (shadow != null) {
                paint.setImageFilter(shadow);
            }
            for (int i = 0; i < lines.size(); i++) {
                float baseline = centerBaselineY(font, blockTop + (i + 0.5F) * lineHeight);
                int currentColor = color;
                float drawX = x;
                for (TextSegment segment : parseColoredText(lines.get(i))) {
                    if (segment.colorCode != null) {
                        currentColor = getColorFromCode(segment.colorCode, currentColor, color);
                    }
                    if (!segment.text.isEmpty()) {
                        paint.setColor(currentColor);
                        drawRuns(canvas, segment.text, drawX, baseline, font, paint);
                        drawX += measureRuns(font, segment.text);
                    }
                }
            }
        } finally {
            canvas.restore();
        }
    }

    /** Families probed for glyphs the bundled subset cannot render. */
    private static final String[] FALLBACK_FAMILIES = {
            "Microsoft YaHei", "DengXian", "Segoe UI", "Segoe UI Symbol",
            "MS Gothic", "Yu Gothic UI", "Malgun Gothic", "Leelawadee UI",
            "Cambria", "Calibri", "Arial", "Noto Sans CJK SC"
    };

    /**
     * Glyph-level fallback: codepoints missing from the primary (bundled subset)
     * resolve through the system FontMgr. Emoji-range codepoints first try Segoe
     * UI Emoji, but only when that font actually contains the glyph — many
     * non-emoji symbols share the 0x2600-0x27BF block (e.g. ✧ U+2727) and would
     * otherwise render as tofu even though Segoe UI Symbol has them.
     */
    private static Font fontFor(Font primary, int codepoint) {
        java.util.BitSet known = PRIMARY_GLYPH_HITS.get(primary);
        if (known != null && known.get(codepoint)) {
            return primary;
        }
        if (primary.getUTF32Glyph(codepoint) != 0) {
            if (known == null) {
                known = new java.util.BitSet();
                PRIMARY_GLYPH_HITS.put(primary, known);
            }
            known.set(codepoint);
            return primary;
        }
        String key = codepoint + "@" + (int) primary.getSize();
        Font cached = FALLBACK_FONT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font resolved = null;
        try {
            io.github.humbleui.skija.FontMgr mgr = io.github.humbleui.skija.FontMgr.getDefault();
            if (mgr != null) {
                io.github.humbleui.skija.Typeface match = null;
                if (isEmojiCodepoint(codepoint)) {
                    io.github.humbleui.skija.Typeface emoji =
                            mgr.matchFamilyStyle("Segoe UI Emoji", io.github.humbleui.skija.FontStyle.NORMAL);
                    if (emoji != null && emoji.getUTF32Glyph(codepoint) != 0) {
                        match = emoji;
                    }
                }
                if (match == null) {
                    // The bundled font is a GB2312 subset, so kaomoji lean on
                    // exotic ranges (kana, Thai, Hangul, phonetic, symbols). A
                    // narrow list leaves tofu even though Windows has the glyphs:
                    // DengXian/MS Gothic cover kana, Malgun Gothic covers Hangul,
                    // Leelawadee UI covers Thai, Cambria/Calibri cover symbols.
                    match = mgr.matchFamiliesStyleCharacter(
                            FALLBACK_FAMILIES,
                            io.github.humbleui.skija.FontStyle.NORMAL, null, codepoint);
                }
                if (match != null) {
                    resolved = new Font(match, primary.getSize());
                }
            }
        } catch (Throwable t) {
            // No fallback available: primary renders tofu, same as before.
        }
        if (resolved == null) {
            resolved = primary;
        }
        FALLBACK_FONT_CACHE.put(key, resolved);
        return resolved;
    }

    private static boolean isEmojiCodepoint(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
                || cp == 0xFE0F || cp == 0x200D || (cp >= 0x2B00 && cp <= 0x2BFF);
    }

    /**
     * U+FE0F is an emoji presentation selector: it should not contribute a
     * visible advance. Some fallback fonts still give it a non-zero width,
     * which makes FE0F emoji sit off-centre in cells and leaves an extra gap
     * next to them in wrapped text. Rendering and measuring therefore drop it
     * while the original chat string is preserved untouched.
     */
    private static final char EMOJI_VARIATION_SELECTOR = '\uFE0F';

    private static void drawRuns(Canvas canvas, String text, float x, float y, Font primary, Paint paint) {
        // Keep the original string intact; only strip the presentation selector
        // from the glyph run we hand to Skia so it cannot add phantom width.
        if (text.indexOf(EMOJI_VARIATION_SELECTOR) >= 0) {
            text = text.replace(String.valueOf(EMOJI_VARIATION_SELECTOR), "");
        }
        float drawX = x;
        int i = 0;
        int n = text.length();
        while (i < n) {
            int cp = text.codePointAt(i);
            Font runFont = fontFor(primary, cp);
            int j = i;
            while (j < n) {
                int cp2 = text.codePointAt(j);
                if (fontFor(primary, cp2) != runFont) {
                    break;
                }
                j += Character.charCount(cp2);
            }
            String run = text.substring(i, j);
            canvas.drawString(run, drawX, y, runFont, paint);
            drawX += runFont.measureTextWidth(run);
            i = j;
        }
    }

    private static float measureRuns(Font primary, String text) {
        if (text.indexOf(EMOJI_VARIATION_SELECTOR) >= 0) {
            text = text.replace(String.valueOf(EMOJI_VARIATION_SELECTOR), "");
        }
        float width = 0.0F;
        int i = 0;
        int n = text.length();
        while (i < n) {
            int cp = text.codePointAt(i);
            Font runFont = fontFor(primary, cp);
            int j = i;
            while (j < n) {
                int cp2 = text.codePointAt(j);
                if (fontFor(primary, cp2) != runFont) {
                    break;
                }
                j += Character.charCount(cp2);
            }
            width += runFont.measureTextWidth(text.substring(i, j));
            i = j;
        }
        return width;
    }

    public static List<String> wrap(Font font, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        float currentWidth = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0.0F;
                continue;
            }
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            // FE0F is preserved in the wrapped line (so caret/string indexes stay
            // stable) but contributes no measurable width, matching draw/measure.
            float charWidth = cp == EMOJI_VARIATION_SELECTOR
                    ? 0.0F
                    : fontFor(font, cp).measureTextWidth(text.substring(i, i + charCount));
            if (currentWidth + charWidth > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0.0F;
                if (Character.isWhitespace(c)) {
                    i += charCount - 1;
                    continue;
                }
            }
            current.append(text, i, i + charCount);
            currentWidth += charWidth;
            if (charCount > 1) {
                i += charCount - 1;
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static int getColorFromCode(String code, int currentColor, int originalColor) {
        if (code == null) {
            return currentColor;
        }
        int alpha = (currentColor >>> 24) & 0xFF;
        if (code.startsWith("#")) {
            return (alpha << 24) | Integer.parseInt(code.substring(1), 16);
        }
        if (code.equals("r")) {
            return originalColor;
        }
        int index = "0123456789abcdef".indexOf(code.charAt(0));
        if (index >= 0 && index < 16) {
            return (alpha << 24) | COLOR_CODE_RGB[index];
        }
        return currentColor;
    }

    private static List<TextSegment> parseColoredText(String text) {
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String pendingColor = null;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                if (current.length() > 0) {
                    segments.add(new TextSegment(current.toString(), pendingColor));
                    current.setLength(0);
                }
                i++;
                char code = text.charAt(i);
                if (code == '#' && i + 6 < text.length()) {
                    String hex = text.substring(i + 1, i + 7);
                    if (isHex(hex)) {
                        pendingColor = "#" + hex;
                        i += 6;
                        continue;
                    }
                }
                if ("0123456789abcdefr".indexOf(code) >= 0) {
                    pendingColor = String.valueOf(code);
                } else {
                    current.append('\u00A7').append(code);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            segments.add(new TextSegment(current.toString(), pendingColor));
        }
        return segments;
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (!Character.isDigit(c) && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private record TextSegment(String text, String colorCode) {
    }
}
