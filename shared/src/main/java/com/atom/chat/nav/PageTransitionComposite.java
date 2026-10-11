package com.atom.chat.nav;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.settings.ProfilePageWiring;
import com.atom.chat.settings.SettingsDetailPage;
import com.atom.chat.ui.ComposerInput;
import com.atom.chat.ui.ContextMenuActions;
import com.atom.chat.ui.ContextMenuController;
import com.atom.chat.ui.HeaderChrome;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

/**
 * The transition frame compositor (D24b-2a): which page layers a frame paints,
 * where each one lands, at what opacity, and in what order.
 *
 * <p><b>Why the whole composite is one class.</b> The three pieces are one
 * frame's flow, not three features: the root-page early return, the pushe
 * detail/root composite, and the nav tail that closes the moving layer and
 * paints the fixed header all pass the same per-frame handles between them
 * ({@code navMovingLayer}, {@code navMovingSlide}, {@code navMovingHidden},
 * {@code suppressHeader}). A split in the middle would produce two classes that
 * each hold half a frame and have to hand the handles back and forth — worse
 * than the size it would save. The class is therefore above the usual 300-line
 * guideline by explicit exemption (main session, block 24b-2), and the flow is
 * sectioned into one private method per branch instead.
 *
 * <p><b>What this class is for.</b> Until this block the composite had no
 * offline coverage at all: the golden frames render page objects and never
 * composite a transition, so "the leaving page slides left and the arriving one
 * enters from the right", "only one side is painted per zoom phase", "the
 * settled layer is skipped as pure overdraw" and "the save/saveLayer pairs are
 * balanced" were all source-guard facts with no pixel evidence. The class draws
 * onto a plain Skija canvas with the page bodies behind ports, so
 * {@code PageTransitionCompositeTest} composites real frames on a CPU raster
 * surface and asserts both the geometry (where each side was asked to draw, at
 * what alpha) and the pixels (which side's colour landed where) — six paths,
 * each mutation-verified.
 *
 * <p><b>What it deliberately does not own.</b> The chat page body itself
 * ({@code drawChatPageBody}) stays on the screen: its segments belong to D22 /
 * D1 / D9 / D21 / D11 and moving it here would both need a pile of ports and put
 * page painting inside the navigation layer. The root page orchestration
 * ({@code drawRootScreen} and friends) stays as well until block 24b-2b, so this
 * class reaches it through its port for now.
 *
 * <p><b>Bucket.</b> Shared single source: no {@code net.minecraft} name appears
 * here — {@code Canvas}/{@code Paint}/{@code Rect} are Skija's, everything else
 * is shared UI or the nav layer itself.
 */
public final class PageTransitionComposite {

    /**
     * Render state of a chat page (the E group's record): the draft, read-only
     * hint, reply bar and scroll the page is drawn with, resolved per page so a
     * transition can composite two of them in one frame from the one shared
     * {@code EditBox}. {@code null} means the page has no composer.
     */
    public record ChatPageView(NavPage page, String draft, boolean readOnly, String readOnlyHint,
                               boolean partnerTyping, ChatMessage reply, ScrollController scroll,
                               boolean ownsField) {
    }

    /**
     * What the screen still owns: the page-body entry points and the facts the
     * compositor has no business holding. Nine methods — every one of them a
     * one-line forward on the screen's side.
     */
    public interface Host {
        /** The page on top right now. Re-read mid-frame: a finished nav moves the stack. */
        AppPage topPage();

        /** D25's field, written by the transition branches (six writes, two reads). */
        void suppressHeader(boolean value);

        /** The E group's view for one page. */
        ChatPageView viewFor(NavPage page);

        /** The chat page body -- stays on the screen (see the class note). */
        void drawChatPageBody(Canvas canvas, UiLayout layout, int mouseX, int mouseY, ChatPageView view);

        /** The root page orchestration -- moves in block 24b-2b. */
        void drawRootScreen(Canvas canvas, int mouseX, int mouseY, AppPage rootPage);

        /** The panel's ring, drawn last inside the panel. */
        void drawBezel(Canvas canvas, UiLayout layout);

        /** The root layout (tab bar mode), read by the nav tail for its fixed header. */
        UiLayout rootLayout();

        /** The pushed-page layout, read by the settings/profile early returns. */
        UiLayout detailLayout();

