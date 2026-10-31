package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.chat.render.Easing;

/**
 * Pure Java state machine for a scrollable list that matches AtomChat's
 * existing chat-scroll feel: wheel movement glides toward a target, the view
 * sticks to the bottom while new content arrives, and the scrollbar has its
 * own hover/fade/emphasis animation state.
 *
 * <p>This class deliberately contains no Minecraft or Skija types. UiLayout is
 * allowed because it is pure layout math used for hit-testing and drawing
 * geometry.
 *
 * <p>Controllers are bottom-anchored by default, which is what chat logs want:
 * the first frame snaps to the newest message. Settings and profile lists pass
 * {@code false} to {@link #ScrollController(boolean)} so they open at the top.
 */
public final class ScrollController {
    private static final float BOTTOM_TOLERANCE = 3.0F;
    private static final float WHEEL_STEP = 45.0F;

    /**
     * Anchor policy for {@link #reset()}: chat logs re-arm the first-frame
     * bottom snap so the newest message is visible immediately, while settings
     * and profile lists stay at the top. {@link #scrollToBottom(boolean)} still
     * overrides the anchor for explicit jumps.
     */
    private final boolean bottomAnchor;

    private float scrollY;
    private float maxScroll;
    private boolean scrollToBottom;
    private float scrollTarget;
    private boolean scrollAnimActive;
    private float scrollAnimFrom;
    private float scrollAnimTo;
    private long scrollAnimStart;
    private long scrollAnimMs = UiMotion.SCROLL_SNAP_MS;

    // Scrollbar fade/hover/active state.
    private float scrollBarAlpha;
    private float scrollEmphasis;
    private float scrollActive;
    private boolean draggingScrollbar;
    private float dragStartY;
    private float dragStartScroll;
    private long lastScrollbarFrame;

    // Content/viewport change detection. A fresh chat controller snaps to the
    // bottom on its first update; top-anchored controllers skip the snap.
    private boolean firstFrameBottomSnap;
    private float lastViewportHeight = -1.0F;

    /**
     * Mirrors {@link Animations#enabled()}. Kept as a field rather than read
     * straight from the config so this class stays pure and testable offline.
     * Only snapping honours it — the wheel keeps gliding (see
     * {@link #startScrollAnim(float, long, boolean)}).
     */
    private boolean decorativeMotion = true;

    /** Creates a chat-style controller that opens at the newest message. */
    public ScrollController() {
        this(true);
    }

    /**
     * @param bottomAnchor {@code true} for chat logs that must start at the
     *                     bottom, {@code false} for settings/profile lists
     *                     that must start at the top
     */
    public ScrollController(boolean bottomAnchor) {
        this.bottomAnchor = bottomAnchor;
        this.scrollToBottom = bottomAnchor;
        this.firstFrameBottomSnap = bottomAnchor;
    }

    public void setDecorativeMotion(boolean enabled) {
        this.decorativeMotion = enabled;
    }

    /**
     * Clears every piece of scroll state, including drag and scrollbar fade.
     * The controller's anchor decides whether the next first frame snaps to the
     * bottom (chat) or stays at the top (settings/profile).
     */
    public void reset() {
        scrollY = 0.0F;
        maxScroll = 0.0F;
        scrollToBottom = bottomAnchor;
        scrollTarget = 0.0F;
        scrollAnimActive = false;
        scrollAnimFrom = 0.0F;
        scrollAnimTo = 0.0F;
        scrollAnimStart = 0L;
        scrollAnimMs = UiMotion.SCROLL_SNAP_MS;
        scrollBarAlpha = 0.0F;
        scrollEmphasis = 0.0F;
        scrollActive = 0.0F;
        draggingScrollbar = false;
        dragStartY = 0.0F;
        dragStartScroll = 0.0F;
        lastScrollbarFrame = 0L;
        firstFrameBottomSnap = bottomAnchor;
        lastViewportHeight = -1.0F;
    }

    /**
     * Records whether the viewport height changed since the previous update.
     * The world-chat input bar animates, so this lets the caller distinguish a
     * viewport-driven bottom pin from a normal content-driven follow.
     */
    public boolean viewportChanged(float viewportHeight) {
        boolean changed = lastViewportHeight >= 0.0F
                && Math.abs(lastViewportHeight - viewportHeight) > 0.01F;
        lastViewportHeight = viewportHeight;
        return changed;
    }

