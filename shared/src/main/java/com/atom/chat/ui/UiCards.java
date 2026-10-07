package com.atom.chat.ui;

import com.atom.chat.render.SkiaDraw;
import io.github.humbleui.skija.Canvas;

/**
 * The one card primitive for settings surfaces. Every content card — settings
 * rows, home tiles, category cards — goes through here, so the elevation
 * system cannot drift between pages:
 *
 * <ul>
 *   <li>a light drop shadow ({@link UiTokens#CARD_SHADOW}, the lower of the
 *       two elevation tiers — chrome floats on {@link UiTokens#CHROME_SHADOW},
 *       content cards sit on this one; blur is s(6), the v0.2.15 lift that
 *       v0.2.16 briefly flattened to s(4) — the tail that spills past
 *       {@link UiTokens#ROW_CLIP_INSET} is its near-transparent fringe),</li>
 *   <li>the configured card fill,</li>
 *   <li>the polarity-adaptive hairline ({@link UiTokens#hairline()}), one
 *       whisper-thin s(1.0) stroke — the three-tier outline ladder was
 *       retired: at its card tier it drew borders heavy enough to read as
 *       frames, not edges; the card's depth is the two shadow tiers plus
 *       this hairline alone,</li>
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
        drawCard(canvas, x, y, w, h, radius, hoverWeight, UiTokens.CARD_SHADOW, UiTokens.s(6));
    }

    /**
     * Draws the full card stack with an explicit shadow tier; {@code hoverWeight}
     * 0..1. Content cards keep the default above; floating chrome (the
     * notification banner) passes the heavier {@link UiTokens#CHROME_SHADOW} at
     * s(8) — the blur/alpha family the shell header and tab bar ride at.
     */
    public static void drawCard(Canvas canvas, float x, float y, float w, float h,
                                float radius, float hoverWeight, int shadowColor, float shadowBlur) {
        drawCard(canvas, x, y, w, h, radius, hoverWeight, shadowColor, shadowBlur,
                UiTokens.cardFill());
    }

    /**
     * The full stack with the base fill explicit too. A surface that floats
     * above message text keeps an opaque base — a translucent
     * {@link UiTokens#cardFill()} would let the messages bleed through — so the
     * banner passes its fixed opaque card colour here; every other surface
     * keeps {@link UiTokens#cardFill()} through the overloads above.
     */
    public static void drawCard(Canvas canvas, float x, float y, float w, float h,
                                float radius, float hoverWeight, int shadowColor, float shadowBlur,
                                int baseFill) {
        SkiaDraw.drawRoundedShadow(canvas, x, y, w, h, radius, shadowBlur, shadowColor);
        SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius, baseFill);
        // The hairline language: one stroke width, alpha 42 on both
        // polarities, so a card edge reads as an edge, not a frame.
        SkiaDraw.drawEdgeHighlight(canvas, x, y, w, h, radius, UiTokens.s(1.0F),
                UiTokens.hairline());
        if (hoverWeight > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius, UiTokens.cardHover(hoverWeight));
        }
    }
}