        /** D20's header action for a page (the feed filter, on the world chat only). */
        ShellHeader.HeaderAction headerActionFor(NavPage page);
    }

    /**
     * The composite's handle for one frame. Three shapes, and the screen acts on
     * exactly one of them:
     *
     * <ul>
     *   <li>{@link #noNav()} — no nav this frame: the screen draws its settled frame
     *       and never calls {@link #end};</li>
     *   <li>{@link #done()} — the composite completed the frame itself, so the screen
     *       returns without drawing anything else;</li>
     *   <li>{@code tailCloses} — the frame is mid-nav and one save-point is open for the
     *       screen's own chat body to be painted inside it. The screen draws that body
     *       and then calls {@link #end} with this handle, which closes it. Every other
     *       frame has already closed what it opened.</li>
     * </ul>
     *
     * <p>{@code running} is true whenever the nav is still in flight, so the screen's
     * two settled-page early returns stay skipped for the whole transition.
     */
    public record NavFrame(Paint movingLayer, boolean movingSlide, boolean movingHidden,
                           boolean running, boolean finished, boolean tailCloses) {
        /**
         * No nav this frame: the screen draws its settled frame and never calls
         * {@link #end}. (The factories cannot be called {@code none}/{@code finished}
         * next to the record's own {@code finished} accessor -- a static method with
         * an accessor's name and signature is rejected.)
         */
        public static NavFrame noNav() {
            return new NavFrame(null, false, false, false, false, false);
        }

        /**
         * The composite completed the frame itself; the screen must return without
         * drawing anything else (the composite closed its own save-points).
         */
        public static NavFrame done() {
            return new NavFrame(null, false, false, false, true, false);
        }
    }

    private final Host host;
    private final PageTransitionDirector director;
    private final ComposerInput composer;
    private final HeaderChrome headerChrome;
    private final DrawPrimitives primitives;
    private final ContextMenuController<?> contextMenu;
    private final ContextMenuActions contextActions;
    private final ProfilePageWiring profileWiring;
    private final SettingsDetailPage settingsDetailPage;

    public PageTransitionComposite(Host host, PageTransitionDirector director, ComposerInput composer,
                                  HeaderChrome headerChrome, DrawPrimitives primitives,
                                  ContextMenuController<?> contextMenu,
                                  ContextMenuActions contextActions,
                                  ProfilePageWiring profileWiring,
                                  SettingsDetailPage settingsDetailPage) {
        this.host = host;
        this.director = director;
        this.composer = composer;
        this.headerChrome = headerChrome;
        this.primitives = primitives;
        this.contextMenu = contextMenu;
        this.contextActions = contextActions;
        this.profileWiring = profileWiring;
        this.settingsDetailPage = settingsDetailPage;
    }

    /**
     * The 11a branch: a root page is on top, so the frame is the root screen, the
     * context menu over it and the bezel — nothing of the nav machinery runs.
     *
     * @return true when the frame is finished and the screen must return.
     */
    public boolean drawRootFrame(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                float vmx, float vmy) {
        if (!host.topPage().isRoot()) {
            return false;
        }
        host.drawRootScreen(canvas, mouseX, mouseY, host.topPage());
        contextMenu.draw(canvas, vmx, vmy);
        host.drawBezel(canvas, layout);
        return true;
    }

    /**
     * The 11b/11c branches: advances the nav clock and composites the frame.
     *
     * <p>Returns {@link NavFrame#noNav()} when no nav is running (the screen then
     * draws its settled frame and never calls {@link #end}); a frame with
     * {@code finished} set when the composite completed the frame itself; and
     * otherwise the open moving layer for {@link #end} to close.
     */
    public NavFrame begin(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                          float vmx, float vmy, long frameDt) {
        if (!director.pageNavActive()) {
            return NavFrame.noNav();
        }
        if (director.pageNavZoom()) {
            director.stepZoomNav(frameDt);
        } else {
            director.stepSlideNav(frameDt);
        }
        if (!director.pageNavActive()) {
            // The clock landed inside this frame, so the nav is over and the page it was
            // heading for is the settled top page. A root one makes the whole frame the
            // root screen (drawn here, exactly as the 11a branch does); any other one is
            // left to the screen's own settled draws — the shape the pre-nav code had by
            // falling out of its nav branch, which is why this arm must report
            // {@link NavFrame#noNav()} and not {@code done()}: the screen returns on a
            // finished frame, so "done" here would paint the panel base and the banner
            // and skip the body, the header and the bezel for a whole frame — and with
            // decorative motion off the tau is 0, so that frame is the first frame of
            // every navigation.
            director.finishPageNav();
            if (host.topPage().isRoot()) {
                host.drawRootScreen(canvas, mouseX, mouseY, host.topPage());
                host.drawBezel(canvas, layout);
                return NavFrame.done();
            }
            return NavFrame.noNav();
        }
        if (director.pageNavFrom() != null && director.pageNavTo() != null
                && !director.pageNavFrom().isRoot() && !director.pageNavTo().isRoot()) {
            return detailToDetail(canvas, layout, mouseX, mouseY);
        }
        return rootToDetail(canvas, layout, mouseX, mouseY, vmx, vmy);
    }

