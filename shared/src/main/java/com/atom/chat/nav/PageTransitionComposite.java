package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.PanelOpenMotion;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiStagger;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

/**
 * The arrival composite — the whole navigation transition is the arriving
 * page's <b>entrance overlay</b>, and this class owns its one piece of state:
 * the arrival stamp.
 *
 * <p><b>The semantics this class implements (the one-stamp rewrite).
 * The previous design was a two-page choreography: a leaving page animated out
 * while the arriving page animated in, with a director state machine per style,
 * a fast/interrupt clock, and a root-tab clock beside it. Users rejected every
 * consequence: "back during the animation returns with no transition", "the
 * global ZOOM style still slid the tab bar hard", "the global SLIDE entrance
 * jumped", "the back arrow lingered", "re-opening Appearance could not
 * interrupt". The one-stamp model has none of that machinery:</p>
 *
 * <ul>
 *   <li><b>No exit phase.</b> The old page stops being drawn the instant the
 *       key changes; only the arriving page ever paints. There is no leaving
 *       layer, no serial zoom hand-over, no fast/slow twin clock.</li>
 *   <li><b>The entrance overlay is the only transition.</b> The arriving page
 *       draws complete and interactive from its first frame, wrapped in a
 *       decorative envelope: a small fade plus a style variant — SLIDE a
 *       {@link UiStagger#PAGE_SLIDE_PX}px horizontal drift, ZOOM a
 *       {@link UiStagger#ZOOM_START_SCALE} scale-up. The per-item rise and the
 *       stagger stay with the page surfaces (unit V's {@code UiStagger}
 *       wiring); this class is the shell-level half of the same overlay.</li>
 *   <li><b>Interrupt = re-stamp.</b> A key change at any point re-arms the one
 *       stamp and replays the same entrance with the same numbers — no fast
 *       branch, no second clock. There is deliberately no direction state:
 *       the entrance always has the same shape regardless of push or pop.</li>
 *   <li><b>One motion per arrival.</b> A list page can run its own per-item
 *       stagger instead ({@link Host#arrivalStaggersSurfaces()}); the envelope
 *       is then skipped entirely, so a page never drifts while its rows
 *       rise.</li>
 * </ul>
 *
 * <p><b>What the screen still does.</b> The composite stamps and envelopes; the
 * page identity check is a plain {@link NavPage} equality, so a settings
 * section switch (same page kind, different {@code param}) re-stamps too.
 * Tab-style content can keep its stamp across a section switch to avoid a
 * flash of re-fade; AtomChat's settings sections are pushed pages, and the
 * user asked for an interruptible, resetting entrance there.</p>
 *
 * <p>Settled by default: the stamp starts unarmed, so the first frame of a
 * fresh screen (which establishes the initial key) plays nothing — an unarmed
 * stamp reads as long-settled.</p>
 *
 * <p><b>Bucket.</b> Shared single source: no {@code net.minecraft} name appears
 * here — {@code Canvas}/{@code Paint}/{@code Rect} are Skija's, the rest is
 * shared UI or this package.</p>
 */
public final class PageTransitionComposite {

    /**
     * What the screen still owns: the page identity per frame and the arriving
     * page's two draw entry points. The body dispatch (settings section /
     * profile detail / chat page) and the header stay on the screen — they are
     * the same draws the settled frame had, now the only draws there are.
     */
    public interface Host {
        /** The page on top right now, asked once per frame by {@link #beginFrame}. */
        NavPage topNav();

        /**
         * The arriving page's body. Called inside the entrance envelope; the
         * overlay transform is already applied, so this draws at rest
         * coordinates exactly like the old settled frame did.
         */
        void drawArrivingBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY);

        /**
         * The arriving page's header — drawn OUTSIDE the envelope, fixed at its
         * final position: it is chrome, not page content (the root pages draw
         * their header untransformed too). A title/back button that slid or
         * scaled with the body was the reported "switching pages moves the top
         * bar". The screen reads the back button's hover state before this runs.
         */
        void drawArrivingHeader(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                float vmx, float vmy);

