package com.atom.chat.ui;

import com.atom.chat.render.Easing;

/**
 * Page-entrance stagger — the DangoMusic adoption, taken verbatim
 * ({@code Ui.kt:180-211}, {@code pageAt} + {@code pageProgress}/{@code itemAnim}):
 * <b>one</b> arrival stamp (Dango's {@code pageAt}) drives everything, and each
 * moving piece reads its own progress off that single clock:
 *
 * <pre>
 * p    = easeOutQuad((now - pageAt) / 320ms)                       // page motion
 * a    = easeOutQuad((now - pageAt - 26ms) / 294ms)                // page fade
 * p_i  = easeOutCubic((now - pageAt - min(i,9)*25ms) / 380ms)      // item i
 * page : alpha = a, offset = (1-p)*44px slide | 0.92+0.08p zoom
 * item : alpha = p_i, one axis per style (see {@link Axis}):
 *        SLIDE  -> dx = (1-p_i)*24px, dy = 0, scale = 1
 *        ZOOM   -> dy = (1-p_i)*18px, dx = 0, scale = 0.97+0.03p_i
 * </pre>
 *
 * <p>Structurally this cannot jump: alpha and the offset of a piece are the
 * <em>same</em> page clock, so a piece can never be seen at rest opacity in a
 * shifted position (or the reverse). Both the motion and the fade run
 * easeOutQuad — non-zero start velocity, so the page never looks paused for a
 * frame. Cubic was the earlier pick and it front-loaded 88% of the travel into
 * the first half of the clock, which is why the zoom read as "no curve, stiff";
 * quad keeps the drift and the fade readable together for the full 320ms. The
 * fade reads the same raw clock delayed by {@link #PAGE_ALPHA_DELAY_MS} (~8%),
 * so the page starts moving before it becomes visible — a fade that starts
 * with the motion has nothing to show at alpha 0.</p>
 *
 * <p><b>Why not ease-out motion plus ease-in fade.</b> That split was tried
 * and rejected against rendered frames: the drift finished while the page was
 * still mostly transparent, so the slide happened where nobody could see it.
 * A near-equal curve pair on one clock (motion first, fade ~26ms behind) is
 * what keeps both visible. Both amplitude increases (44px slide, 0.92 zoom)
 * exist so the two styles stay visually distinct now that list pages run
 * their own per-item entrance instead of the whole-page envelope.</p>
 *
 * <p><b>Settled by default.</b> An entrance holder starts long-settled, so a
 * surface that was never {@link #markEntered} (golden frames, offscreen
 * probes, a page whose owner has no nav flow) renders fully opaque at rest —
 * the animation exists only where the navigation explicitly arms it. With
 * decorative motion off every progress is 1.</p>
 *
 * <p>Bucket: shared single source, no Minecraft names. Pure arithmetic.</p>
 */