    /**
     * Detail-to-detail (public &lt;-&gt; private, chat &lt;-&gt; profile) full-width
     * push/pop. Same style rule as root&lt;-&gt;detail: SLIDE translates two whole
     * pages, ZOOM runs the shared serial fade+scale. Each side is drawn from its
     * own page view — the hidden EditBox is shared, so nothing may re-read
     * topPage() state while the other page is composited — and no chrome is
     * repainted fixed outside the moving layer: the composer deck belongs to its
     * page and travels with it.
     */
    private NavFrame detailToDetail(Canvas canvas, UiLayout layout, int mouseX, int mouseY) {
        boolean popping = director.pageNavPopPending();
        NavPage fromPage = director.pageNavFrom();
        NavPage toPage = director.pageNavTo();
        float travel = layout.list.w();
        float progress = director.navSlideProgress();
        // Both whole pages ride one pair of offsets, assigned by role and not by
        // page kind: the page on top of the stack during the nav -- the
        // destination on a push, the page being left on a pop -- uses the shared
        // director.pageNavDx(travel) expression the root<->detail slide rides,
        // and the other side its mirror.
        float slideDx = director.pageNavDx(travel);
        float fromDx = popping ? slideDx : -travel * progress;
        float toDx = popping ? -travel * (1.0F - progress) : slideDx;
        host.suppressHeader(true);
        // Per-page render state: draft, read-only hint, reply bar and scroll come
        // from the page they are drawn for. A page with no composer (settings
        // sub-page, profile detail) has no view and is drawn by its own body
        // method.
        ChatPageView fromView = host.viewFor(fromPage);
        ChatPageView toView = host.viewFor(toPage);
        if (fromView != null || toView != null) {
            // Grow the bar before the list is measured, and grow it for whichever
            // side needs the taller composer: the composer is part of the moving
            // page now, so the shared layout has to be final -- and tall enough
            // for both pages -- before either side paints.
            float width = layout.inputTextMaxWidth();
            layout = composer.updateLayout(layout, composer.text(),
                    Math.max(inputExtraFor(fromView, width), inputExtraFor(toView, width)));
        }
        UiLayout.Rect panelRect = layout.rect();
        if (director.pageNavZoom()) {
            // ZOOM: one whole side per phase, on the shared serial clock -- the
            // same serial zoom channels the root<->detail push rides, so the
            // setting means the same motion on every pair. EXIT drains the page
            // being left (alpha 1 -> 0 at scale 1.0 -> 0.9), ENTER settles the
            // arriving one (0 -> 1 at 1.1 -> 1.0). The invisible phase is skipped
            // outright, so the halves never double-expose, and a settled
            // alpha+scale skips the layer (pure overdraw, the state
            // decorative-motion-off lands in on frame one). EXIT paints the
            // leaving page whole and ENTER the arriving one whole -- never a bare
            // message list with the chrome of the other side left standing.
            boolean exiting = director.zoomExiting();
            NavPage visiblePage = exiting ? fromPage : toPage;
            ChatPageView visibleView = exiting ? fromView : toView;
            float alpha = exiting ? director.navExitAlpha() : director.navEnterAlpha();
            float scale = exiting ? 0.9F + alpha * 0.1F : 1.1F - alpha * 0.1F;
            Paint layer = null;
            canvas.save();
            if (!PageTransitionDirector.navLayerSkippable(alpha, scale)) {
                layer = new Paint().setAlphaf(alpha);
                canvas.saveLayer(Rect.makeXYWH(panelRect.x(), panelRect.y(),
                        panelRect.w(), panelRect.h()), layer);
            }
            // Same rounded panel clip and centre scale as the root<->detail zoom:
            // the moving body follows the bezel corner radius instead of biting
            // it with square edges.
            SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(),
                    UiTokens.panelRadius());
            PageTransitionDirector.applyNavScale(canvas, panelRect, scale);
            if (visibleView != null) {
                host.drawChatPageBody(canvas, layout, mouseX, mouseY, visibleView);
            } else if (visiblePage.page() == AppPage.PROFILE_DETAIL) {
                profileWiring.drawProfileDetail(canvas, mouseX, mouseY);
            } else if (visiblePage.page() == AppPage.SETTINGS_SECTION) {
                // The pages without a composer draw their own body. Leaving this
                // side blank would composite one page against nothing for the
                // whole transition.
                settingsDetailPage.drawSettingsSection(canvas, mouseX, mouseY, visiblePage.section());
            }
            if (layer != null) {
                canvas.restore();
                layer.close();
            }
            canvas.restore();
        } else {
            // SLIDE: both whole pages travel as one piece each -- the messages,
            // reply bar, composer, scrollbar and jump-to-latest of a chat page go
            // together with it, so the composer never sits still while its
            // conversation slides past (and the incoming one no longer pops in).
            canvas.save();
            SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(),
                    UiTokens.panelRadius());
            canvas.translate(fromDx, 0.0F);
            if (fromView != null) {
                host.drawChatPageBody(canvas, layout, mouseX, mouseY, fromView);
            } else if (fromPage.page() == AppPage.PROFILE_DETAIL) {
                profileWiring.drawProfileDetail(canvas, mouseX, mouseY);
            } else if (fromPage.page() == AppPage.SETTINGS_SECTION) {
                settingsDetailPage.drawSettingsSection(canvas, mouseX, mouseY, fromPage.section());
            }
            canvas.restore();

