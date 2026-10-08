package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
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
 * failed action, rising up from behind the composer, holding briefly, then
 * sinking back down while it fades out — the notification banner's motion
 * family, mirrored for an anchor at the bottom of the panel instead of the top.
 *
 * <p>Draws on the one float-surface family it shares with the notification
 * banner: {@link UiCards#drawFloatSurface} on {@link UiTokens#floatSurfaceFill()},
 * with its text and glyph inks derived from that fill by
 * {@link UiTokens#onFloatSurface(int)} rather than taken from the panel's text
 * colour. Success draws the accent check when the accent still reads on the
 * float, otherwise the float's own ink; failure draws the shared
 * {@link UiTokens#dangerColor()} cross. The toast carries no avatar, preview or
 * reply button — it is a status line, not a navigational banner.</p>
 */
public final class ActionToast {
    private static final float PAD_X = UiTokens.s(12);
    private static final float ICON = UiTokens.s(12);

    private ActionToast() {
    }

    /**
     * Draws up to {@link ActionFeedback#MAX_VISIBLE} toasts stacked upward from
     * {@code bottom}, centred in the panel width; the first entry sits nearest
     * the anchor and later ones climb. The whole stack is clipped above
     * {@code clipTop} and, more usefully, above {@code bottom} — so a row rising
     * into place emerges from behind the composer instead of painting over it.
     * Entry keys are localized through {@code translator}.
     */
    public static void render(Canvas canvas, ActionFeedback feedback, float panelX, float panelW,
                              float clipTop, float bottom, long now, Translator translator) {
        List<ActionFeedback.Entry> entries = feedback.snapshot(now);
        if (entries.isEmpty()) {
            return;
        }
        boolean motion = Animations.enabled();
        Font font = FontManager.font(UiTokens.FONT_NAME);
        float rowH = UiTokens.s(36);
        float gap = UiTokens.s(8);
        canvas.save();
        try {
            SkiaDraw.clip(canvas, panelX, clipTop, panelW, Math.max(0.0F, bottom - clipTop), 0.0F);
            for (int i = 0; i < entries.size(); i++) {
                ActionFeedback.Entry entry = entries.get(i);
                float alpha = entry.alpha(now, motion);
                if (alpha <= 0.0F) {
                    continue;
                }
                String label = entry.label() != null ? entry.label() : translator.text(entry.key());
                float textW = SkiaFontRenderer.getStringWidth(font, label);
                float w = Math.min(panelW - UiTokens.s(32), textW + PAD_X * 2.0F + ICON + UiTokens.s(8));
                float x = panelX + (panelW - w) / 2.0F;
                // Index 0 (the entry snapshot() puts first) sits nearest the
                // anchor and later entries climb away from it. Note this is not
                // "stable under new arrivals": a fresh ERROR is placed at index 0
                // and shifts the rest up a slot, and past MAX_VISIBLE the cap
                // drops the newest success. Both are the queue's own rules.
                float y = ActionFeedback.Entry.rowTop(bottom, i, rowH, gap, UiTokens.s(8));
                if (y + rowH < clipTop) {
                    break;
                }
                // One curve for the whole row: the card, its glyph and its text
                // travel and fade together, so nothing detaches mid-entrance.
                // The card needs its own layer for the fade — without it only the
                // glyph and label dimmed, which read as "no fade at all".
                canvas.save();
                try {
                    canvas.translate(0.0F, entry.offset(now, motion));
                    try (Paint layer = new Paint()) {
                        layer.setColor(Color.makeARGB((int) (255.0F * alpha), 0, 0, 0));
                        // The layer is padded to the shadow's measured reach, not
                        // to the blur radius: with both at s(8) the shadow's tail
                        // was cut into a hard rectangle along the layer edge.
                        float pad = UiTokens.floatSurfaceShadowPad();
                        canvas.saveLayer(Rect.makeXYWH(x - pad, y - pad,
                                w + pad * 2.0F, rowH + pad * 2.0F), layer);
                        try {
                            drawRow(canvas, font, entry, x, y, w, rowH, label);
                        } finally {
                            canvas.restore();
                        }
                    }
                } finally {
                    canvas.restore();
                }
            }
        } finally {
            canvas.restore();
        }
    }

    private static void drawRow(Canvas canvas, Font font, ActionFeedback.Entry entry,
                                float x, float y, float w, float h, String label) {
        // The float-surface family, shared with the notification banner: an
        // opaque fill that follows the theme's card colour, on the two-tier chrome
        // shadow, with its ink derived from that fill. The row's own fade rides the
        // caller's layer, so nothing here needs to know the current opacity.
        int fill = UiTokens.floatSurfaceFill();
        UiCards.drawFloatSurface(canvas, x, y, w, h, UiTokens.cardRadius(), 0.0F, fill);
        float cy = y + h / 2.0F;
        float ix = x + PAD_X;
        drawGlyph(canvas, entry.outcome(), ix, cy, fill);
        SkiaFontRenderer.drawText(canvas, font, label, ix + ICON + UiTokens.s(8),
                SkiaFontRenderer.centerBaselineY(font, cy), UiTokens.onFloatSurface(fill));
    }

    private static void drawGlyph(Canvas canvas, ActionFeedback.Outcome outcome, float x, float cy,
                                  int fill) {
        Path glyph = outcome == ActionFeedback.Outcome.ERROR
                ? AppIcons.ICON_CLOSE_PATH : AppIcons.ICON_CHECK_PATH;
        Rect b = glyph.getBounds();
        float sc = ICON / Math.max(b.getWidth(), b.getHeight());
        // Success takes the accent, but only when the accent still reads on the
        // float: a light float under a pale accent would lose the tick, so it
        // falls back to the float's own ink.
        int color = outcome == ActionFeedback.Outcome.ERROR
                ? UiTokens.dangerColor()
                : readableOn(AtomChatConfig.get().accentColor, fill);
        try (Paint paint = new Paint().setAntiAlias(true)
                .setColor(color)
                .setMode(PaintMode.STROKE)
                // The canvas scales after the paint is built: divide the stroke
                // by the scale or the glyph draws ~1.4x too thick.
                .setStrokeWidth(UiTokens.s(1.8F) / sc)
                .setStrokeCap(PaintStrokeCap.ROUND)
                .setStrokeJoin(PaintStrokeJoin.ROUND)) {
            canvas.save();
            try {
                canvas.translate(x + ICON / 2.0F - (b.getLeft() + b.getRight()) / 2.0F * sc,
                        cy - (b.getTop() + b.getBottom()) / 2.0F * sc);
                canvas.scale(sc, sc);
                canvas.drawPath(glyph, paint);
            } finally {
                canvas.restore();
            }
        }
    }

    /**
     * {@code ink} when it still contrasts with {@code surface}, otherwise the
     * surface's own derived ink — so a status glyph never disappears into the
     * float it is drawn on.
     */
    private static int readableOn(int ink, int surface) {
        float a = com.atom.chat.theme.ThemeService.relativeLuminance(ink);
        float b = com.atom.chat.theme.ThemeService.relativeLuminance(surface);
        float contrast = (Math.max(a, b) + 0.05F) / (Math.min(a, b) + 0.05F);
        return contrast >= 3.0F ? ink : UiTokens.onFloatSurface(surface);
    }

    /** Resolves an action key to its localized label; the host supplies it. */
    public interface Translator {
        String text(String key);
    }
}
