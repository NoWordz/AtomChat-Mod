package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.Animator;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import io.github.humbleui.skija.Canvas;

/**
 * Both shell transition state machines and the geometry they resolve (D24b-1).
 *
 * <p><b>Why this class exists.</b> The page push/pop clock and the root-tab
 * clock are pure state machines over a handful of floats: no Minecraft type, no
 * page object, no drawing of their own except the one panel-centred scale every
 * nav layer applies. Left on the screen they could not be asserted offline at
 * all — the screen needs the client to load — so the only evidence a refactor
 * kept them was a hand-written equivalence table. Here every step is a number in
 * and a number out, and {@code PageTransitionDirectorTest} pins the exact
 * values: the 0.02 hand-over of the serial zoom, the same-frame hand-over, the
 * push/pop sign of {@code pageNavDx}, the four style combinations, the
 * pure-overdraw skip, the panel-centred scale matrix and the slide/zoom arms of
 * the tab clock that clear the transition on the frame it completes.
 *
 * <p><b>What stayed behind.</b> Three steps need the page stack or a domain the
 * screen owns, so they go through {@link Host}: the draft save and the reply
 * capture a nav starts with, and the pop a finished pop transition applies. The
 * tab bar itself (the widget, its hit test and its draws) and the recorded root
 * pointer stay on the screen, because their consumers are the root draw/input
 * groups, not the state machine; the capsule animator, whose only writer is this
 * clock, is handed in.
 *
 * <p><b>Bucket.</b> Shared single source: no {@code net.minecraft} name appears
 * here. {@code Canvas} is Skija's, {@code Animator}/{@code UiLayout}/{@code UiMotion}
 * are shared, and both clocks read the shared {@code Animations} gate.
 *
 * <p>The two machines deliberately stay in one class: they share the single
 * {@link #pageNavStyle()} resolution and the same {@code expApproach} clock
 * family, and splitting them would let the root tabs and the page pushes drift
 * apart — which is exactly what a "detail&lt;-&gt;detail is always SLIDE" bug
 * looked like.
 */
public final class PageTransitionDirector {

    /**
     * The three steps this class cannot take itself.
     *
     * <p>Names are deliberately unlike any screen method: the source guards
     * locate members by first textual occurrence, so a port that borrowed a
     * screen method's name would capture the guard's anchor.
     */
    public interface Host {
        /** D2: the draft save every nav starts with. */
        void persistCurrentDraft();

        /** D1: the reply capture that goes with it, before the pages change. */
        void captureReplySlot();

        /**
         * The navigation pop a finished pop transition applies. The screen hands
         * this to the nav stack, which owns the cascade.
         */
        void applyPendingPop();
    }

    /** The capsule's value holder: the root tab bar's own animator. */
    private final Animator capsule;
    private final Host host;

    // ---- Page push/pop transition (root <-> detail). The navigation entry
    // changes only after the animation settles so the active page stays
    // consistent. No tween clock: the SLIDE style rides navSlideProgress and the
    // ZOOM style rides the serial state machine below — both exponential
    // (UiMotion.expApproach), so the motion starts at full speed and decays
    // instead of the eased tween's acceleration notch.
    private float navSlideProgress;
    private NavPage pageNavFrom;
    private NavPage pageNavTo;
    private boolean pageNavPopPending;
    /** Resolved once per nav in {@link #startPageNav} by {@link #pageNavStyle()}:
     *  true exactly while the configured style is ZOOM, for every page pair. */
    private boolean pageNavZoom;
    /**
     * Melodify-style serial zoom phases: the leaving page shrinks out first
     * (EXIT, alpha 1 -> 0 at scale 1.0 -> 0.9), then the arriving page
     * settles in (ENTER, alpha 0 -> 1 at scale 1.1 -> 1.0) — never two
     * half-transparent pages composited at once.
     */
    private static final int NAV_ZOOM_EXIT = 0;
    private static final int NAV_ZOOM_ENTER = 1;
    private int navZoomPhase = NAV_ZOOM_ENTER;
    private float navExitAlpha;
    private float navEnterAlpha;