            canvas.save();
            SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(),
                    UiTokens.panelRadius());
            canvas.translate(toDx, 0.0F);
            if (toView != null) {
                host.drawChatPageBody(canvas, layout, mouseX, mouseY, toView);
            } else if (toPage.page() == AppPage.PROFILE_DETAIL) {
                profileWiring.drawProfileDetail(canvas, mouseX, mouseY);
            } else if (toPage.page() == AppPage.SETTINGS_SECTION) {
                settingsDetailPage.drawSettingsSection(canvas, mouseX, mouseY, toPage.section());
            }
            canvas.restore();
        }
        host.suppressHeader(false);
        ShellHeader.render(canvas, layout.header, headerChrome.shellTitleFor(toPage), true,
                headerChrome.backButton(), headerChrome.backButtonHover(), primitives.textPrimary(),
                toPage.page() == AppPage.PRIVATE_CHAT
                        ? contextActions.isOnlinePlayer(toPage.target()) : null,
                host.headerActionFor(toPage));
        host.drawBezel(canvas, layout);
        return NavFrame.done();
    }

    /**
     * Full-width push/pop: root&lt;-&gt;detail transitions. The header is drawn
     * separately as a fixed "status bar" after both layers.
     */
    private NavFrame rootToDetail(Canvas canvas, UiLayout layout, int mouseX, int mouseY,
                                  float vmx, float vmy) {
        host.suppressHeader(true);
        NavPage navRoot = director.pageNavTo().isRoot() ? director.pageNavTo() : director.pageNavFrom();
        NavPage moving = director.pageNavTo().isRoot() ? director.pageNavFrom() : director.pageNavTo();
        float travel = layout.rect().w();
        float progress = director.navSlideProgress();
        boolean pushing = !director.pageNavTo().isRoot();
        // One eased clock, two paired layers. ZOOM gives the change depth: the
        // moving page grows 0.94 -> 1 while fading in while the page underneath
        // sinks 1 -> 0.96 and dims; a pop runs the same channels in reverse so the
        // return matches its push (the old code slid every pop, whatever the
        // style). SLIDE keeps the horizontal push. The fixed chrome (header,
        // bezel) never joins the transforms.
        boolean zoom = director.pageNavZoom();
        // Melodify's serial zoom: while EXIT runs only the leaving page is
        // composited (shrinking 1.0 -> 0.9 as it fades); while ENTER runs only the
        // arriving page (settling 1.1 -> 1.0 as it fades in). The invisible side
        // gets alpha 0 and is skipped below, so the phases never stack into a
        // double-exposure. Slides keep the paired translation.
        float movingAlpha;
        float movingScale;
        float rootAlpha;
        float rootScale;
        if (zoom) {
            if (director.zoomExiting()) {
                float a = director.navExitAlpha();
                float s = 0.9F + a * 0.1F;
                if (pushing) {
                    rootAlpha = a;
                    rootScale = s;
                    movingAlpha = 0.0F;
                    movingScale = 1.0F;
                } else {
                    movingAlpha = a;
                    movingScale = s;
                    rootAlpha = 0.0F;
                    rootScale = 1.0F;
                }
            } else {
                float a = director.navEnterAlpha();
                float s = 1.1F - a * 0.1F;
                if (pushing) {
                    movingAlpha = a;
                    movingScale = s;
                    rootAlpha = 0.0F;
                    rootScale = 1.0F;
                } else {
                    rootAlpha = a;
                    rootScale = s;
                    movingAlpha = 0.0F;
                    movingScale = 1.0F;
                }
            }
        } else {
            // The slide's alpha/scale channels are unused (it translates);
            // keeping the assignment local to the else would leave them
            // unassigned for the branch above.
            movingAlpha = pushing ? progress : 1.0F - progress;
            movingScale = pushing ? 0.94F + 0.06F * progress : 1.0F - 0.06F * progress;
            rootAlpha = pushing ? 1.0F - 0.5F * progress : 0.5F + 0.5F * progress;
            rootScale = pushing ? 1.0F - 0.04F * progress : 0.96F + 0.04F * progress;
        }
        float rootDx = pushing ? -travel * progress : -travel * (1.0F - progress);
        UiLayout.Rect panelRect = layout.rect();
        // Bottom layer: on a zoom it sinks and dims inside its own saveLayer (the
        // layer bounds double as the panel clip); on a slide it just translates.
        // save and saveLayer each push their own entry, so a zoom pairs two
        // restores below; a skipped layer or a slide leaves just the plain save
        // to pop. During the zoom phase where the root is the invisible side,
        // both the layer and the draw are skipped.
        boolean rootVisible = !zoom || rootAlpha > 0.001F;
        canvas.save();
        Paint rootLayer = null;
        if (zoom) {
            if (rootVisible && !PageTransitionDirector.navLayerSkippable(rootAlpha, rootScale)) {
                rootLayer = new Paint().setAlphaf(rootAlpha);
                canvas.saveLayer(Rect.makeXYWH(panelRect.x(), panelRect.y(),
                        panelRect.w(), panelRect.h()), rootLayer);
                // The layer bounds clip square, so round the layer's content to
                // the panel radius right away — the scaled page must not paint
                // into the bezel's corners.
                SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(),
                        UiTokens.panelRadius());
                PageTransitionDirector.applyNavScale(canvas, panelRect, rootScale);
            }
        } else {
            SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(),
                    UiTokens.panelRadius());
            canvas.translate(rootDx, 0.0F);
        }
        if (rootVisible) {
            host.drawRootScreen(canvas, mouseX, mouseY, navRoot.page());
        }
        if (rootLayer != null) {
            // The zoom's saveLayer sits on top of the plain save; pop it first so each
            // entry pairs with its own restore. This is the LAYER's lifecycle, not the
            // draw's: it pairs with the saveLayer above whatever the draw turned out to
            // be, so the phase where the root side is skipped outright (a push's ENTER)
            // still closes it.
            canvas.restore();
            rootLayer.close();
        }
        canvas.restore();

        // Moving layer: on a zoom it fades+scales inside a saveLayer; on a slide
        // it translates under a plain save. Exactly one save-point is opened here
        // — the settings/profile branches close it before returning, the chat
        // page falls through and closes it after the shared tail below. While the
        // moving side is the zoom's invisible phase, no layer opens and the draws
        // below are skipped (drawing without the layer would composite straight
        // onto the visible side).
        boolean movingVisible = !zoom || movingAlpha > 0.001F;
        boolean navMovingHidden = zoom && !movingVisible;
        Paint navMovingLayer = null;
        boolean navMovingSlide = false;
        if (zoom) {
            if (movingVisible && !PageTransitionDirector.navLayerSkippable(movingAlpha, movingScale)) {
                navMovingLayer = new Paint().setAlphaf(movingAlpha);
                canvas.saveLayer(Rect.makeXYWH(panelRect.x(), panelRect.y(),
                        panelRect.w(), panelRect.h()), navMovingLayer);
                // Same rounded layer clip as the root side above.
                SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(),
                        UiTokens.panelRadius());
                PageTransitionDirector.applyNavScale(canvas, panelRect, movingScale);
            }
        } else {
            canvas.save();
            SkiaDraw.clip(canvas, panelRect.x(), panelRect.y(), panelRect.w(), panelRect.h(),
                    UiTokens.panelRadius());
            canvas.translate(director.pageNavDx(travel), 0.0F);
            navMovingSlide = true;
        }
        // A chat page's body is drawn by the SCREEN after this method returns (that is
        // what drawChatPageBody's port is for), so its save-point stays open and the
        // nav tail closes it. Every other page kind draws itself here and closes it
        // before this method returns.
        boolean chatBodyFollows = !moving.isRoot() && host.viewFor(moving) != null;
        // (viewFor is asked once here and its answer is handed on, so the frame stays one
        // resolution of the page's render state.)
        // A settings sub-page has no composer tail to draw into the open layer,
        // so it renders and closes its own layer here. The chat page leaves the
        // layer open for the shared tail below.
        if (moving.page() == AppPage.SETTINGS_SECTION) {
            if (movingVisible) {
                settingsDetailPage.drawSettingsSection(canvas, mouseX, mouseY, moving.section());
            }
            if (navMovingLayer != null) {
                canvas.restore();
                navMovingLayer.close();
            } else if (navMovingSlide) {
                // The slide branch opened a save; a skipped zoom layer opened
                // nothing, so it must not pop past it.
                canvas.restore();
            }
            chatBodyFollows = false;
            host.suppressHeader(false);
            // The header always names the destination, never the page that is
            // sliding away — a pop flips the title on frame one too, which is
            // what "click then title" expects.
            headerChrome.drawPushedHeader(canvas, vmx, vmy, pushing ? moving : navRoot);
            host.drawBezel(canvas, host.detailLayout());
            return NavFrame.done();
        }
        if (moving.page() == AppPage.PROFILE_DETAIL) {
            if (movingVisible) {
                profileWiring.drawProfileDetail(canvas, mouseX, mouseY);
            }
            if (navMovingLayer != null) {
                canvas.restore();
                navMovingLayer.close();
            } else if (navMovingSlide) {
                // Same pairing as the settings branch above.
                canvas.restore();
            }
            chatBodyFollows = false;
            host.suppressHeader(false);
            headerChrome.drawPushedHeader(canvas, vmx, vmy, pushing ? moving : navRoot);
            host.drawBezel(canvas, host.detailLayout());
            return NavFrame.done();
        }
        return new NavFrame(navMovingLayer, navMovingSlide, navMovingHidden,
                chatBodyFollows, false, chatBodyFollows);
    }

    /**
     * The 21 branch: closes the moving layer the chat page left open and paints
     * the fixed status-bar header, which never slides with the page bodies. Only
     * called when {@link #begin} returned a frame that is not finished.
     */
    public void end(Canvas canvas, UiLayout layout, NavFrame frame) {
        if (frame.movingLayer() != null) {
            canvas.restore();
            frame.movingLayer().close();
        } else if (frame.movingSlide()) {
            canvas.restore();
        }
        host.suppressHeader(false);
        // Fixed status-bar header: it never slides with the page bodies.
        NavPage to = director.pageNavTo();
        if (!to.isRoot()) {
            ShellHeader.render(canvas, layout.header, headerChrome.shellTitleFor(to), true,
                    headerChrome.backButton(), headerChrome.backButtonHover(), primitives.textPrimary(),
                    to.page() == AppPage.PRIVATE_CHAT
                            ? contextActions.isOnlinePlayer(to.target()) : null,
                    host.headerActionFor(to));
        } else {
            UiLayout root = host.rootLayout();
            ShellHeader.render(canvas, root.header, headerChrome.shellTitleFor(to), false, null, 0.0F,
                    primitives.textPrimary());
        }
    }

    /** How much taller the composer has to grow for one side, or 0 when it has none. */
    private float inputExtraFor(ChatPageView view, float maxWidth) {
        if (view == null || view.readOnly()) {
            return 0.0F;
        }
        return composer.extraFor(view.draft(), view.readOnly(), maxWidth);
    }
}