        /**
         * Whether the arriving page runs its own per-item stagger instead of
         * the whole-page envelope (the list pages: settings section, profile
         * detail). The envelope is then skipped entirely — one transition, one
         * motion, never a page drifting while its rows rise.
         */
        boolean arrivalStaggersSurfaces();
    }

    private final Host host;
    /** The one piece of transition state left in the shell: the arrival stamp. */
    private final UiStagger.Entrance entrance = new UiStagger.Entrance();
    /** The page key last stamped; {@code null} until the first frame establishes it. */
    private NavPage stamped;
    /**
     * One-shot arrival flag: true on exactly the frame after a key change, so
     * the screen can re-arm the arriving surface's own entrance (the list's
     * item stagger, the settings rows') in step with this stamp.
     */
    private boolean arrivalEvent;
    /**
     * Whether the last key change was a pushed page returning to a root page.
     * The bottom bar is not drawn on pushed pages at all, so that is the one
     * arrival where it has to play its own (fade + lift) entrance; a root tab
     * switch keeps the bar still and only slides its capsule.
     */
    private boolean arrivalFromPushedToRoot;

    public PageTransitionComposite(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------- the frame flow

    /**
     * Establishes the frame's page key and stamps an arrival when it changed.
     * The screen calls this once per frame, before anything draws.
     */
    public void beginFrame(long nowMs) {
        NavPage top = host.topNav();
        if (top == null) {
            return;
        }
        if (stamped == null) {
            // First frame of the screen: establish the key, play nothing.
            stamped = top;
            return;
        }
        if (!top.equals(stamped)) {
            // An interrupt is nothing special any more: a key change at any
            // point re-stamps the one entrance, and the same 320ms/44px/0.92
            // numbers replay from zero (one clock, no fast branch).
            arrivalFromPushedToRoot = !stamped.page().isRoot() && top.page().isRoot();
            entrance.markEntered(nowMs);
            stamped = top;
            arrivalEvent = true;
        }
    }

    /** Whether this frame's arrival was a pushed page popping back to a root page. */
    public boolean arrivalFromPushedToRoot() {
        return arrivalFromPushedToRoot;
    }

    /**
     * Consumes the one-shot arrival flag — true on exactly the frame a key
     * change stamped, so the screen re-arms the surfaces' entrances once.
     */
    public boolean consumeArrivalEvent() {
        boolean event = arrivalEvent;
        arrivalEvent = false;
        return event;
    }

    /** The arrival stamp, for the surface re-arms and the overlay reads. */
    public UiStagger.Entrance entrance() {
        return entrance;
    }

    // ----------------------------------------------------- overlay reads

    /** The overlay fade at {@code nowMs}: 0 on the arrival frame, 1 at rest. */
    public float overlayAlpha(long nowMs) {
        return UiStagger.pageAlpha(entrance, nowMs);
    }

    /** The SLIDE variant's drift at {@code nowMs} (enters from the right). */
    public float overlaySlideDx(long nowMs) {
        return UiStagger.pageSlidePx(entrance, nowMs);
    }

    /** The ZOOM variant's scale at {@code nowMs}: 0.92 -> 1. */
    public float overlayZoomScale(long nowMs) {
        return UiStagger.pageZoomScale(entrance, nowMs);
    }

    /**
     * Whether the entrance has landed: no layer, no transform, no fade — the
     * frame is the plain settled draw.
     */
    public boolean overlaySettled(long nowMs) {
        return UiStagger.entranceSettled(entrance, nowMs);
    }

    /** The resolved page-nav style: the one config read, shared by every branch. */
    public AtomChatConfig.PageNavStyle style() {
        return AtomChatConfig.get().pageNavStyle;
    }

    // --------------------------------------------------- the arriving frame

    /**
     * Draws one pushed-page frame: the arriving page <em>body</em> inside the
     * entrance overlay, then the header outside it. The header is chrome, not
     * page content: the root pages already draw theirs fixed and untransformed
     * ({@code drawRootScreen}), and a title/back button that slid or scaled
     * with the body was the reported "when switching pages the top bar moves —
     * it never used to". Bezel and panel-edge chrome are excluded too.
     */
    public void drawArrivingFrame(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                  float vmx, float vmy, long nowMs) {
        if (!overlaySettled(nowMs) && !host.arrivalStaggersSurfaces()) {
            float alpha = overlayAlpha(nowMs);
            UiLayout.Rect panel = layout.rect();
            boolean zoom = style() == AtomChatConfig.PageNavStyle.ZOOM;
            float scale = zoom ? overlayZoomScale(nowMs) : 1.0F;
            // save() and saveLayer() push two entries; both are popped below — the
            // layer's restore first, then the plain save's. One restore short leaks
            // a save point into every entrance frame.
            canvas.save();
            Paint layer = new Paint().setAlphaf(alpha);
            canvas.saveLayer(Rect.makeXYWH(panel.x(), panel.y(), panel.w(), panel.h()), layer);
            // The layer's bounds clip square, so round the content to the panel
            // radius right away — the entering page must not paint into the
            // bezel's corners.
            SkiaDraw.clip(canvas, panel.x(), panel.y(), panel.w(), panel.h(),
                    UiTokens.panelRadius());
            if (zoom) {
                PanelOpenMotion.applyLayerScale(canvas, panel, scale);
            } else {
                canvas.translate(overlaySlideDx(nowMs), 0.0F);
            }
            host.drawArrivingBody(canvas, layout, mouseX, mouseY);
            canvas.restore();
            layer.close();
            canvas.restore();
        } else {
            host.drawArrivingBody(canvas, layout, mouseX, mouseY);
        }
        // Header last and untransformed, over the body: it is the fixed band
        // the entering page slides under.
        host.drawArrivingHeader(canvas, layout, mouseX, mouseY, vmx, vmy);
    }
}
