package com.atom.chat.ui;

import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;

/**
 * One toggle switch: the control behind every settings row.
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
    private static final int TRACK_OFF = Color.makeARGB(70, 255, 255, 255);
    private static final int KNOB = Color.makeARGB(255, 255, 255, 255);

    private final Animator anim = new Animator(Easing::easeOutCubic);
    /** Hover/press bounce; hit-testing stays unscaled upstream, this is draw-only. */
    private final PressScale scale = PressScale.control();
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
        scale.update(hover, pressed || pulsing(), dtMs, Animations.enabled());
    }

    /**
     * @param x left edge of the track
     * @param y top edge of the track
     * @param accent the on-state track colour
     */
    public void render(Canvas canvas, float x, float y, int accent) {
        scale.begin(canvas, x + UiTokens.SWITCH_W / 2.0F, y + UiTokens.SWITCH_H / 2.0F);
        try {
            float p = Math.max(0.0F, Math.min(1.0F, anim.getValue()));
            SkiaDraw.drawRoundedRect(canvas, x, y, UiTokens.SWITCH_W, UiTokens.SWITCH_H,
                    UiTokens.SWITCH_H / 2.0F, SkiaDraw.lerpColor(TRACK_OFF, accent, p));

            float travel = UiTokens.SWITCH_W - UiTokens.SWITCH_KNOB - UiTokens.SWITCH_INSET * 2.0F;
            float knobX = x + UiTokens.SWITCH_INSET + travel * p;
            float knobY = y + (UiTokens.SWITCH_H - UiTokens.SWITCH_KNOB) / 2.0F;
            SkiaDraw.drawRoundedRect(canvas, knobX, knobY, UiTokens.SWITCH_KNOB, UiTokens.SWITCH_KNOB,
                    UiTokens.SWITCH_KNOB / 2.0F, KNOB);
        } finally {
            canvas.restore();
        }
    }
}