public final class UiStagger {
    /** The page/header envelope (Dango's 320ms page clock). */
    public static final long PAGE_MS = 320L;
    /**
     * The fade's head start on the page clock, in ms (~8% of {@link #PAGE_MS}).
     * The motion runs from the first frame; the fade starts here, so the slide
     * is already travelling while the page becomes visible. Both still read the
     * same raw stamp, so they cannot disagree about when the entrance began.
     */
    public static final long PAGE_ALPHA_DELAY_MS = 26L;
    /**
     * The SLIDE page-nav variant's whole-page horizontal excursion. Dango's own
     * 12px is a phone-width budget; on AtomChat's wide panel it read as "almost
     * nothing" in the field, and since list pages stagger per item the pushed
     * pages need a visibly different excursion from the ZOOM scale — 44px, on
     * the same 320ms clock as the fade.
     */
    public static final float PAGE_SLIDE_PX = 44.0F;
    /**
     * The ZOOM page-nav variant's start scale: 0.92 -> 1 while fading in.
     * Deeper than Dango's 0.96 because the per-item scale (0.97) now runs
     * underneath it and the two must not read as the same gesture.
     */
    public static final float ZOOM_START_SCALE = 0.92F;
    /** The item rise duration (Dango's 380ms item clock). */
    public static final long ITEM_MS = 380L;
    /**
     * The per-item stagger step — 25ms rather than Dango's 35ms, so a full
     * list lands in 225 + 380 = 605ms instead of 870ms.
     */
    public static final long ITEM_STAGGER_MS = 25L;
    /**
     * The item delay is {@code min(index, ITEM_STAGGER_CAP - 1) * ITEM_STAGGER_MS}
     * and the index is the row's position <em>inside the visible viewport</em>,
     * so scrolling never re-plays the entrance for rows already on screen.
     */
    public static final int ITEM_STAGGER_CAP = 10;
    /** The rise distance in logical px (Dango's 18px item rise). */
    public static final float ITEM_RISE_PX = 18.0F;
    /**
     * The SLIDE style's per-item horizontal drift: each row enters from the
     * right while it fades, so a list arrival carries the same directional
     * signature as a pushed page's drift. Driven by {@link #itemProgress}
     * (the rise's own clock), never a second one.
     *
     * <p>24px, not the previous 16. The 16 was sized while SLIDE also carried
     * the 18px vertical rise, so the two components split one "how far did the
     * item travel" budget. With the rise gone under SLIDE (see {@link Axis})
     * the horizontal leg is the only travel left, and 16 read in the field as
     * "it barely moved".</p>
     */
    public static final float ITEM_STYLE_SLIDE_PX = 24.0F;
    /**
     * The ZOOM style's per-item start scale: each row settles 0.97 -> 1 on the
     * rise clock. Small on purpose — a list is wide, so more than a few
     * percent reads as a layout wobble, not an entrance.
     */
    public static final float ITEM_STYLE_ZOOM_START = 0.97F;
    /**
     * The bottom bar's return lift: when a pushed page pops back to a root page
     * the bar rides the same page envelope up these px while fading in (it is
     * not drawn at all on pushed pages, so without this it would appear with
     * zero frames of its own).
     */
    public static final float TAB_BAR_RISE_PX = 10.0F;

    /**
     * Which of the two nav styles an item entrance is playing, as a motion
     * axis rather than a style flag: the two variants must not be able to
     * disagree about <em>which</em> channel they drive.
     *
     * <ul>
     *   <li>{@link #HORIZONTAL} — SLIDE. The item drifts in from the right and
     *       does <b>not</b> rise: the entrance is a horizontal gesture, and a
     *       list that also lifted would read as a diagonal, which is neither
     *       style ("斜向右上" was the field complaint). No scale either.</li>
     *   <li>{@link #VERTICAL} — ZOOM. The item rises and settles from
     *       {@link #ITEM_STYLE_ZOOM_START}, with no horizontal leg.</li>
     * </ul>
     *
     * <p>Every channel therefore takes the axis: an item can only ever move on
     * its own style's axis, and a caller cannot accidentally keep the other
     * style's component by forgetting a branch.</p>
     */
    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    /**
     * One page's entrance stamp — Dango's {@code pageAt}. Armed by the
     * navigation when the page becomes the one on screen; an interrupt re-arms
     * it with the exact same numbers. Long-settled until marked, so unmarked
     * surfaces never animate.
     */
    public static final class Entrance {
        private static final long SETTLED = Long.MIN_VALUE / 2L;
        private long enteredAtMs = SETTLED;

        /** Arms the entrance now (the emoji grid's own open clock). */
        public void markEntered() {
            markEntered(System.currentTimeMillis());
        }

        /** Arms the entrance at an explicit stamp — the shape the nav flow uses. */
        public void markEntered(long nowMs) {
            this.enteredAtMs = nowMs;
        }

        /** Long-settled until armed; a re-arm restarts the entrance. */
        public boolean armed() {
            return enteredAtMs != SETTLED;
        }

        /**
         * Back to settled without playing anything — used when another motion
         * already owns this arrival (the page envelope), so the same content is
         * never moved by two clocks at once.
         */
        public void markSettled() {
            enteredAtMs = SETTLED;
        }

        /** The raw stamp, so every surface can share the navigation's one clock. */
        public long enteredAtMs() {
            return enteredAtMs;
        }
    }

    private UiStagger() {
    }

