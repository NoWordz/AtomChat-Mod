package com.atom.chat.ui;

import io.github.humbleui.skija.Canvas;

/**
 * The one rendering language for every right-click menu in AtomChat: the
 * chat screen's message / avatar / player-card context menu and the profile
 * page's avatar menu draw their popup surface, their row hover wash and
 * their per-row bounce through this class and nowhere else. Before it the
 * two menu families had drifted — the chat menu inset its row washes by a
 * fixed {@code s(4)} and had no bounce at all, while the profile menu
 * derived its inset from a private copy of the bounce maths whose own
 * comment admitted a fixed spill past the spring peak. One class owns the
 * language now, so the families can only move together.
 *
 * <p><b>The surface is fixed-polarity by design.</b> A popup floats above
 * every theme surface, so it does not follow the theme: the same dark
 * plate, radius and shadow everywhere ({@link #drawSurface}). Entrance and
 * exit stay with the caller — each screen already wraps its menu in a
 * {@code saveLayer} whose alpha is the popup's animation progress plus the
 * 0.94 scale-in, and this class deliberately knows nothing about it.</p>
 *
 * <p><b>Row bounce and its pixel budget.</b> Every row carries its own
 * {@link PressScale} ({@link PressScale#bounce()}), driven per frame through
 * {@link #updateRowBounce} and wrapped around the row's whole content with
 * {@link #beginRowBounce}/{@link #endRowBounce} so icon and label travel
 * with the wash capsule. The width budget means a wide row lifts less in
 * scale but the same in pixels — which is exactly why the wash inset below
 * has to be derived from that budget rather than fixed.</p>
 */
public final class MenuPopup {
    /**
     * Release-peak gain of the {@link PressScale} bounce over the hover
     * target's excess above 1. The bounce spring
     * ({@link UiSpring#newBounceSpring()}) travels from the pressed mirror
     * {@code 2 - u} back to the hover target {@code u} and overshoots past
     * it by 15.5446% of that travel — simulated with the real
     * {@link SpringAnim} substeps at 60fps, and because the spring is
     * linear the fraction is scale-invariant, so every width shares this
     * one gain: {@code peak = 1 + 1.31089 * (u - 1)}. This is the upper
     * bound of every excursion the row can make: a hover entry from rest
     * overshoots by only the analytic 16.3% of its shorter travel, and the
     * menus here drive {@code pressed = false} always — sizing against the
     * release peak keeps the wash inside the surface even if a caller ever
     * adds presses.
     */
    public static final float RELEASE_PEAK_GAIN = 1.31089F;

    /** The fixed dark popup plate: (35,39,47) at alpha 245, the shared {@link UiTokens#SKIN_PANEL}. */
    public static final int SURFACE_COLOR = UiTokens.SKIN_PANEL;
    /** Corner radius token (fed through {@link UiTokens#radius}). */
    public static final float SURFACE_RADIUS = 10.0F;
    /** Drop-shadow blur token (fed through {@link UiTokens#s}). */
    public static final float SURFACE_SHADOW_BLUR = 8.0F;

    /** The row wash: pure white on the fixed dark plate, alpha 55 at full hover. */
    public static final float WASH_ALPHA = 55.0F;
    /** Row wash capsule radius. */
    public static final float WASH_RADIUS = 6.0F;
    /**
     * Vertical wash inset, a fixed {@code s(4)} — the horizontal inset is
     * derived because the bounce spends its pixel budget on the row's
     * width, but the vertical travel stays tiny at every row height: at the
     * chat menu's row height of {@code s(32) = 40px} the wash capsule
     * stands 30px tall (row minus both insets) and the release peak moves
     * its edge only 1.14px, far inside the 5px inset. Deriving the
     * vertical side would buy nothing and split the language in two.
     */
    public static final float WASH_VERTICAL_INSET = UiTokens.s(4.0F);

    private MenuPopup() {
    }

    /**
     * The popup plate: fixed dark surface, corner-radius token and drop
     * shadow. Call this inside the caller's entrance {@code saveLayer}, so
     * the fade and scale-in apply to it for free.
     */
    public static void drawSurface(Canvas canvas, float x, float y, float w, float h) {
        com.atom.chat.render.SkiaDraw.drawRoundedShadow(canvas, x, y, w, h,
                UiTokens.radius(SURFACE_RADIUS), UiTokens.s(SURFACE_SHADOW_BLUR), UiTokens.CHROME_SHADOW);
        com.atom.chat.render.SkiaDraw.drawRoundedRect(canvas, x, y, w, h,
                UiTokens.radius(SURFACE_RADIUS), SURFACE_COLOR);
    }

