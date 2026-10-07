package com.atom.chat.ui;

import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.theme.ThemeService;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;

/**
 * One toggle switch: the control behind every settings row.
 *
 * <p>Three visual states, tuned for the "switch is hard to see" report:</p>
 * <ul>
 *   <li><b>On</b> — the accent track alone. The alpha-110 rim stroke was
 *       retired at the owner's request; the deep neutral off-track beside
 *       it keeps the two states legible without any outline.</li>
 *   <li><b>Off</b> — a clearly deeper track than the old white-mist one:
 *       solid dim grey on dark cards (the old translucent white read as
 *       fog, not a track), dark grey at high alpha on light cards (where
 *       translucent white vanished completely). Deeper, but never a pale
 *       accent-on-pale-card mistake: the off track is always neutral.</li>
 *   <li><b>Disabled</b> — the whole switch renders at 40% opacity, far
 *       below any enabled state, so "unavailable" is legible at a glance.
 *       Input is already blocked upstream (the row's click handler drops
 *       unavailable items before the switch is toggled), so this is a
 *       visual state only; the hover/press spring keeps running.</li>
 * </ul>
 *
 * <p>The knob owns an {@link Animator} rather than a plain eased value so a
 * mid-flight reversal retargets from the current position instead of snapping
 * back to an end — clicking twice quickly reads as one continuous slide.</p>
 *
 * <p>Geometry comes entirely from {@link UiTokens}: knob diameter is the track
 * height minus the two insets, so the travel is
 * {@code SWITCH_W - SWITCH_KNOB - 2 * SWITCH_INSET}.</p>
 */
public final class ToggleSwitch {
    private static final int KNOB = Color.makeARGB(255, 255, 255, 255);
    /**
     * Opacity of the disabled state; every colour the switch draws is scaled
     * by it. Package-private for contract tests.
     */
    static final float DISABLED_OPACITY = 0.4F;

    private final Animator anim = new Animator(Easing::easeOutCubic);
    /** Hover/press bounce; hit-testing stays unscaled upstream, this is draw-only. */
    private final PressScale scale = PressScale.bounce();
    private boolean hover;
    private boolean pressed;
    /** How long a click pulse holds the pressed target after the state flipped. */
    private static final long PULSE_MS = 90;
    /** End of the armed click pulse; 0 = none. */
    private long pulseUntil;

    public ToggleSwitch() {
        anim.setValue(0.0F);
    }

    /**
     * Pointer state for the scale bounce. The switch itself never hit-tests —
     * the row reports whether the pointer is over the switch and whether the
     * row press landed on it.
     */
    public void setInteraction(boolean hover, boolean pressed) {
        this.hover = hover;
        this.pressed = pressed;
    }

    /**
     * Arms a one-shot press pulse (the EmojiPanel cell pattern): the pressed
     * target holds for {@link #PULSE_MS}, then the spring rebounds with its
     * overshoot. This is what makes the click bounce state-change proof — the
     * click performs on mouse-down and the same frame writes the config file,
     * so a bounce tied to the physical press window can be swallowed whole.
     */
    public void pulse() {
        pulseUntil = System.currentTimeMillis() + PULSE_MS;
    }

    private boolean pulsing() {
        return System.currentTimeMillis() < pulseUntil;
    }

    /** Jumps to the target with no animation (used when a page is first shown). */
    public void snapTo(boolean on) {
        anim.setValue(on ? 1.0F : 0.0F);
    }

    /**
     * Advances the knob. Safe to call every frame: {@code animateTo} is a no-op
     * while the target is unchanged, so an idle switch costs one comparison.
     */
    public void update(float dtMs, boolean on) {
        anim.animateTo(UiMotion.TOGGLE_MS, on ? 1.0F : 0.0F);
        anim.update(dtMs);
        // The scaled shape is the whole track, so the budget derives from SWITCH_W.
        scale.update(hover, pressed || pulsing(), dtMs, Animations.enabled(), UiTokens.SWITCH_W);
    }

    /**
     * @param x left edge of the track
     * @param y top edge of the track
     * @param accent the on-state track colour
     */
    public void render(Canvas canvas, float x, float y, int accent) {
        render(canvas, x, y, accent, true);
    }

    /**
     * @param x left edge of the track
     * @param y top edge of the track
     * @param accent the on-state track colour
     * @param enabled false renders the whole switch at
     *                {@link #DISABLED_OPACITY}; input is handled upstream
     */
    public void render(Canvas canvas, float x, float y, int accent, boolean enabled) {
        float op = enabled ? 1.0F : DISABLED_OPACITY;
        scale.begin(canvas, x + UiTokens.SWITCH_W / 2.0F, y + UiTokens.SWITCH_H / 2.0F);
        try {
            float p = Math.max(0.0F, Math.min(1.0F, anim.getValue()));
            SkiaDraw.drawRoundedRect(canvas, x, y, UiTokens.SWITCH_W, UiTokens.SWITCH_H,
                    UiTokens.SWITCH_H / 2.0F, dim(SkiaDraw.lerpColor(trackOff(), accent, p), op));

            float travel = UiTokens.SWITCH_W - UiTokens.SWITCH_KNOB - UiTokens.SWITCH_INSET * 2.0F;
            float knobX = x + UiTokens.SWITCH_INSET + travel * p;
            float knobY = y + (UiTokens.SWITCH_H - UiTokens.SWITCH_KNOB) / 2.0F;
            SkiaDraw.drawRoundedRect(canvas, knobX, knobY, UiTokens.SWITCH_KNOB, UiTokens.SWITCH_KNOB,
                    UiTokens.SWITCH_KNOB / 2.0F, dim(KNOB, op));
        } finally {
            canvas.restore();
        }
    }

    /**
     * Off-track colour, polarity-aware against the card surface the switch
     * sits on. Dark card: solid dim grey — a firm unlit track, deeper and
     * more solid than the old translucent white mist. Light card: near-black
     * at high alpha, because the old white mist was invisible on pale cards.
     * Pending in-game visual acceptance, tuned blind.
     */
    private static int trackOff() {
        return trackOff(ThemeService.colorIsLight(UiTokens.cardCutout()));
    }

    /**
     * The polarity pair as a pure function (package-private for contract
     * tests): {@code lightCard} is the caller-resolved card polarity.
     */
    static int trackOff(boolean lightCard) {
        return lightCard
                ? Color.makeARGB(110, 30, 30, 34)
                : Color.makeARGB(255, 58, 58, 62);
    }

    /**
     * Alpha-only dim (the disabled state); RGB untouched. Package-private
     * for contract tests.
     */
    static int dim(int color, float opacity) {
        int a = Math.round(((color >>> 24) & 0xFF) * opacity);
        return Color.makeARGB(a, (color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF);
    }
}
