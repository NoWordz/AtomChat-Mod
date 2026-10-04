package com.atom.chat.ui;

import com.atom.chat.render.SkiaDraw;
import com.atom.chat.theme.ThemeService;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;

/**
 * The one card primitive for settings surfaces. Every content card — settings
 * rows, home tiles, category cards — goes through here, so the elevation
 * system cannot drift between pages:
 *
 * <ul>
 *   <li>a light drop shadow ({@link UiTokens#CARD_SHADOW}, the lower of the
 *       two elevation tiers — chrome floats on {@link UiTokens#CHROME_SHADOW},
 *       content cards sit on this one),</li>
 *   <li>the configured card fill,</li>
 *   <li>a border picked by panel polarity: a 1px dark hairline on light
 *       panels (where the white lit edge is invisible), the lit white edge on
 *       dark/frosted ones,</li>
 *   <li>the hover wash, last.</li>
 * </ul>
 *
 * <p>Disabled rows dim their text and controls only — the card base never
 * changes, so the list keeps one uniform surface instead of patchy light
 * rectangles.</p>
 */
public final class UiCards {
    private UiCards() {
    }

    /** Draws the full card stack; {@code hoverWeight} 0..1. */
    public static void drawCard(Canvas canvas, float x, float y, float w, float h,
                                float radius, float hoverWeight) {
        SkiaDraw.drawRoundedShadow(canvas, x, y, w, h, radius, UiTokens.s(6), UiTokens.CARD_SHADOW);
        SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius, UiTokens.cardFill());
        if (ThemeService.panelIsLight()) {
            SkiaDraw.drawEdgeHighlight(canvas, x, y, w, h, radius, UiTokens.s(1.0F), hairlineColor());
        } else {
            SkiaDraw.drawEdgeHighlight(canvas, x, y, w, h, radius, UiTokens.s(1.2F), UiTokens.CARD_EDGE);
        }
        if (hoverWeight > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius, UiTokens.cardHover(hoverWeight));
        }
    }

    /**
     * Theme-adaptive hairline for small round elements (colour swatches, the
     * slider knob's outer trace): a whisper of black on light panels, a
     * whisper of white on dark ones — either way enough to separate a pale
     * fill from a pale ground and vice versa.
     */
    public static int hairlineColor() {
        return ThemeService.panelIsLight()
                ? Color.makeARGB(42, 0, 0, 0)
                : Color.makeARGB(64, 255, 255, 255);
    }
}