    /**
     * Peak scale of a row {@code rowWidthPx} wide over the whole bounce:
     * the width budget sets the hover target
     * {@code u = 1 + min(BUDGET_PX / (rowWidthPx / 2), BUDGET_PCT)} and the
     * release-peak gain turns that into the bound the padding below is
     * sized against.
     */
    public static float rowPeakScale(float rowWidthPx) {
        float u = 1.0F + Math.min(PressScale.BUDGET_PX / (rowWidthPx * 0.5F), PressScale.BUDGET_PCT);
        return 1.0F + RELEASE_PEAK_GAIN * (u - 1.0F);
    }

    /**
     * Horizontal inset between the menu's edge and a row wash capsule, so
     * the capsule survives its own bounce: the row scales about the menu's
     * centre, so at scale {@code k} the capsule's outer edge lands at
     * {@code rowW/2 - (rowW/2 - inset) * k} in the menu's frame — and the
     * bound is tightest when the capsule is at its widest, the full row.
     * Sizing the inset at the edge's outward travel at the bounce peak
     * keeps it inside:
     * <pre>   inset = ceil(rowWidthPx * (peak - 1) / 2) + 1
     *   peak  = rowPeakScale(rowWidthPx)</pre>
     * with one pixel of clearance so the capsule edge reads as clear of the
     * card edge instead of tangent to it. Worked example at the chat menu's
     * width, {@code s(110) = 137.5px}: the budget gives
     * {@code u = 1 + min(4/68.75, 0.06) = 1.0582}, the release peak is
     * {@code 1 + 1.31089 * 0.0582 = 1.07627}, each edge travels
     * {@code 137.5 * 0.07627 / 2 = 5.24px} outward, the inset is
     * {@code ceil(5.24) + 1 = 7px}. The capsule's own edge only travels
     * {@code (68.75 - 7) * 0.07627 = 4.71px} at the peak (5.24px is the
     * full row's edge), so the true clearance is 2.29px. Old fixed insets
     * cannot do this: narrow menus riding the 6%
     * cap spill proportionally more than wide ones spend, which is the
     * fixed spill the profile menu's local derivation admitted to.
     */
    public static float rowInset(float rowWidthPx) {
        return (float) Math.ceil(rowWidthPx * (rowPeakScale(rowWidthPx) - 1.0F) / 2.0F) + 1.0F;
    }

    /**
     * Advances a row's bounce toward the hover state. {@code pressed} is
     * fixed false — menus select on release, they have no press half — and
     * the animation gate is {@link Animations#enabled()} like every other
     * decorative motion. One {@link PressScale} per row, never shared.
     */
    public static void updateRowBounce(PressScale scale, boolean hover, float dtMs, float rowWidthPx) {
        scale.update(hover, false, dtMs, Animations.enabled(), rowWidthPx);
    }

    /**
     * Begins a row's centered bounce: {@code (cx, cy)} is the row's own
     * centre, so the wash, icon and label travel together instead of
     * drifting out of the capsule. Always pair with {@link #endRowBounce} —
     * including when the scale is 1 — a try/finally around the row content
     * is the pattern both menus use.
     */
    public static void beginRowBounce(Canvas canvas, PressScale scale, float cx, float cy) {
        scale.begin(canvas, cx, cy);
    }

    /** Closes {@link #beginRowBounce}. */
    public static void endRowBounce(Canvas canvas) {
        canvas.restore();
    }

    /**
     * The row hover wash: a white capsule at {@code 55 * hoverAlpha} on the
     * fixed dark plate, inset by {@link #rowInset} horizontally (the bounce
     * budget) and {@link WASH_VERTICAL_INSET} vertically. Draws nothing
     * below the same 0.01 alpha floor the menus guard their content with.
     */
    public static void drawRowWash(Canvas canvas, float rowX, float rowY, float rowW, float rowH, float hoverAlpha) {
        if (hoverAlpha <= 0.01F) {
            return;
        }
        float a = Math.max(0.0F, Math.min(1.0F, hoverAlpha));
        float inset = rowInset(rowW);
        com.atom.chat.render.SkiaDraw.drawRoundedRect(canvas, rowX + inset, rowY + WASH_VERTICAL_INSET,
                rowW - inset * 2.0F, rowH - WASH_VERTICAL_INSET * 2.0F, UiTokens.s(WASH_RADIUS),
                io.github.humbleui.skija.Color.makeARGB(Math.round(WASH_ALPHA * a), 255, 255, 255));
    }
}