    // ---- Root tab content transition, following the configured page-nav style
    // like the root<->detail push: ZOOM = the same serial shrink-out/settle
    // state machine, staged inside the list area; SLIDE = the two-page push
    // driven by the same exponential family. The capsule shares the tab bar's
    // animator as its value holder and is advanced from the same clock in
    // {@link #stepRootNav}, so pill and pages always land together.
    /** Resolved once per switch in {@link NavStackController#switchRoot}: ZOOM while the config style
     *  is ZOOM. */
    private boolean rootTabZoom;
    /** Serial zoom phases for the tab content, mirroring {@link #navZoomPhase}. */
    private static final int ROOT_ZOOM_EXIT = 0;
    private static final int ROOT_ZOOM_ENTER = 1;
    private int rootZoomPhase = ROOT_ZOOM_ENTER;
    private float rootZoomExitAlpha;
    private float rootZoomEnterAlpha;
    /** Exponential 0->1 clock of the SLIDE tab push; the page-nav twin is {@link #navSlideProgress}. */
    private float rootTabProgress;
    private int rootTabFrom = -1;
    private int rootTabTo = -1;

    public PageTransitionDirector(Host host, Animator capsule) {
        this.host = host;
        this.capsule = capsule;
    }

    // ------------------------------------------------- the page nav machine

    public boolean pageNavActive() {
        if (pageNavFrom == null || pageNavTo == null) {
            return false;
        }
        if (pageNavZoom) {
            return !(navZoomPhase == NAV_ZOOM_ENTER && navEnterAlpha >= 0.999F);
        }
        return navSlideProgress < 0.999F;
    }

    /**
     * Advances the serial zoom: the leaving page drains to alpha 0 (handing
     * over to ENTER at 0.02), then the arriving page fills to 1. With
     * decorative motion off the tau is 0 and both phases snap in one frame.
     * Sequential ifs on purpose: a fast exit can hand over to the enter
     * phase within the same step.
     */
    public void stepZoomNav(float dtMs) {
        float tau = Animations.enabled() ? UiMotion.PAGE_NAV_TAU_MS : 0.0F;
        if (navZoomPhase == NAV_ZOOM_EXIT) {
            navExitAlpha = UiMotion.expApproach(navExitAlpha, 0.0F, dtMs, tau);
            if (navExitAlpha <= 0.02F) {
                navExitAlpha = 0.0F;
                navZoomPhase = NAV_ZOOM_ENTER;
            }
        }
        if (navZoomPhase == NAV_ZOOM_ENTER && navEnterAlpha < 1.0F) {
            navEnterAlpha = UiMotion.expApproach(navEnterAlpha, 1.0F, dtMs, tau);
        }
    }

    /**
     * Advances the SLIDE progress on the same exponential language as the
     * zoom ({@link #stepZoomNav}): full-speed start, decaying tail, no eased
     * tween's acceleration notch. With decorative motion off the tau is 0
     * and the progress snaps in one frame.
     */
    public void stepSlideNav(float dtMs) {
        float tau = Animations.enabled() ? UiMotion.PAGE_SLIDE_TAU_MS : 0.0F;
        navSlideProgress = UiMotion.expApproach(navSlideProgress, 1.0F, dtMs, tau);
    }

    /** Arms the page transition. The style is resolved here, once per nav. */
    public void startPageNav(NavPage from, NavPage to, boolean popPending) {
        host.persistCurrentDraft();
        // The reply bar is captured with the draft: whichever page leaves keeps
        // its own bar, so the incoming page's composer is never drawn with it.
        host.captureReplySlot();
        pageNavFrom = from;
        pageNavTo = to;
        pageNavPopPending = popPending;
        // The style is resolved once per nav so push and pop can never
        // disagree mid-flight, and every pair goes through the one resolver
        // (pageNavStyle) — no page kind carries an animation rule of its own.
        AtomChatConfig.PageNavStyle style = pageNavStyle();
        pageNavZoom = style == AtomChatConfig.PageNavStyle.ZOOM;
        if (pageNavZoom) {
            // The zoom runs on its own two-phase clock (UiMotion.expApproach,
            // Melodify's M3 step); pageNavActive() reads the state machine.
            navZoomPhase = NAV_ZOOM_EXIT;
            navExitAlpha = 1.0F;
            navEnterAlpha = 0.0F;
        } else {
            // The slide rides the exponential progress; the Animations gate
            // sits inside stepSlideNav's tau, so decorative-motion-off snaps
            // there.
            navSlideProgress = 0.0F;
        }
    }