    /** Recomputes maxScroll from total content height and clamps current state. */
    public void setContent(float contentHeight, float viewportHeight) {
        maxScroll = Math.max(0.0F, contentHeight - viewportHeight);
        scrollY = Math.max(0.0F, Math.min(scrollY, maxScroll));
        scrollTarget = Math.max(0.0F, Math.min(scrollTarget, maxScroll));
    }

    public float getScrollY() {
        return scrollY;
    }

    public float getMaxScroll() {
        return maxScroll;
    }

    public float getTarget() {
        return scrollTarget;
    }

    /** Whether the view is currently pinned to / following the bottom. */
    public boolean isAtBottom() {
        return scrollToBottom || scrollTarget >= maxScroll - BOTTOM_TOLERANCE;
    }

    /** Whether explicit bottom-follow is armed (used before content changes). */
    public boolean isBottomStick() {
        return scrollToBottom;
    }

    /** Arms bottom-follow; the next {@link #updateAnimation(long)} will glide. */
    public void stickToBottom() {
        scrollToBottom = true;
    }

    /** Releases explicit bottom-follow without changing the current offset. */
    public void releaseBottomStick() {
        scrollToBottom = false;
    }

    /**
     * Scrolls to the bottom of the current content. With {@code animate} false
     * this pins instantly; with true it starts the standard snap animation and
     * leaves the view armed to follow new content.
     */
    public void scrollToBottom(boolean animate) {
        if (maxScroll <= 0.0F) {
            scrollY = 0.0F;
            scrollTarget = 0.0F;
            scrollAnimActive = false;
            return;
        }
        if (animate) {
            scrollToBottom = true;
            scrollTarget = maxScroll;
            startScrollAnim(scrollTarget, UiMotion.SCROLL_SNAP_MS, true);
        } else {
            scrollY = maxScroll;
            scrollTarget = maxScroll;
            scrollToBottom = false;
            scrollAnimActive = false;
        }
    }

    /** Applies a mouse-wheel step; positive values scroll up in Minecraft terms. */
    public void wheel(float amount) {
        scrollToBottom = false;
        scrollTarget = Math.max(0.0F, Math.min(scrollTarget - amount * WHEEL_STEP, maxScroll));
        // The wheel is pointer feedback, not ornament: it keeps gliding even
        // when decorative motion is off, otherwise scrolling feels broken.
        startScrollAnim(scrollTarget, UiMotion.SCROLL_WHEEL_MS, false);
    }

    public boolean isDragging() {
        return draggingScrollbar;
    }

    public void beginDrag(float y) {
        draggingScrollbar = true;
        dragStartY = y;
        dragStartScroll = scrollY;
    }

    /**
     * Maps a pointer position on the scrollbar track to a scroll offset using
     * the same thumb/track formula as the renderer.
     */
    public void dragTo(float y, float viewportHeight) {
        if (!draggingScrollbar) {
            return;
        }
        float visibleRatio = Math.min(1.0F, viewportHeight / (viewportHeight + maxScroll));
        float thumbH = Math.max(UiTokens.s(30), viewportHeight * visibleRatio);
        float travel = viewportHeight - thumbH;
        float delta = (y - dragStartY) * (travel > 0.0F ? maxScroll / travel : 0.0F);
        scrollY = Math.max(0.0F, Math.min(dragStartScroll + delta, maxScroll));
        scrollTarget = scrollY;
        scrollToBottom = false;
        scrollAnimActive = false;
    }

    public void endDrag() {
        draggingScrollbar = false;
    }

    /**
     * Advances the scroll offset toward its target, including the first-frame
     * bottom snap and the bottom-follow retarget used by new message arrivals.
     */
    public void updateAnimation(long nowMs) {
        if (firstFrameBottomSnap) {
            scrollY = maxScroll;
            scrollTarget = maxScroll;
            scrollToBottom = false;
            firstFrameBottomSnap = false;
            return;
        }
        boolean wasAtBottom = scrollTarget >= maxScroll - BOTTOM_TOLERANCE;
        if (scrollToBottom || wasAtBottom) {
            scrollTarget = maxScroll;
            scrollToBottom = false;
            startScrollAnim(scrollTarget, UiMotion.SCROLL_SNAP_MS, true);
        }
        if (scrollAnimActive) {
            float t = Math.min(1.0F, (nowMs - scrollAnimStart) / (float) scrollAnimMs);
            scrollY = scrollAnimFrom + (scrollAnimTo - scrollAnimFrom) * Easing.easeOutCubic(t);
            if (t >= 1.0F) {
                scrollY = scrollAnimTo;
                scrollAnimActive = false;
            }
        }
    }