    /**
     * The item entrance progress 0..1 for item {@code index}: item {@code i}
     * waits {@code min(i, 14) * 35ms}, then runs the 380ms rise (Dango's
     * {@code itemAnim}). Unarmed or decorative-motion-off holders read 1
     * (fully in place).
     */
    public static float itemProgress(Entrance entrance, int index, long nowMs) {
        return curve(entrance, rawItemDelayMs(index), ITEM_MS, nowMs, Easing::easeOutCubic);
    }

    /**
     * The item's rise offset in logical px: {@link #ITEM_RISE_PX} at progress
     * 0 under {@link Axis#VERTICAL}, and exactly 0 under
     * {@link Axis#HORIZONTAL} — SLIDE is a horizontal entrance and must not
     * carry a vertical leg.
     */
    public static float itemRise(Entrance entrance, int index, long nowMs, Axis axis) {
        if (axis == Axis.HORIZONTAL) {
            return 0.0F;
        }
        return (1.0F - itemProgress(entrance, index, nowMs)) * ITEM_RISE_PX;
    }

    /**
     * The item's fade 0..1 — the same progress that drives {@link #itemRise},
     * which is the point: Dango drives a piece's offset and its opacity from one
     * value ({@code shifted(dx, dy, alpha)} reads {@code p} three times,
     * {@code Ui.kt:182-199}). Fading a row with a second clock, or moving it
     * while it is still fully opaque, is what read in the field as "the card
     * jumps up on arrival".
     */
    public static float itemAlpha(Entrance entrance, int index, long nowMs) {
        return itemProgress(entrance, index, nowMs);
    }

    /**
     * The item's style component on the SLIDE side: the horizontal drift it
     * still owes, {@link #ITEM_STYLE_SLIDE_PX} at progress 0 and 0 at rest.
     * Exactly 0 under {@link Axis#VERTICAL}. Read by the list surfaces only,
     * and driven by the <em>same</em> {@link #itemProgress} as the rise and the
     * fade — the channels can never disagree about where a row is.
     */
    public static float itemStyleSlidePx(Entrance entrance, int index, long nowMs, Axis axis) {
        if (axis == Axis.VERTICAL) {
            return 0.0F;
        }
        return (1.0F - itemProgress(entrance, index, nowMs)) * ITEM_STYLE_SLIDE_PX;
    }

    /**
     * The item's style component on the ZOOM side: its scale at {@code nowMs},
     * {@link #ITEM_STYLE_ZOOM_START} at progress 0 and 1 at rest; exactly 1
     * under {@link Axis#HORIZONTAL}. Same clock as {@link #itemStyleSlidePx}.
     */
    public static float itemStyleZoomScale(Entrance entrance, int index, long nowMs, Axis axis) {
        if (axis == Axis.HORIZONTAL) {
            return 1.0F;
        }
        return ITEM_STYLE_ZOOM_START
                + (1.0F - ITEM_STYLE_ZOOM_START) * itemProgress(entrance, index, nowMs);
    }

    /**
     * The whole-page entrance motion progress 0..1 — the 320ms page clock on
     * easeOutQuad. The style motion (SLIDE drift / ZOOM scale) reads this value.
     *
     * <p><b>Why easeOutQuad and not ease-in-out cubic.</b> Cubic put 88% of the
     * travel into the first half of the clock, so the page arrived long before
     * the eye had anything to follow: the field read it as "没有曲线，显得生硬".
     * Quad still starts at full velocity (no pause-then-jump frame) but keeps
     * the last third of the clock moving, so the drift stays visible while the
     * fade catches up. The fade is the same curve on a clock delayed by
     * {@link #PAGE_ALPHA_DELAY_MS} — see {@link #pageAlpha} for why the delay
     * rather than a slower curve.</p>
     */
    public static float pageProgress(Entrance entrance, long nowMs) {
        return curve(entrance, 0L, PAGE_MS, nowMs, Easing::easeOutQuad);
    }