    /**
     * The one resolution path for every page switch: root tabs, root&lt;-&gt;detail,
     * detail&lt;-&gt;detail, pushes, pops and the banner-driven jumps all read the
     * configured style here, in both directions (a pop mirrors its push).
     *
     * <p>The pair used to be split by kind — root&lt;-&gt;detail followed the config
     * while detail&lt;-&gt;detail was pinned to SLIDE — which made the setting read
     * as "does nothing" on the public&lt;-&gt;private and chat&lt;-&gt;profile hops and put
     * a second, invisible rule next to the config field. No pair is special
     * any more: the style is whatever {@link AtomChatConfig#pageNavStyle} says.
     */
    public static AtomChatConfig.PageNavStyle pageNavStyle() {
        return AtomChatConfig.get().pageNavStyle;
    }

    /** Whether the configured style is the serial zoom, for the screen's port. */
    public boolean navStyleIsZoom() {
        return pageNavStyle() == AtomChatConfig.PageNavStyle.ZOOM;
    }

    public void finishPageNav() {
        if (pageNavPopPending) {
            host.applyPendingPop();
        }
        pageNavFrom = null;
        pageNavTo = null;
        pageNavPopPending = false;
        navSlideProgress = 0.0F;
    }

    /** Disarms the page transition without running it (the non-animated arm of a push). */
    public void abandonPageNav() {
        pageNavFrom = null;
        pageNavTo = null;
        pageNavPopPending = false;
        navSlideProgress = 0.0F;
    }

    /**
     * True when a zoom layer has nothing left to composite: the scale is back
     * at 1 and the layer is fully opaque. A full-panel saveLayer behind an
     * identity transform is pure overdraw, so the nav branch and the settled
     * shell panel skip the layer entirely in that state (motion off lands here
     * from frame one).
     */
    public static boolean navLayerSkippable(float alpha, float scale) {
        return alpha >= 0.999F && Math.abs(scale - 1.0F) < 0.001F;
    }

    /** Centers a nav scale around the panel middle. Draw-only: hit-tests
     *  keep unscaled coordinates, like every other transform in this screen. */
    public static void applyNavScale(Canvas canvas, UiLayout.Rect panelRect, float scale) {
        if (scale == 1.0F) {
            return;
        }
        float cx = panelRect.x() + panelRect.w() / 2.0F;
        float cy = panelRect.y() + panelRect.h() / 2.0F;
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.translate(-cx, -cy);
    }

    public float pageNavDx(float travel) {
        float progress = navSlideProgress;
        // Push: the incoming page enters from the right (travel -> 0).
        // Pop: the outgoing page leaves to the right (0 -> travel).
        // The old code decided by pageNavTo.isRoot(), which broke detail<->detail
        // pops (the destination WORLD_CHAT is also non-root).
        return pageNavPopPending ? travel * progress : travel * (1.0F - progress);
    }

    // ------------------------------------------------------ page nav reads

    public NavPage pageNavFrom() {
        return pageNavFrom;
    }

    public NavPage pageNavTo() {
        return pageNavTo;
    }

    public boolean pageNavPopPending() {
        return pageNavPopPending;
    }

    public boolean pageNavZoom() {
        return pageNavZoom;
    }

    public int navZoomPhase() {
        return navZoomPhase;
    }

    /** Whether the serial zoom is drawing the leaving page (the EXIT phase). */
    public boolean zoomExiting() {
        return navZoomPhase == NAV_ZOOM_EXIT;
    }

    public float navExitAlpha() {
        return navExitAlpha;
    }

    public float navEnterAlpha() {
        return navEnterAlpha;
    }

    public float navSlideProgress() {
        return navSlideProgress;
    }

    // ------------------------------------------------- the root tab machine

    public boolean rootTransitionActive() {
        if (rootTabFrom < 0 || rootTabTo < 0 || rootTabFrom == rootTabTo) {
            return false;
        }
        if (rootTabZoom) {
            return !(rootZoomPhase == ROOT_ZOOM_ENTER && rootZoomEnterAlpha >= 0.999F);
        }
        return rootTabProgress < 0.999F;
    }

    public void clearRootTransition() {
        rootTabFrom = -1;
        rootTabTo = -1;
    }

