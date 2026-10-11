package com.atom.chat.ui;

import com.atom.chat.chat.ConversationKeys;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.render.SkiaDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

import java.util.HashMap;
import java.util.Map;

/**
 * Scroll state, its scrollbar and the jump-to-latest pill (D11): the four
 * controllers, the per-conversation lookup that hands one to a page, the
 * per-frame motion push and the two float controls that read them.
 *
 * <p>Two orderings in here are load-bearing and are kept exactly as the screen
 * had them:
 * <ul>
 *   <li>{@link #drawScrollbar}: the thumb's Y is computed <em>before</em> the
 *       drag update, so the frame is drawn from the offset captured at frame
 *       start. Swapping the two makes the thumb lag a frame behind the pointer
 *       while dragging;</li>
 *   <li>{@link #drawJumpLatest}: the fade, hover and spring values are shared
 *       screen state, so they are stepped only by the page that owns the
 *       composer ({@code advance}) and the other side of a transition merely
 *       re-draws them; the spring step also sits <em>before</em> the fade gate,
 *       because the click that presses the pill scrolls to the bottom and hides
 *       it on the very next frame — behind the gate the release half of the
 *       bounce would never be painted.</li>
 * </ul>
 * The existing seam between the two is deliberate and unchanged:
 * {@link #overJumpLatest} hit-tests against the current page's scroll while
 * {@link #drawJumpLatest} draws from the page it is given.
 *
 * <p>Everything it wants from the screen arrives through {@link Host}: the frame
 * clock, where the navigation stack is (D24) and the drawing primitives it draws
 * the pill's glyph with (D9). No Minecraft type is named here.
 */
public final class ScrollSyncService {
    /** The screen-side facts the scrolling controls read. */
    public interface Host {
        /** Frame delta in ms, the clock the fade and the FAB springs step on. */
        long frameDt();

        /** The page kind on top of the nav stack (D24's {@code topPage()}). */
        AppPage topPage();

        /** The private partner on top of the stack, or null (D24's {@code activePrivateTarget()}). */
        PlayerRef activePrivateTarget();

        /** The drawing primitives (D9): the pill's glyph and the ARGB blend. */
        DrawPrimitives primitives();
    }

    private final Host host;

    /** Scroll state for the world-chat message list. */
    private final ScrollController worldScroll = new ScrollController();
    /** Per-private-conversation scroll controllers; keys are {@link PlayerRef#key()}. */
    private final Map<String, ScrollController> privateScrolls = new HashMap<>();
    /** Scroll state for root pages; shared across the root tabs, opening at the top. */
    private final ScrollController rootScroll = new ScrollController(false);
    /** Scroll state for pushed detail pages (settings/profile), opening at the top. */
    private final ScrollController detailScroll = new ScrollController(false);

    private float jumpLatestAnim;
    /** Eased hover of the jump-to-latest FAB, matching the composer keys' fade. */
    private float jumpLatestHover;
    /** Bounce spring for the FAB; the scaled shape is the round pill itself. */
    private final PressScale jumpLatestScale = PressScale.bounce();
    /** True while the FAB is held down, so the release can bounce back. */
    private boolean jumpLatestHeld;

