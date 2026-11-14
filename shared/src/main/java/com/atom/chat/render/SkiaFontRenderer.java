package com.atom.chat.render;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.diagnostics.FirstOpenBudget;
import com.atom.chat.diagnostics.FrameProfile;
import com.atom.chat.font.FontManager;
import com.atom.chat.theme.ThemeService;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.TextBlob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal Skia text renderer with Minecraft color-code support (ported from Tuui's FontRenderer).
 */
public final class SkiaFontRenderer {
    private static final EmojiGlyphCache EMOJI_GLYPHS = new EmojiGlyphCache();
    /** Render-thread scratch paints; every draw sets the properties it reads. */
    private static final Paint TEXT_PAINT = new Paint();
    private static final Paint SHADOW_PAINT = new Paint();
    static void clearEmojiGlyphCache() {
        EMOJI_GLYPHS.clear();
        clearTextShapeCache();
        clearPrewarmedFonts();
    }
    static long emojiGlyphBuilds() { return EMOJI_GLYPHS.builds(); }
    static long emojiPrewarmHits() { return EMOJI_GLYPHS.prewarmHits(); }
    static long shapedPlanBuilds() { return shapeCacheBuilds; }

    /** One shaped fallback-font run. The width is measured once with the blob. */
    private record ShapedRun(Font font, String text, TextBlob blob, float width) {}
    /** All fallback runs for one visible string, retained across repeated frames. */
    private record ShapedPlan(List<ShapedRun> runs, float width) {}
    private record ShapeKey(Font font, String text) {}

    /**
     * Repeated labels otherwise enter Canvas.drawString on every frame, which
     * shapes the same run again in native Skia. The cache is deliberately
     * bounded by both entries and UTF-16 characters; the render thread is the
     * only writer, like the width and fallback-font caches beside it.
     */
    private static final int SHAPE_CACHE_ENTRIES = 2048;
    private static final int SHAPE_CACHE_CHARACTERS = 131_072;
    private static final Map<ShapeKey, ShapedPlan> SHAPE_CACHE =
            new java.util.LinkedHashMap<>(256, 0.75F, true);
    private static int shapeCacheCharacters;
    private static long shapeCacheBuilds;
    private static final int PARSE_CACHE_ENTRIES = 2048;
    private static final Map<String, List<TextSegment>> PARSE_CACHE =
            new java.util.LinkedHashMap<>(256, 0.75F, true);
    private record WrapKey(Font font, String text, float maxWidth, boolean words) {}
    private static final int WRAP_CACHE_ENTRIES = 2048;
    private static final int WRAP_CACHE_CHARACTERS = 131_072;
    private static final Map<WrapKey, List<String>> WRAP_CACHE =
            new java.util.LinkedHashMap<>(256, 0.75F, true);
    private static int wrapCacheCharacters;
    private static long wrapCacheBuilds;
    /** Native editable/ordered text is literal; section signs are not a second formatting language. */
    public static float literalWidth(Font font,String text) { return text == null || text.isEmpty() ? 0 : measureRuns(font,text); }
    public static void drawLiteralText(Canvas canvas,Font font,String text,float x,float y,int color) {
        if (text == null || text.isEmpty()) return;
        drawRuns(canvas, text, x, y, font, TEXT_PAINT.setColor(color));
    }
    private static final int[] COLOR_CODE_RGB = new int[]{
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /**
     * Per-codepoint+size cache of resolved fallback fonts (emoji / chars missing
     * from the primary). Bounded: every entry is a native Font holding a
     * Typeface, and a chat backlog plus a server MOTD can present thousands of
     * distinct exotic codepoints at a handful of sizes. The bound is high
     * enough that the working set (one emoji or kana per size) always fits, and
     * the eviction is the same LRU shape the width cache uses.
     */
    private static final int FALLBACK_CACHE_MAX = 2048;
    private static final java.util.Map<String, Font> FALLBACK_FONT_CACHE =
            new java.util.LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Font> eldest) {
                    return size() > FALLBACK_CACHE_MAX;
                }
            };

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

    /**
     * Faces resolved by the background prewarm, keyed exactly like
     * {@link #FALLBACK_FONT_CACHE} ({@code codepoint@size}). The render thread
     * takes one (removing it) and installs it in the normal cache; ownership
     * transfers exactly once. Bounded so a pathological backlog cannot pin
     * native fonts forever.
     */
    private static final int PREWARMED_FONT_MAX = 256;
    private static final Object PREWARM_FONT_LOCK = new Object();
    private static final java.util.Map<String, Font> PREWARMED_FONTS = new java.util.HashMap<>();
    private static volatile boolean hasPrewarmedFonts;
    private static long prewarmFontGeneration;
    private static volatile long prewarmFontHits;

    private SkiaFontRenderer() {
    }

    static long emojiPrewarmFontHits() {
        return prewarmFontHits;
    }

    /**
     * Background-thread entry point. Resolves each emoji face once per
     * {@code (size, codepoint)} and rasterizes the glyphs the first frame would
     * otherwise have built. CPU-only: no GL and no {@code DirectContext}.
     */
    static void prewarmEmojiGlyphs(java.util.List<EmojiPrewarmSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return;
        }
        io.github.humbleui.skija.Typeface typeface = FontManager.getDefaultTypeface();
        if (typeface == null) {
            return;
        }
        boolean fauxBold = FontManager.usesSystemFallback();
        long generation;
        synchronized (PREWARM_FONT_LOCK) {
            generation = prewarmFontGeneration;
        }
        java.util.Map<String, Font> local = new java.util.HashMap<>();
        java.util.Set<String> primaryOnly = new java.util.HashSet<>();
        try {
            for (EmojiPrewarmSpec spec : specs) {
                if (spec.text().isEmpty()) {
                    continue;
                }
                int cp = spec.text().codePointAt(0);
                String key = cp + "@" + (int) spec.size();
                Font font = local.get(key);
                if (font == null) {
                    Font primary = new Font(typeface, spec.size());
                    if (fauxBold) {
                        primary.setEmboldened(true);
                    }
                    if (primary.getUTF32Glyph(cp) != 0) {
                        font = primary;
                        primaryOnly.add(key);
                    } else {
                        Font fallback = resolveFallbackFont(primary, cp);
                        if (fallback == null) {
                            font = primary;
                            primaryOnly.add(key);
                        } else {
                            primary.close();
                            font = fallback;
                        }
                    }
                    local.put(key, font);
                }
                EMOJI_GLYPHS.prewarmOne(font, spec.text(), spec.rgb(), 2);
            }
        } finally {
            for (var entry : local.entrySet()) {
                if (primaryOnly.contains(entry.getKey())) {
                    entry.getValue().close();
                } else {
                    depositPrewarmedFont(entry.getKey(), entry.getValue(), generation);
                }
            }
        }
    }

    private static Font takePrewarmedFont(String key) {
        if (!hasPrewarmedFonts) {
            return null;
        }
        synchronized (PREWARM_FONT_LOCK) {
            Font font = PREWARMED_FONTS.remove(key);
            if (font != null) {
                prewarmFontHits++;
                if (PREWARMED_FONTS.isEmpty()) {
                    hasPrewarmedFonts = false;
                }
            }
            return font;
        }
    }

    private static void depositPrewarmedFont(String key, Font font, long generation) {
        synchronized (PREWARM_FONT_LOCK) {
            if (generation != prewarmFontGeneration || PREWARMED_FONTS.size() >= PREWARMED_FONT_MAX) {
                font.close();
                return;
            }
            Font previous = PREWARMED_FONTS.put(key, font);
            if (previous != null) {
                previous.close();
            }
            hasPrewarmedFonts = true;
        }
    }

    private static void clearPrewarmedFonts() {
        synchronized (PREWARM_FONT_LOCK) {
            prewarmFontGeneration++;
            for (Font font : PREWARMED_FONTS.values()) {
                font.close();
            }
            PREWARMED_FONTS.clear();
            hasPrewarmedFonts = false;
            prewarmFontHits = 0;
        }
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
                canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                        x - pad, y + metrics.getAscent() - pad,
                        getStringWidth(font, text) + pad * 2.0F,
                        (metrics.getDescent() - metrics.getAscent()) + pad * 2.0F),
                        SHADOW_PAINT.setImageFilter(shadow));
            }
            try {
                Paint paint = TEXT_PAINT;
                int currentColor = color;
                float drawX = x;
                for (TextSegment segment : parseColoredText(text)) {
                    if (segment.colorCode != null) {
                        currentColor = getColorFromCode(segment.colorCode, currentColor, color);
                    }
                    if (!segment.text.isEmpty()) {
                        paint.setColor(currentColor);
                        drawX += drawRuns(canvas, segment.text, drawX, y, font, paint);
                    }
                }
            } finally {
                if (shadow != null) {
                    // Exactly one restore per saveLayer, before the bare
                    // save's own restore.
                    canvas.restore();
                    SHADOW_PAINT.setImageFilter(null);
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
        try {
            Paint paint = shadow == null ? TEXT_PAINT : SHADOW_PAINT.setImageFilter(shadow);
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
                        drawX += drawRuns(canvas, segment.text, drawX, baseline, font, paint);
                    }
                }
            }
        } finally {
            if (shadow != null) SHADOW_PAINT.setImageFilter(null);
            canvas.restore();
        }
    }

    /**
     * Families probed for glyphs the bundled subset cannot render, in order.
     *
     * <p><strong>Windows families stay first</strong> so a Windows machine
     * resolves exactly the faces it did before. The Linux / cross-platform tail
     * covers the fonts distributions actually ship — Noto, WenQuanYi, DejaVu,
     * Ubuntu — which is what a Linux player has instead of YaHei/DengXian. An
     * absent name simply matches nothing and is skipped, so the tail costs a
     * Windows user nothing.
     */
    static final String[] FALLBACK_FAMILIES = {
            "Microsoft YaHei", "DengXian", "Segoe UI", "Segoe UI Symbol",
            "MS Gothic", "Yu Gothic UI", "Malgun Gothic", "Leelawadee UI",
            "Cambria", "Calibri", "Arial",
            // Linux / cross-platform tail (the fonts distro packages install).
            "Noto Sans CJK SC", "Noto Sans CJK TC", "Noto Sans SC",
            "Source Han Sans SC", "WenQuanYi Micro Hei", "WenQuanYi Zen Hei",
            "AR PL UMing CN", "Droid Sans Fallback",
            "Noto Sans Symbols 2", "Noto Sans Symbols",
            "DejaVu Sans", "FreeSans", "Ubuntu"
    };

    /**
     * Emoji-capable families tried before the general fallback, in order.
     * {@code Segoe UI Emoji} stays first so Windows is unchanged; the rest are
     * the emoji fonts Linux ships — {@code Noto Color Emoji} is the standard
     * Ubuntu/Debian/Fedora package. A candidate is accepted only when it
     * actually carries the codepoint, so non-emoji residents of the emoji
     * blocks (e.g. ✧ U+2727) still fall through to {@link #FALLBACK_FAMILIES}.
     */
    static final String[] EMOJI_FAMILIES = {
            "Segoe UI Emoji", "Noto Color Emoji", "Noto Emoji",
            "Apple Color Emoji", "Twemoji Mozilla", "JoyPixels",
            "EmojiOne Color", "Symbola"
    };

    /**
     * First family in {@code families} for which {@code hasGlyph} reports true,
     * or {@code null} when none does. Pure — no native calls — so the ordering
     * contract is testable on a machine that has none of these fonts; the
     * renderer passes a predicate that asks the live {@code FontMgr}.
     */
    static String firstFamilyWithGlyph(String[] families, java.util.function.Predicate<String> hasGlyph) {
        for (String family : families) {
            if (hasGlyph.test(family)) {
                return family;
            }
        }
        return null;
    }

    /** True when {@code family} resolves on this machine and carries {@code codepoint}. */
    private static boolean familyCarriesGlyph(io.github.humbleui.skija.FontMgr mgr, String family, int codepoint) {
        try (io.github.humbleui.skija.Typeface face =
                     mgr.matchFamilyStyle(family, io.github.humbleui.skija.FontStyle.NORMAL)) {
            return face != null && face.getUTF32Glyph(codepoint) != 0;
        }
    }

    /** Set once so the missing-emoji-font warning is emitted a single time, not per emoji per frame. */
    private static final java.util.concurrent.atomic.AtomicBoolean NO_EMOJI_FONT_REPORTED =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * One line the first time an emoji codepoint finds no emoji-capable font.
     *
     * <p><strong>Probed with a representative emoji, not the codepoint that
     * just missed.</strong> Non-emoji symbols live in the emoji blocks too —
     * ✧ (U+2727) is missing from Segoe UI Emoji and is drawn correctly by
     * Segoe UI Symbol — so keying the warning off "this codepoint missed" would
     * fire on a stock Windows box that has emoji fonts and needs none. Asking
     * "does any emoji font carry 😀 (U+1F600)" answers the real question.
     *
     * <p>On a slim Linux without an emoji font this is <em>the</em> reason the
     * panel fills with boxes, and without the line the symptom is silent — Skia
     * just draws .notdef. Once per process; the renderer runs every frame.
     */
    private static void reportNoEmojiFontIfMissing(io.github.humbleui.skija.FontMgr mgr) {
        if (!NO_EMOJI_FONT_REPORTED.compareAndSet(false, true)) {
            return;
        }
        boolean hasEmojiFont = firstFamilyWithGlyph(EMOJI_FAMILIES,
                name -> familyCarriesGlyph(mgr, name, 0x1F600)) != null;
        if (hasEmojiFont || com.atom.chat.AtomChat.LOGGER == null) {
            return;
        }
        com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                "No emoji-capable font found (tried: {}); emoji will render as boxes. "
                        + "On Linux install fonts-noto-color-emoji (Ubuntu/Debian: apt install)."),
                String.join(", ", EMOJI_FAMILIES));
    }

    /**
     * Glyph-level fallback: codepoints missing from the primary (bundled subset)
     * resolve through the system FontMgr. Emoji-range codepoints first try the
     * {@link #EMOJI_FAMILIES} list (Segoe UI Emoji on Windows; Noto Color Emoji
     * and friends on Linux), but only when the candidate actually contains the
     * glyph — many non-emoji symbols share the 0x2600-0x27BF block (e.g. ✧
     * U+2727) and would otherwise render as tofu even though a symbol font has
     * them. Whatever the emoji pass does not claim falls to
     * {@link #FALLBACK_FAMILIES}.
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
        // A background prewarm may have already resolved the same face. Taking
        // it is the whole point of the move: the first emoji frame must not walk
        // the family list again.
        Font prewarmed = takePrewarmedFont(key);
        if (prewarmed != null) {
            FALLBACK_FONT_CACHE.put(key, prewarmed);
            return prewarmed;
        }
        long budgetStart = FirstOpenBudget.active() && isEmojiCodepoint(codepoint)
                ? System.nanoTime() : 0L;
        Font resolved = resolveFallbackFont(primary, codepoint);
        if (budgetStart != 0L) {
            FirstOpenBudget.addEmoji(System.nanoTime() - budgetStart);
        }
        if (resolved == null) {
            resolved = primary;
        }
        FALLBACK_FONT_CACHE.put(key, resolved);
        return resolved;
    }

    /** Resolve the fallback face, or null when none carries the codepoint. */
    private static Font resolveFallbackFont(Font primary, int codepoint) {
        try {
            io.github.humbleui.skija.FontMgr mgr = io.github.humbleui.skija.FontMgr.getDefault();
            if (mgr == null) {
                return null;
            }
            return resolveFont(mgr, primary, codepoint);
        } catch (Throwable t) {
            // No fallback available: primary renders tofu, same as before.
            return null;
        }
    }

    /**
     * The family walk, shared by the draw path and the background prewarm. The
     * prewarm must make the exact same choice or its glyph pixels would differ.
     */
    private static Font resolveFont(io.github.humbleui.skija.FontMgr mgr, Font primary, int codepoint) {
        Font resolved = null;
        try {
            io.github.humbleui.skija.Typeface match = null;
            if (isEmojiCodepoint(codepoint)) {
                String emojiFamily = firstFamilyWithGlyph(EMOJI_FAMILIES,
                        name -> familyCarriesGlyph(mgr, name, codepoint));
                if (emojiFamily != null) {
                    match = mgr.matchFamilyStyle(emojiFamily, io.github.humbleui.skija.FontStyle.NORMAL);
                } else {
                    reportNoEmojiFontIfMissing(mgr);
                }
            }
            if (match == null) {
                // The bundled font is a GB2312 subset, so kaomoji lean on
                // exotic ranges (kana, Thai, Hangul, phonetic, symbols). A
                // narrow list leaves tofu even though the system has the
                // glyphs: DengXian/MS Gothic cover kana, Malgun Gothic
                // covers Hangul, Leelawadee UI covers Thai, Cambria/Calibri
                // cover symbols — and on Linux the Noto/DejaVu/WenQuanYi
                // tail stands in for all of them.
                match = mgr.matchFamiliesStyleCharacter(
                        FALLBACK_FAMILIES,
                        io.github.humbleui.skija.FontStyle.NORMAL, null, codepoint);
            }
            if (match != null) {
                resolved = new Font(match, primary.getSize());
            }
        } catch (Throwable t) {
            // No fallback available: primary renders tofu, same as before.
        }
        return resolved;
    }

    static boolean isEmojiCodepoint(int cp) {
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

    private static float drawRuns(Canvas canvas, String text, float x, float y, Font primary, Paint paint) {
        // Keep the original string intact; only strip the presentation selector
        // from the glyph run we hand to Skia so it cannot add phantom width.
        if (text.indexOf(EMOJI_VARIATION_SELECTOR) >= 0) {
            text = text.replace(String.valueOf(EMOJI_VARIATION_SELECTOR), "");
        }
        if (text.isEmpty()) {
            return 0.0F;
        }
        ShapedPlan plan = shapedPlan(primary, text);
        float drawX = x;
        for (ShapedRun run : plan.runs) {
            if (!EMOJI_GLYPHS.draw(canvas, run.font, run.text, drawX, y, paint)) {
                if (run.blob == null) {
                    canvas.drawString(run.text, drawX, y, run.font, paint);
                } else {
                    canvas.drawTextBlob(run.blob, drawX, y, paint);
                }
            }
            drawX += run.width;
        }
        return drawX - x;
    }

    private static ShapedPlan shapedPlan(Font primary, String text) {
        ShapeKey key = new ShapeKey(primary, text);
        ShapedPlan cached = SHAPE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<ShapedRun> runs = new ArrayList<>();
        float width = 0.0F;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            Font runFont = fontFor(primary, cp);
            int end = i + Character.charCount(cp);
            while (end < text.length()) {
                int next = text.codePointAt(end);
                if (fontFor(primary, next) != runFont) break;
                end += Character.charCount(next);
            }
            String runText = text.substring(i, end);
            TextBlob blob = null;
            float runWidth;
            // Oversized strings are intentionally not retained. Do not allocate
            // native TextBlobs for them either, or an uncached draw would leak
            // one blob per frame in long-lived sessions.
            if (runText.length() <= 512 && text.length() <= SHAPE_CACHE_CHARACTERS) {
                try {
                    short[] glyphs = runFont.getStringGlyphs(runText);
                    if (glyphs.length > 0) {
                        float[] positions = runFont.getXPositions(glyphs, 0.0F);
                        blob = TextBlob.makeFromPosH(glyphs, positions, 0.0F, runFont);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // Canvas.drawString below remains the compatibility path.
                }
            }
            runWidth = runFont.measureTextWidth(runText);
            runs.add(new ShapedRun(runFont, runText, blob, runWidth));
            width += runWidth;
            i = end;
        }
        ShapedPlan plan = new ShapedPlan(List.copyOf(runs), width);
        shapeCacheBuilds++;
        if (text.length() <= SHAPE_CACHE_CHARACTERS) {
            SHAPE_CACHE.put(key, plan);
            shapeCacheCharacters += text.length();
            while (SHAPE_CACHE.size() > SHAPE_CACHE_ENTRIES
                    || shapeCacheCharacters > SHAPE_CACHE_CHARACTERS) {
                var eldest = SHAPE_CACHE.entrySet().iterator().next();
                shapeCacheCharacters -= eldest.getKey().text().length();
                closePlan(eldest.getValue());
                SHAPE_CACHE.remove(eldest.getKey());
            }
        }
        return plan;
    }

    static void clearTextShapeCache() {
        for (ShapedPlan plan : SHAPE_CACHE.values()) closePlan(plan);
        SHAPE_CACHE.clear();
        PARSE_CACHE.clear();
        shapeCacheCharacters = 0;
        WRAP_CACHE.clear();
        wrapCacheCharacters = 0;
    }

    private static void closePlan(ShapedPlan plan) {
        for (ShapedRun run : plan.runs) {
            if (run.blob != null) run.blob.close();
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
        if (text.isEmpty()) return List.of();
        WrapKey key = new WrapKey(font, text, maxWidth, false);
        List<String> cached = WRAP_CACHE.get(key);
        if (cached != null) return cached;
        wrapCacheBuilds++;
        List<String> lines = new ArrayList<>();
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
        return cacheWrap(key, lines);
    }

    /**
     * Word-aware wrapping for settings copy. CJK has no spaces, so it falls
     * back to the existing code-point wrap; Latin text breaks at whitespace
     * first and only splits a word that cannot fit a line by itself.
     */
    public static List<String> wrapWords(Font font, String text, float maxWidth) {
        if (text == null || text.isEmpty()) return List.of();
        WrapKey key = new WrapKey(font, text, maxWidth, true);
        List<String> cached = WRAP_CACHE.get(key);
        if (cached != null) return cached;
        wrapCacheBuilds++;
        return cacheWrap(key, wrapWords(text, maxWidth, s -> getStringWidth(font, s)));
    }

    private static List<String> cacheWrap(WrapKey key, List<String> lines) {
        List<String> immutable = List.copyOf(lines);
        if (key.text().length() <= WRAP_CACHE_CHARACTERS) {
            WRAP_CACHE.put(key, immutable);
            wrapCacheCharacters += key.text().length();
            while (WRAP_CACHE.size() > WRAP_CACHE_ENTRIES
                    || wrapCacheCharacters > WRAP_CACHE_CHARACTERS) {
                var eldest = WRAP_CACHE.entrySet().iterator().next();
                wrapCacheCharacters -= eldest.getKey().text().length();
                WRAP_CACHE.remove(eldest.getKey());
            }
        }
        return immutable;
    }

    static long wrapCacheBuilds() { return wrapCacheBuilds; }

    static List<String> wrapWords(String text, float maxWidth,
                                  java.util.function.Function<String, Float> measure) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        float currentWidth = 0.0F;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int count = Character.charCount(cp);
            if (cp == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0.0F;
                i += count;
                continue;
            }
            String glyph = text.substring(i, i + count);
            float width = measure.apply(glyph);
            if (currentWidth + width > maxWidth && current.length() > 0) {
                int split = current.length();
                while (split > 0 && !Character.isWhitespace(current.charAt(split - 1))) {
                    split--;
                }
                if (split > 0) {
                    while (split > 0 && Character.isWhitespace(current.charAt(split - 1))) {
                        split--;
                    }
                    String tail = current.substring(split).stripLeading();
                    if (split > 0) {
                        lines.add(current.substring(0, split));
                        current.setLength(0);
                        current.append(tail);
                        currentWidth = measure.apply(tail);
                    }
                }
                if (currentWidth + width > maxWidth && current.length() > 0) {
                    if (cannotStartLine(cp)) {
                        // CJK punctuation stays with the preceding glyph instead
                        // of becoming a one-character orphan at the line start.
                        int carry = current.offsetByCodePoints(current.length(), -1);
                        String tail = current.substring(carry);
                        if (carry > 0) {
                            lines.add(current.substring(0, carry));
                            current.setLength(0);
                            current.append(tail);
                            currentWidth = measure.apply(tail);
                        }
                    } else {
                        lines.add(current.toString());
                        current.setLength(0);
                        currentWidth = 0.0F;
                    }
                }
                if (Character.isWhitespace(cp)) {
                    // A reflow can move the line break to an earlier space,
                    // leaving a carried word on the new line. In that case this
                    // whitespace still separates it from the next word.
                    if (current.length() > 0) {
                        current.append(' ');
                        currentWidth += width;
                    }
                    i += count;
                    continue;
                }
            }
            current.append(glyph);
            currentWidth += width;
            i += count;
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static boolean cannotStartLine(int cp) {
        return cp=='\u3001'||cp=='\u3002'||cp=='\uff0c'||cp=='\uff1b'||cp=='\uff1a'
                ||cp=='\uff01'||cp=='\uff1f'||cp==')'||cp==']'||cp=='}'
                ||cp=='\uff09'||cp=='\u300b'||cp=='\u300d'||cp=='\u300f'||cp=='\u3011'
                ||cp=='\u3015'||cp=='\u3009'||cp=='\u300b'
                ||cp=='\u2026'||cp=='\u2014'||cp=='.'||cp==','||cp==';'||cp==':'||cp=='!'||cp=='?';
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
        List<TextSegment> cached = PARSE_CACHE.get(text);
        if (cached != null) return cached;
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
        List<TextSegment> parsed = List.copyOf(segments);
        PARSE_CACHE.put(text, parsed);
        if (PARSE_CACHE.size() > PARSE_CACHE_ENTRIES) {
            PARSE_CACHE.remove(PARSE_CACHE.keySet().iterator().next());
        }
        return parsed;
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
