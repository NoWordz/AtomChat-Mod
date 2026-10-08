package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;

import java.util.List;

/**
 * Draws the action-feedback stack: a compact opaque card per completed or
 * failed action, dropped in from above, holding briefly, then sliding up and
 * fading out — the notification banner's motion family, so the two floating
 * surfaces read as one system.
 *
 * <p>Same card language as the rest of the floating chrome: {@link UiCards}
 * over the heavier chrome shadow, on {@link UiTokens#cardFill()} so the surface
 * follows the theme (a banner's fixed white base is exactly what looked foreign
 * on dark themes). Success draws the accent check the profile copy already
 * uses; failure draws the shared {@link UiTokens#dangerColor()} cross. The
 * toast carries no avatar, preview or reply button — it is a status line, not
 * a navigational banner.</p>
 */
public final class ActionToast {
    /** Drop-in travel, matching the notification banner. */
    private static final float DROP_TRAVEL = UiTokens.s(14);
    private static final float PAD_X = UiTokens.s(12);
    private static final float ICON = UiTokens.s(12);

    private ActionToast() {
    }

    /**
     * Draws up to {@link ActionFeedback#MAX_VISIBLE} toasts stacked downward
     * from {@code top}, centred in the panel width; older entries sit lower.
     * Entry keys are localized through {@code translator}.
     */
    public static void render(Canvas canvas, ActionFeedback feedback, float panelX, float panelW,
                              float top, long now, Translator translator) {
        List<ActionFeedback.Entry> entries = feedback.snapshot(now);
        if (entries.isEmpty()) {
            return;
        }
        Font font = FontManager.font(UiTokens.FONT_NAME);
        float rowH = UiTokens.s(36);
        float gap = UiTokens.s(8);
        float y = top;
        for (ActionFeedback.Entry entry : entries) {
            float alpha = entry.alpha(now);
            if (alpha <= 0.0F) {
                continue;
            }
            String label = translator.text(entry.key());
            float textW = SkiaFontRenderer.getStringWidth(font, label);
            float w = Math.min(panelW - UiTokens.s(32), textW + PAD_X * 2.0F + ICON + UiTokens.s(8));
            float x = panelX + (panelW - w) / 2.0F;
            // One curve for the whole row: the card, its glyph and its text
            // travel and fade together, so nothing detaches mid-entrance.
            float travel = -(1.0F - alpha) * DROP_TRAVEL;

            canvas.save();
            try {
                canvas.translate(0.0F, travel);
                drawRow(canvas, font, entry, x, y, w, rowH, label, alpha);
            } finally {
                canvas.restore();
            }
            y += rowH + gap;
        }
    }

    private static void drawRow(Canvas canvas, Font font, ActionFeedback.Entry entry,
                                float x, float y, float w, float h, String label, float alpha) {
        UiCards.drawCard(canvas, x, y, w, h, UiTokens.cardRadius(), 0.0F,
                UiTokens.CHROME_SHADOW, UiTokens.s(8));
        float cy = y + h / 2.0F;
        float ix = x + PAD_X;
        drawGlyph(canvas, entry.outcome(), ix, cy, alpha);
        SkiaFontRenderer.drawText(canvas, font, label, ix + ICON + UiTokens.s(8),
                SkiaFontRenderer.centerBaselineY(font, cy), withAlpha(AtomChatConfig.get().textPrimaryColor, alpha));
    }

    private static void drawGlyph(Canvas canvas, ActionFeedback.Outcome outcome, float x, float cy, float alpha) {
        Path glyph = outcome == ActionFeedback.Outcome.ERROR
                ? AppIcons.ICON_CLOSE_PATH : AppIcons.ICON_CHECK_PATH;
        Rect b = glyph.getBounds();
        float sc = ICON / Math.max(b.getWidth(), b.getHeight());
        int color = withAlpha(outcome == ActionFeedback.Outcome.ERROR
                ? UiTokens.dangerColor() : AtomChatConfig.get().accentColor, alpha);
        try (Paint paint = new Paint().setAntiAlias(true)
                .setColor(color)
                .setMode(PaintMode.STROKE)
                // The canvas scales after the paint is built: divide the stroke
                // by the scale or the glyph draws ~1.4x too thick.
                .setStrokeWidth(UiTokens.s(1.8F) / sc)
                .setStrokeCap(PaintStrokeCap.ROUND)
                .setStrokeJoin(PaintStrokeJoin.ROUND)) {
            canvas.save();
            canvas.translate(x + ICON / 2.0F - (b.getLeft() + b.getRight()) / 2.0F * sc,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * sc);
            canvas.scale(sc, sc);
            canvas.drawPath(glyph, paint);
            canvas.restore();
        }
    }

    private static int withAlpha(int argb, float alpha) {
        return Color.makeARGB((int) (((argb >>> 24) & 0xFF) * alpha),
                (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }

    /** Resolves an action key to its localized label; the host supplies it. */
    public interface Translator {
        String text(String key);
    }
}