    /**
     * Advances the tab transition, style-aware like the page nav. The capsule
     * shares the tab bar's animator as its value holder and is written from
     * the same clock as the content: on a slide the pill position is the
     * progress mapped across the from->to cells (perfect lockstep), on a zoom
     * it keeps its own exponential travel at the zoom tau so it lands as the
     * enter phase settles. A transition that completes here is cleared at
     * once, so the same frame already draws the settled page.
     */
    public void stepRootNav(float dtMs) {
        if (!rootTransitionActive()) {
            return;
        }
        boolean enabled = Animations.enabled();
        if (rootTabZoom) {
            float tau = enabled ? UiMotion.PAGE_NAV_TAU_MS : 0.0F;
            if (rootZoomPhase == ROOT_ZOOM_EXIT) {
                rootZoomExitAlpha = UiMotion.expApproach(rootZoomExitAlpha, 0.0F, dtMs, tau);
                if (rootZoomExitAlpha <= 0.02F) {
                    rootZoomExitAlpha = 0.0F;
                    rootZoomPhase = ROOT_ZOOM_ENTER;
                }
            }
            if (rootZoomPhase == ROOT_ZOOM_ENTER && rootZoomEnterAlpha < 1.0F) {
                rootZoomEnterAlpha = UiMotion.expApproach(rootZoomEnterAlpha, 1.0F, dtMs, tau);
            }
            capsule.setValue(UiMotion.expApproach(capsule.getValue(), rootTabTo, dtMs, tau));
        } else {
            float tau = enabled ? UiMotion.PAGE_SLIDE_TAU_MS : 0.0F;
            rootTabProgress = UiMotion.expApproach(rootTabProgress, 1.0F, dtMs, tau);
            capsule.setValue(rootTabFrom + (rootTabTo - rootTabFrom) * rootTabProgress);
        }
        if (!rootTransitionActive()) {
            clearRootTransition();
        }
    }

    public AppPage rootPageForIndex(int index) {
        return switch (index) {
            case 0 -> AppPage.CHAT_LIST;
            case 1 -> AppPage.PROFILE;
            case 2 -> AppPage.SETTINGS;
            default -> throw new IllegalStateException("Unexpected root tab index " + index);
        };
    }

    public int rootIndex(AppPage page) {
        return switch (page) {
            case CHAT_LIST -> 0;
            case PROFILE -> 1;
            case SETTINGS -> 2;
            case WORLD_CHAT, PRIVATE_CHAT, SETTINGS_SECTION, PROFILE_DETAIL ->
                    throw new IllegalStateException("Pushed pages have no bottom tab index");
        };
    }

    /**
     * The four steps a tab switch is armed with, in the order the switch runs
     * them: the span, the style, the motion state, the capsule. They are one
     * method each rather than one call so the caller's sequence stays the
     * contract it always was.
     */
    public void pinTabCapsule(AppPage from) {
        capsule.setValue(rootIndex(from));
    }

    public void markTabSpan(AppPage from, AppPage to) {
        rootTabFrom = rootIndex(from);
        rootTabTo = rootIndex(to);
    }

    public void applyTabMotion(boolean zoom) {
        rootTabZoom = zoom;
        if (zoom) {
            rootZoomPhase = ROOT_ZOOM_EXIT;
            rootZoomExitAlpha = 1.0F;
            rootZoomEnterAlpha = 0.0F;
        } else {
            rootTabProgress = 0.0F;
        }
    }

    public void resetTabCapsule() {
        capsule.setValue(rootTabFrom);
    }

    // ------------------------------------------------------ root tab reads

    public boolean rootTabZoom() {
        return rootTabZoom;
    }

    public int rootTabFrom() {
        return rootTabFrom;
    }

    public int rootTabTo() {
        return rootTabTo;
    }

    public int rootZoomPhase() {
        return rootZoomPhase;
    }

    /** Whether the tab zoom is drawing the leaving tab (the EXIT phase). */
    public boolean rootZoomExiting() {
        return rootZoomPhase == ROOT_ZOOM_EXIT;
    }

    public float rootZoomExitAlpha() {
        return rootZoomExitAlpha;
    }

    public float rootZoomEnterAlpha() {
        return rootZoomEnterAlpha;
    }

    public float rootTabProgress() {
        return rootTabProgress;
    }
}
