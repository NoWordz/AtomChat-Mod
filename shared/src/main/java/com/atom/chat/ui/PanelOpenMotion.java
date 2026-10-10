package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;

/**
 * The panel shell's own arithmetic: the two frame clocks, the open/close
 * animation, and the geometry the panel and its blur capture rect share
 * (D25).
 *
 * <p><b>Why this class exists.</b> These are the only parts of the shell that
 * could never be asserted offline at all, and two of them carry a contract that
 * a golden frame cannot see. The open transform
 * ({@link #openScale}/{@link #openSlidePx}) is painted by {@code drawPhone} and
 * mirrored by the blur pre-pass's capture rect: if the two ever compute
 * different rects the blurred region drifts off the panel mid-flight, and
 * nothing offline noticed — the golden scenes render page objects, never the
 * panel frame. Here the rect the transform paints is <em>one</em> function
 * ({@link #paintedPanelRect}), the capture rect is that same function plus the
 * density hop, and {@code PanelOpenMotionTest} pins the numbers of both.
 *
 * <p>The rest is the same story at a smaller scale: the two frame clocks clamp
 * differently on purpose (the render clock's floor is 0 ms, the domain clock's
 * is 1 ms) and a swap is invisible; the close cut-off is a 6% threshold that
 * only decides when the screen goes away; the blur cadence gate is fed a
 * "chrome is moving" answer whose epsilon (0.01 px of composer height) is a
 * tuned value.
 *
 * <p><b>Bucket.</b> Shared single source: no {@code net.minecraft} name appears
 * here — the only dependencies are shared constants and the shared config.
 */
public final class PanelOpenMotion {

    /** The fade layer's rectangle: the scaled panel plus its slide excursion and slack. */
    public record LayerRect(float x, float y, float w, float h) {
    }

    /**
     * A rounded rect in one coordinate space: panel space
     * ({@link #paintedPanelRect}) or framebuffer space ({@link #blurCapture}).
     */
    public record PanelRect(float x, float y, float w, float h, float radius) {
    }

    private PanelOpenMotion() {
    }

    // ---------------------------------------------------------- frame clocks

    /**
     * The render clock's step: real nanos since the previous frame, clamped to
     * [0, 50] ms. The clamp is what {@code UiMotion.expApproach} also does
     * internally, and the zero floor keeps a backwards clock (a paused frame, a
     * debugger) from running an animation backwards.
     */
    public static float frameDtMs(long nowNanos, long lastNanos) {
        return Math.max(0.0F, Math.min(50.0F, (nowNanos - lastNanos) / 1_000_000.0F));
    }

    /**
     * The domain clock's step: milliseconds since the previous frame, clamped to
     * [1, 50]. The floor is 1 ms — not 0 — because every hover/spring stepper in
     * the panel divides by this value, so the first frame and a same-millisecond
     * frame must still look like one millisecond of progress.
     */
    public static long domainFrameDtMs(long nowMs, long lastMs) {
        return Math.min(50L, Math.max(1L, nowMs - lastMs));
    }

    // ------------------------------------------------------- open and close

    /**
     * One step of the panel's open/close progress. Opening rides
     * {@link UiMotion#PANEL_OPEN_TAU_MS}, closing the shorter
     * {@link UiMotion#PANEL_CLOSE_TAU_MS}; with decorative motion off the tau is
     * zero and the progress lands this frame, closing included.
     */
    public static float stepProgress(float progress, boolean closing, float dtMs, boolean animationsEnabled) {
        float target = closing ? 0.0F : 1.0F;
        float tau = !animationsEnabled ? 0.0F
                : closing ? UiMotion.PANEL_CLOSE_TAU_MS : UiMotion.PANEL_OPEN_TAU_MS;
        return UiMotion.expApproach(progress, target, dtMs, tau);
    }

    /**
     * Whether the panel reached its target this frame. {@code expApproach} snaps
     * exactly to the target, so this is an equality test — and it is the settle
     * test the close cut-off and the blur cadence gate both read.
     */
    public static boolean settled(float progress, boolean closing) {
        return progress == (closing ? 0.0F : 1.0F);
    }

    /**
     * Whether a click mid-animation must be swallowed. The gate owns that rule;
     * this is the answer it asks for (see {@code InputGate.Host}).
     */
    public static boolean animating(float progress, boolean closing) {
        return !settled(progress, closing);
    }

    /**
     * Closing is cut at 6% left: past that the panel is a subpixel sliver and the
     * exponential tail would only delay {@code setScreen(null)} (see
     * {@link UiMotion#PANEL_CLOSE_TAU_MS} for why the close runs a shorter tau at
     * all). Matches the settled state too, so an exact landing and a 5.9% sliver
     * both end the close on the frame they happen.
     */
    public static boolean closeDue(float progress, boolean closing) {
        return closing && (settled(progress, closing) || progress < 0.06F);
    }

