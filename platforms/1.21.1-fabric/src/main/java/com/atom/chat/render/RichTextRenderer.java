package com.atom.chat.render;

import com.atom.chat.text.RichText;
import com.atom.chat.text.RichTextLayout;
import com.atom.chat.text.RichTextLayout.RichLine;
import com.atom.chat.text.TextMeasurer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Skia-dependent helper that lays out and draws {@link RichText} runs.
 *
 * <p>Wrapping is delegated to {@link RichTextLayout} with
 * {@link SkiaFontRenderer#getStringWidth} as the text measurer so layout,
 * drawing and width queries all agree with the existing Skia font renderer.
 */
public final class RichTextRenderer {
    private RichTextRenderer() {
    }

    /**
     * Wraps rich text to the given width using the supplied Skia font.
     */
    public static List<RichLine> wrapFor(RichText text, Font font, float maxWidth) {
        TextMeasurer measurer = s -> SkiaFontRenderer.getStringWidth(font, s);
        return RichTextLayout.wrap(text, measurer, maxWidth);
    }

    /**
     * Returns the on-screen width of one already-wrapped rich line.
     */
    public static float width(Font font, RichLine line) {
        float total = 0.0F;
        for (RichText.RichRun run : line.runs()) {
            total += SkiaFontRenderer.getStringWidth(font, run.text());
        }
        return total;
    }

    /**
     * Draws rich lines as one block vertically centered on {@code centerY}.
     *
     * <p>Each run uses the run's style color when present, otherwise
     * {@code fallbackColor}. Underlines are drawn for runs that are underlined
     * or carry a click event. When {@code addClickable} is true and a run has a
     * click or hover event, its line-box rectangle is appended to {@code sink}.
     */
    public static void drawLines(Canvas canvas, Font font, List<RichLine> lines,
                                 float x, float centerY, float lineHeight, int fallbackColor,
                                 List<ClickableSpan> sink, boolean addClickable) {
        drawLines(canvas, font, lines, x, centerY, lineHeight, fallbackColor, sink, addClickable, false);
    }

    /**
     * Draws rich lines as one block vertically centered on {@code centerY}.
     *
     * <p>Each run uses the run's style color when present, otherwise
     * {@code fallbackColor}. Underlines are drawn for runs that are underlined
     * or carry a click event. When {@code addClickable} is true and a run has a
     * click or hover event, its line-box rectangle is appended to {@code sink}.
     *
     * <p>With {@code backing} the whole block first goes down as one soft
     * shadow pre-pass (see {@code SkiaFontRenderer#drawBackingText}) before
     * any main glyph is drawn — one full pre-pass keeps a run's shadow from
     * darkening the previous run's glyphs.
     */
    public static void drawLines(Canvas canvas, Font font, List<RichLine> lines,
                                 float x, float centerY, float lineHeight, int fallbackColor,
                                 List<ClickableSpan> sink, boolean addClickable, boolean backing) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        float totalH = lines.size() * lineHeight;
        float blockTop = centerY - totalH / 2.0F;
        if (backing) {
            drawRunsPass(canvas, font, lines, x, blockTop, lineHeight, fallbackColor, sink, false, false);
        }
        drawRunsPass(canvas, font, lines, x, blockTop, lineHeight, fallbackColor, sink, addClickable, true);
    }

    /**
     * One full pass over all lines and runs at the given block top. The
     * shadow pass ({@code mainPass} false) draws every run as the soft
     * drop shadow and never underlines or records spans; the main pass behaves
     * exactly as before.
     */
    private static void drawRunsPass(Canvas canvas, Font font, List<RichLine> lines,
                                     float x, float blockTop, float lineHeight, int fallbackColor,
                                     List<ClickableSpan> sink, boolean addClickable, boolean mainPass) {
        if (!mainPass) {
            drawShadowPass(canvas, font, lines, x, blockTop, lineHeight);
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            RichLine line = lines.get(i);
            float lineCenterY = blockTop + (i + 0.5F) * lineHeight;
            float baseline = SkiaFontRenderer.centerBaselineY(font, lineCenterY);
            float lineTop = blockTop + i * lineHeight;
            float runX = x;
            for (RichText.RichRun run : line.runs()) {
                String text = run.text();
                if (text.isEmpty()) {
                    continue;
                }
                int color = effectiveColor(run.style(), fallbackColor);
                float runWidth = SkiaFontRenderer.getStringWidth(font, text);
                SkiaFontRenderer.drawText(canvas, font, text, runX, baseline, color);
                if (run.style().getClickEvent() != null || run.style().isUnderlined()) {
                    drawUnderline(canvas, font, runX, baseline, runWidth, color);
                }
                if (addClickable && sink != null
                        && (run.style().getClickEvent() != null || run.style().getHoverEvent() != null)) {
                    Style spanStyle = withUrlHover(run.style());
                    sink.add(new ClickableSpan(runX, lineTop, runWidth, lineHeight, spanStyle));
                }
                runX += runWidth;
            }
        }
    }

    /**
     * The shadow pre-pass as one filtered layer. Per-run {@code drawBackingText}
     * calls made every run push its own ImageFilter saveLayer — the overdraw
     * {@code SkiaFontRenderer#drawTextPass} itself documents — one per run of
     * every backing-backed block (sender names) per frame. The drop shadow is
     * linear in the glyph mask, so blurring the runs' combined mask once inside
     * a single layer composites exactly like one layer per run; the layer below
     * is the exact union of those per-run bounds. Shadow glyphs go down as the
     * same flat wash through the non-backing drawText overload.
     */
    private static void drawShadowPass(Canvas canvas, Font font, List<RichLine> lines,
                                       float x, float blockTop, float lineHeight) {
        // Layer bounds before the first glyph: widest line horizontally, first
        // and last baseline vertically — padded like drawTextPass pads a run.
        float maxLineW = 0.0F;
        for (RichLine line : lines) {
            float lineW = 0.0F;
            for (RichText.RichRun run : line.runs()) {
                lineW += SkiaFontRenderer.getStringWidth(font, run.text());
            }
            maxLineW = Math.max(maxLineW, lineW);
        }
        float pad = 8.0F;
        FontMetrics metrics = font.getMetrics();
        float topBaseline = SkiaFontRenderer.centerBaselineY(font, blockTop + 0.5F * lineHeight);
        float bottomBaseline = SkiaFontRenderer.centerBaselineY(font, blockTop + (lines.size() - 0.5F) * lineHeight);
        canvas.save();
        try {
            try (Paint layer = new Paint()) {
                layer.setImageFilter(SkiaFontRenderer.backingShadow());
                canvas.saveLayer(io.github.humbleui.types.Rect.makeXYWH(
                        x - pad, topBaseline + metrics.getAscent() - pad,
                        maxLineW + pad * 2.0F,
                        (bottomBaseline - topBaseline) + (metrics.getDescent() - metrics.getAscent()) + pad * 2.0F), layer);
                for (int i = 0; i < lines.size(); i++) {
                    RichLine line = lines.get(i);
                    float baseline = SkiaFontRenderer.centerBaselineY(font, blockTop + (i + 0.5F) * lineHeight);
                    float runX = x;
                    for (RichText.RichRun run : line.runs()) {
                        String text = run.text();
                        if (text.isEmpty()) {
                            continue;
                        }
                        SkiaFontRenderer.drawText(canvas, font, text, runX, baseline, 0xFF000000);
                        runX += SkiaFontRenderer.getStringWidth(font, text);
                    }
                }
            } finally {
                // Exactly one restore per saveLayer, before the bare save's own.
                canvas.restore();
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Returns the style to attach to a clickable span. Bare URL links show the
     * URL as a hover tooltip even when the incoming style carries no explicit
     * hover event; constructing the hover text is deferred to render time when
     * the Minecraft client is bootstrapped.
     */
    private static Style withUrlHover(Style style) {
        if (style == null || style.getClickEvent() == null
                || style.getClickEvent().getAction() != ClickEvent.Action.OPEN_URL
                || style.getHoverEvent() != null) {
            return style;
        }
        return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Text.literal(style.getClickEvent().getValue())));
    }

    /**
     * Minecraft {@link Style} colors are 24-bit RGB while Skia paints expect
     * ARGB. Like vanilla's TextRenderer, a styled run replaces the RGB channels
     * of the fallback color while keeping its alpha.
     */
    private static int effectiveColor(Style style, int fallbackColor) {
        if (style == null || style.getColor() == null) {
            return fallbackColor;
        }
        return (fallbackColor & 0xFF000000) | (style.getColor().getRgb() & 0xFFFFFF);
    }

    private static void drawUnderline(Canvas canvas, Font font, float x, float baseline,
                                      float width, int color) {
        FontMetrics metrics = font.getMetrics();
        Float underlinePos = metrics.getUnderlinePosition();
        Float underlineThickness = metrics.getUnderlineThickness();
        float y = baseline + (underlinePos != null ? underlinePos : 1.5F);
        float strokeWidth = underlineThickness != null && underlineThickness > 0.0F
                ? underlineThickness : Math.max(1.0F, font.getSize() / 14.0F);
        try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                .setStroke(true).setStrokeWidth(strokeWidth)) {
            canvas.drawLine(x, y, x + width, y, paint);
        }
    }
}