    /**
     * Updates scrollbar fade/emphasis state. Returns the current alpha so the
     * renderer can skip drawing when the bar is fully hidden.
     *
     * @param nowMs current frame time
     * @param vmx virtual mouse x
     * @param vmy virtual mouse y
     * @param list the list rect the scrollbar is attached to
     * @param dragging whether the scrollbar is being dragged
     * @param trackWidth rendered track width
     * @param frameDtMs normal per-frame delta used for hover emphasis
     */
    public float updateScrollbarFade(long nowMs, float vmx, float vmy, UiLayout.Rect list,
                                     boolean dragging, float trackWidth, float frameDtMs) {
        long dt = Math.min(50L, nowMs - lastScrollbarFrame);
        lastScrollbarFrame = nowMs;

        float trackX = list.right() - trackWidth - UiTokens.s(2);
        boolean nearTrack = vmx >= trackX - UiTokens.s(12) && vmx <= trackX + trackWidth + UiTokens.s(12)
                && vmy >= list.y() - UiTokens.s(12) && vmy <= list.bottom() + UiTokens.s(12);
        boolean active = maxScroll > 0.0F && (dragging || nearTrack);
        scrollBarAlpha = UiMotion.approach(scrollBarAlpha, active ? 1.0F : 0.0F, dt,
                decorativeMotion ? UiMotion.SCROLLBAR_FADE_MS : 0L);
        if (scrollBarAlpha <= 0.0F) {
            return 0.0F;
        }

        boolean hover = !dragging
                && vmx >= trackX - UiTokens.s(8) && vmx <= trackX + trackWidth + UiTokens.s(8)
                && vmy >= list.y() && vmy <= list.bottom();
        scrollEmphasis = UiMotion.approach(scrollEmphasis, (hover || dragging) ? 1.0F : 0.0F,
                frameDtMs, UiMotion.SCROLLBAR_EMPHASIS_MS);
        scrollActive = UiMotion.approach(scrollActive, dragging ? 1.0F : 0.0F,
                frameDtMs, UiMotion.SCROLLBAR_EMPHASIS_MS);
        return scrollBarAlpha;
    }

    public float getScrollBarAlpha() {
        return scrollBarAlpha;
    }

    public float getScrollEmphasis() {
        return scrollEmphasis;
    }

    public float getScrollActive() {
        return scrollActive;
    }

    /**
     * @param decorative true for snap/follow (collapsed when decorative motion
     *                   is off), false for wheel glide (always animated).
     */
    private void startScrollAnim(float to, long durationMs, boolean decorative) {
        long ms = (decorative && !decorativeMotion) ? 0L : durationMs;
        scrollTarget = to;
        if (ms <= 0L || Math.abs(scrollY - to) <= 0.5F) {
            scrollY = to;
            scrollAnimActive = false;
            return;
        }
        if (scrollAnimActive && scrollAnimTo == to) {
            return;
        }
        scrollAnimFrom = scrollY;
        scrollAnimTo = to;
        scrollAnimStart = System.currentTimeMillis();
        scrollAnimMs = ms;
        scrollAnimActive = true;
    }

    /**
     * Jumps to an absolute offset — used by "jump to the message behind a
     * notification banner". Bottom-follow is released first, otherwise
     * {@link #updateAnimation} would retarget straight back to the bottom on the
     * very next frame.
     */
    public void scrollTo(float y, boolean animate) {
        scrollToBottom = false;
        // A jump is authoritative: kill the fresh-controller bottom snap so the
        // next updateAnimation cannot retarget to maxScroll behind our back.
        firstFrameBottomSnap = false;
        startScrollAnim(Math.max(0.0F, Math.min(y, maxScroll)),
                animate ? UiMotion.SCROLL_SNAP_MS : 0L, true);
    }
}