    public ScrollSyncService(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------- the controllers

    /** Scroll state for the world-chat message list (the feed's own controller). */
    public ScrollController worldScroll() {
        return worldScroll;
    }

    /** Scroll state for pushed detail pages (settings/profile). */
    public ScrollController detailScroll() {
        return detailScroll;
    }

    /** Scroll state for root pages. */
    public ScrollController rootScroll() {
        return rootScroll;
    }

    /**
     * Scroll controller of the page on top of the stack: a private conversation
     * gets its own (created on first use), everything else the world feed's.
     */
    public ScrollController currentScroll() {
        if (host.topPage() == AppPage.PRIVATE_CHAT) {
            PlayerRef target = host.activePrivateTarget();
            if (target == null) {
                return worldScroll;
            }
            return privateScrolls.computeIfAbsent(target.key(), k -> new ScrollController());
        }
        return worldScroll;
    }

    /**
     * Scroll controller of one nav page — by that page, never by whatever sits on
     * top of the stack: a transition composites two pages in the same frame and
     * each side must read its own scroll.
     */
    public ScrollController scrollForNav(NavPage page) {
        if (page != null && page.page() == AppPage.PRIVATE_CHAT && page.target() != null) {
            return privateScrolls.computeIfAbsent(ConversationKeys.of(page), k -> new ScrollController());
        }
        return worldScroll;
    }

    /** Scroll controller of whatever list the top page is showing. */
    public ScrollController listScroll() {
        return host.topPage() == AppPage.SETTINGS_SECTION || host.topPage() == AppPage.PROFILE_DETAIL
                ? detailScroll : rootScroll;
    }

    /**
     * Pushes the decorative-motion flag into every live scroll controller once
     * per frame. The controllers stay pure (no config reads of their own), so
     * the flag has to be handed to them; private controllers are created on
     * demand and therefore have to be refreshed too.
     */
    public void syncScrollMotion() {
        boolean on = Animations.enabled();
        worldScroll.setDecorativeMotion(on);
        rootScroll.setDecorativeMotion(on);
        for (ScrollController controller : privateScrolls.values()) {
            controller.setDecorativeMotion(on);
        }
    }

    // ---------------------------------------------------------- the scrollbar

    /**
     * Rounded scrollbar: fades in only while the pointer is near the track or
     * while dragged, and fades straight back out otherwise.
     * Thumb maps scrollY to the track; drag maps 1:1 with clamping.
     */
    public void drawScrollbar(Canvas canvas, UiLayout layout, float vmx, float vmy,
                              ScrollController controller) {
        long now = System.currentTimeMillis();

        UiLayout.Rect list = layout.list;
        float trackW = UiTokens.s(6);
        float trackH = list.h();
        float visibleRatio = Math.min(1.0F, trackH / (trackH + controller.getMaxScroll()));
        float thumbH = Math.max(UiTokens.s(30), trackH * visibleRatio);
        // Deliberately computed before drag updates below, matching the original
        // frame ordering: the thumb is drawn from the offset captured at frame start.
        float thumbY = list.y() + (trackH - thumbH) * (controller.getScrollY() / controller.getMaxScroll());

        if (controller.updateScrollbarFade(now, vmx, vmy, list, controller.isDragging(), trackW,
                host.frameDt()) <= 0.0F) {
            return;
        }

        if (controller.isDragging()) {
            controller.dragTo(vmy, trackH);
        }

        float w = trackW + controller.getScrollEmphasis() * UiTokens.s(3);
        // The thumb rides the accent channel the whole way — a static white
        // thumb vanishes on light panels, so the idle low-alpha state reads
        // accent too, same emphasis language as the docked bar's active tab.
        int accent = AtomChatConfig.get().accentColor;
        int r = (accent >> 16) & 0xFF;
        int g = (accent >> 8) & 0xFF;
        int bch = accent & 0xFF;
        // Mth.clamp(x, 0, 255), written out: shared code cannot see net.minecraft,
        // and this is that method's own definition (Math.min(Math.max(v, min), max)).
        int alpha = Math.min(Math.max((int) ((170 + 60 * controller.getScrollEmphasis())
                * controller.getScrollBarAlpha()), 0), 255);
        int color = (alpha << 24) | (r << 16) | (g << 8) | bch;
        float trackX = list.right() - trackW - UiTokens.s(2);
        SkiaDraw.drawRoundedRect(canvas, trackX - (w - trackW) / 2.0F, thumbY, w, thumbH, w / 2.0F, color);
    }

    public boolean overScrollbarTrack(UiLayout layout, float vmx, float vmy,
                                      ScrollController controller) {
        float trackW = UiTokens.s(6);
        float trackX = layout.list.right() - trackW - UiTokens.s(2);
        return controller.getMaxScroll() > 0.0F
                && vmx >= trackX - UiTokens.s(8) && vmx <= trackX + trackW + UiTokens.s(8)
                && vmy >= layout.list.y() && vmy <= layout.list.bottom();
    }

    // ------------------------------------------------------------ the FAB

    /**
     * The floating jump-to-latest pill.
     *
     * @param scroll  the drawn page's own scroll controller — the pill belongs to
     *                its page and must never read another page's scroll while a
     *                transition composites both
     * @param advance true for the page that owns the composer field: the fade,
     *                hover and spring values are shared screen state, so they are
     *                stepped once per frame and the second page of a transition
     *                only re-draws them
     */
    public void drawJumpLatest(Canvas canvas, UiLayout layout, float vmx, float vmy,
                               ScrollController scroll, boolean advance) {
        boolean show = scroll.getMaxScroll() > 0.0F && !scroll.isAtBottom();
        float size = UiTokens.s(38);
        float x = layout.list.right() - size - UiTokens.s(12);
        float y = layout.list.bottom() - size - UiTokens.s(12);
        boolean hover = vmx >= x && vmx <= x + size && vmy >= y && vmy <= y + size;
        // Same interaction language as every other control: the hover grey is
        // eased rather than switched, and the pill rides the shared bounce spring
        // so the press squashes it and the release bounces back. Both are stepped
        // BEFORE the fade gate below, because the click that presses the pill also
        // scrolls to the bottom and hides it on the very next frame — behind the
        // gate the release half of the bounce would never be drawn.
        long frameDt = host.frameDt();
        if (advance) {
            jumpLatestAnim = UiMotion.approach(jumpLatestAnim, show ? 1.0F : 0.0F, frameDt, UiMotion.POPUP_MS);
            jumpLatestHover = UiMotion.approach(jumpLatestHover, hover ? 1.0F : 0.0F,
                    frameDt, UiMotion.HOVER_MS);
            jumpLatestScale.update(hover, jumpLatestHeld, frameDt, Animations.enabled(), size);
        }
        // The fade and spring are shared screen state stepped by the owning page,
        // so a side that does not own the composer must not draw the pill from
        // them: it would show a pill belonging to the other page's scroll.
        if (jumpLatestAnim < 0.01F || (!advance && !show)) {
            return;
        }
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * jumpLatestAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(x - UiTokens.s(4), y - UiTokens.s(4),
                    size + UiTokens.s(8), size + UiTokens.s(8)), layer);
            float cx = x + size / 2.0F;
            float cy = y + size / 2.0F;
            jumpLatestScale.begin(canvas, cx, cy);
            try {
                // Fixed dark floating pill (the jump FAB floats over arbitrary
                // message content, whose polarity no theme token knows), same fixed
                // polarity family as the popups; hover lifts the grey a notch.
                int bg = DrawPrimitives.lerpArgb(Color.makeARGB(235, 52, 58, 70),
                        Color.makeARGB(245, 70, 76, 90), jumpLatestHover);
                SkiaDraw.drawRoundedRect(canvas, x, y, size, size, size / 2.0F, bg);
                SkiaDraw.drawRoundedShadow(canvas, x, y, size, size, size / 2.0F, UiTokens.s(8),
                        UiTokens.CHROME_SHADOW);
                // Glyph pinned white like the pill itself: the pill is fixed dark,
                // so a themed text colour would go dark-on-dark on light themes.
                host.primitives().drawIconCentered(canvas, DrawPrimitives.ICON_JUMP_DOWN_PATH, cx, cy,
                        UiTokens.s(18), Color.makeARGB(255, 255, 255, 255));
            } finally {
                canvas.restore();
            }
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    /**
     * Hit test of the pill. It reads the <em>current</em> page's scroll while
     * {@link #drawJumpLatest} draws the page it is handed — a pre-existing seam,
     * kept as it was.
     */
    public boolean overJumpLatest(UiLayout layout, float vmx, float vmy) {
        ScrollController scroll = currentScroll();
        if (jumpLatestAnim < 0.01F || scroll.getMaxScroll() <= 0.0F || scroll.isAtBottom()) {
            return false;
        }
        float size = UiTokens.s(38);
        float x = layout.list.right() - size - UiTokens.s(12);
        float y = layout.list.bottom() - size - UiTokens.s(12);
        return vmx >= x && vmx <= x + size && vmy >= y && vmy <= y + size;
    }

    // ---------------------------------------------------- the FAB's own state

    /** Arms the pill's press bounce (the click site owns the pairing with release). */
    public void pressJumpLatest() {
        jumpLatestHeld = true;
    }

    /** Releases the pill's press bounce, so the spring plays its release half. */
    public void releaseJumpLatest() {
        jumpLatestHeld = false;
    }
}