    /** The fade's opacity: the progress itself, clamped so a clamp can never overshoot. */
    public static float fade(float progress) {
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    // --------------------------------------------------- the open transform

    /**
     * The one progress→shape mapping of the open/close animation. ZOOM grows the
     * panel around its centre; SLIDE keeps scale 1 and pushes it in from the left
     * ({@link #openSlidePx}). Both are read by {@code drawPhone} and by the blur
     * capture rect, so a style that moved one without the other would tear the
     * panel from its blur.
     */
    public static float openScale(float progress, AtomChatConfig.PageNavStyle style) {
        return style == AtomChatConfig.PageNavStyle.ZOOM
                ? OPEN_MIN_SCALE + (1.0F - OPEN_MIN_SCALE) * progress
                : 1.0F;
    }

    /** The SLIDE style's horizontal excursion at {@code progress}; 0 on ZOOM. */
    public static float openSlidePx(float progress, AtomChatConfig.PageNavStyle style) {
        return style == AtomChatConfig.PageNavStyle.SLIDE
                ? (progress - 1.0F) * UiTokens.s(24)
                : 0.0F;
    }

    /** The open-animation shape floor for the ZOOM style: the panel grows 0.94 -> 1. */
    public static final float OPEN_MIN_SCALE = 0.94F;

    // ------------------------------------------------------------- geometry

    /**
     * The rect the open transform paints, in panel space: the panel inset by the
     * bezel stroke, carried through the open transform — scaled around the panel
     * centre, then slid. This is the single derivation the blur capture rect
     * rides, so "the capture rect is the painted rect" is structural rather than
     * two copies of the same arithmetic.
     */
    public static PanelRect paintedPanelRect(float progress, AtomChatConfig.PageNavStyle style,
                                            float panelX, float panelY, float panelW, float panelH,
                                            float strokeWidth) {
        float scale = openScale(progress, style);
        float slide = openSlidePx(progress, style);
        float insetW = panelW - strokeWidth * 2.0F;
        float insetH = panelH - strokeWidth * 2.0F;
        float cx = panelX + panelW / 2.0F;
        float cy = panelY + panelH / 2.0F;
        return new PanelRect(
                cx + slide - insetW * scale / 2.0F,
                cy - insetH * scale / 2.0F,
                insetW * scale,
                insetH * scale,
                (UiTokens.panelRadius() - strokeWidth) * scale);
    }

    /**
     * The blur pre-pass's capture rect: {@link #paintedPanelRect} carried from
     * panel space into framebuffer space through the UI density and the window's
     * GUI scale — which is the space {@code PanelBlurRenderer} samples in.
     */
    public static PanelRect blurCapture(float progress, AtomChatConfig.PageNavStyle style,
                                       float panelX, float panelY, float panelW, float panelH,
                                       float strokeWidth, double density, double guiScale) {
        PanelRect painted = paintedPanelRect(progress, style, panelX, panelY, panelW, panelH, strokeWidth);
        return new PanelRect(
                (float) (painted.x() * density / guiScale),
                (float) (painted.y() * density / guiScale),
                (float) (painted.w() * density / guiScale),
                (float) (painted.h() * density / guiScale),
                (float) (painted.radius() * density / guiScale));
    }

    /**
     * The faded layer's rectangle: the scaled panel plus its slide excursion, with
     * {@code slack} on every side for the bezel and the shadow. Too small and the
     * fade clips the bezel while the panel is still in flight.
     */
    public static LayerRect fadeLayer(float progress, AtomChatConfig.PageNavStyle style,
                                     float x, float y, float w, float h, float slack) {
        float scale = openScale(progress, style);
        float slide = openSlidePx(progress, style);
        float scaledW = w * scale;
        float scaledH = h * scale;
        float cx = x + w / 2.0F;
        float cy = y + h / 2.0F;
        float left = cx - scaledW / 2.0F;
        float top = cy - scaledH / 2.0F;
        return new LayerRect(
                Math.min(left, left + slide) - slack,
                top - slack,
                scaledW + slack * 2.0F + Math.abs(slide),
                scaledH + slack * 2.0F);
    }

    // ---------------------------------------------------------- blur cadence

    /**
     * Whether the blur's fast cadence is due: a panel still in flight, or chrome
     * that moved since the previous frame. The composer height is the chrome that
     * moves without the panel moving (the input bar growing as text wraps), and
     * the epsilon is a tuned 0.01 px so layout noise cannot pin the cadence high.
     */
    public static boolean chromeAnimating(boolean settled, float extraH, float lastExtraH) {
        return !settled || Math.abs(extraH - lastExtraH) > 0.01F;
    }
}