    /**
     * The whole-page overlay's fade, 0..1 — {@link #PAGE_ALPHA_DELAY_MS} behind
     * {@link #pageProgress} on the same raw stamp, on the same easeOutQuad.
     *
     * <p><b>Why a delay instead of a different curve.</b> Alpha at 0 is
     * invisible by definition, so a fade that starts with the motion spends its
     * first frames lighting up a page that has already moved most of the way.
     * Delaying the fade by 26ms (~8%) lets the page begin travelling first, then
     * become visible while still travelling. The delay is subtracted from the
     * same {@code enteredAtMs} the motion reads, so the two can never disagree
     * about the entrance start — only about when the fade joins in.</p>
     */
    public static float pageAlpha(Entrance entrance, long nowMs) {
        long fadeMs = PAGE_MS - PAGE_ALPHA_DELAY_MS;
        return curve(entrance, PAGE_ALPHA_DELAY_MS, fadeMs, nowMs, Easing::easeOutQuad);
    }

    /**
     * The SLIDE variant's horizontal excursion in logical px at {@code nowMs}:
     * {@link #PAGE_SLIDE_PX} at progress 0, 0 at rest. The caller applies the
     * sign (the overlay always enters from the right, Dango has no direction
     * state and neither does this). 0 unless the entrance is armed.
     */
    public static float pageSlidePx(Entrance entrance, long nowMs) {
        return (1.0F - pageProgress(entrance, nowMs)) * PAGE_SLIDE_PX;
    }

    /**
     * The ZOOM variant's scale at {@code nowMs}: {@link #ZOOM_START_SCALE} -> 1
     * with the same envelope.
     */
    public static float pageZoomScale(Entrance entrance, long nowMs) {
        return ZOOM_START_SCALE + (1.0F - ZOOM_START_SCALE) * pageProgress(entrance, nowMs);
    }

    /**
     * The bottom bar's return lift: {@link #TAB_BAR_RISE_PX} at progress 0
     * under {@link Axis#VERTICAL}; 0 under {@link Axis#HORIZONTAL}. The bar is
     * a fade-in either way, but SLIDE is the horizontal style, so it may not
     * hand the bar a vertical leg.
     */
    public static float tabBarRise(Entrance entrance, long nowMs, Axis axis) {
        if (axis == Axis.HORIZONTAL) {
            return 0.0F;
        }
        return (1.0F - pageProgress(entrance, nowMs)) * TAB_BAR_RISE_PX;
    }

    /** The bottom bar's return fade — the page envelope's alpha. */
    public static float tabBarAlpha(Entrance entrance, long nowMs) {
        return pageAlpha(entrance, nowMs);
    }

    /**
     * Whether the shell envelope has landed — the 320ms page clock (the fade
     * reaches 1 and the drift/scale reaches rest here). This is the
     * overlay-skip test: past it a frame is the plain settled draw. An unarmed
     * entrance is settled by definition.
     */
    public static boolean entranceSettled(Entrance entrance, long nowMs) {
        return rawProgress(entrance, 0L, PAGE_MS, nowMs) >= 1.0F;
    }

    /**
     * Whether every item slot has landed — the last (capped) item's rise clock
     * has run out, so no row still owes drift or lift. The list surfaces use it
     * to drop the widened viewport clip as soon as there is no travel left to
     * contain; sampling the capped slot covers every index below it.
     */
    public static boolean itemsSettled(Entrance entrance, long nowMs) {
        return rawProgress(entrance, rawItemDelayMs(ITEM_STAGGER_CAP - 1), ITEM_MS, nowMs) >= 1.0F;
    }

    private static long rawItemDelayMs(int index) {
        return (long) Math.min(index, ITEM_STAGGER_CAP - 1) * ITEM_STAGGER_MS;
    }

    /** Clamped 0..1 clock; 1 for an unarmed holder or with motion off. */
    private static float rawProgress(Entrance entrance, long delayMs, long durationMs, long nowMs) {
        if (entrance == null || !entrance.armed() || !Animations.enabled()) {
            return 1.0F;
        }
        float t = (nowMs - entrance.enteredAtMs - delayMs) / (float) durationMs;
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    private static float curve(Entrance entrance, long delayMs, long durationMs, long nowMs,
                               java.util.function.Function<Float, Float> easing) {
        return easing.apply(rawProgress(entrance, delayMs, durationMs, nowMs));
    }
}
