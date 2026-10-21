package com.atom.chat.screen;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.nav.AppPage;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import com.mojang.blaze3d.platform.Window;

/**
 * The screen's geometry: the panel frame, the virtual-coordinate conversion and
 * the three list layouts (D10). One source of truth for drawing and hit testing
 * alike, so a rect a click is tested against can never disagree with the rect
 * that was painted.
 *
 * <p>The public methods take the window they measure; the arithmetic behind
 * them takes the measurement itself and is package-private. That split exists
 * because these numbers are the contract: the offline test pins them for fixed
 * window sizes (small window, 1080p, ultrawide, 4K) and at the clamp edges,
 * which is only possible if the arithmetic can be handed a window size instead
 * of asking Minecraft for one.
 *
 * <p>Three facts stay with the screen and never enter here: the composer's
 * animated extra height (D21) and whether a reply bar is reserved (D1) are
 * parameters of the one layout that needs them — {@code layout()}, which the
 * shell assembles — and the top page (D24) is passed in by the caller that
 * knows which list it wants. Nothing in this class reads another domain.
 *
 * <p>Every literal below is load-bearing: the content-zoom clamp [0.8, 1.5],
 * the viewport slack of 32, the 1080p-anchored density base and its 0.5 floor
 * all move the panel, every hit rect and every blur capture rect if they
 * change. They are moved verbatim from the screen and verified by the test.
 */
public final class ScreenMetrics {
    private ScreenMetrics() {
    }

    // ------------------------------------------------------- the panel frame

    public static float panelWidth(Window window) {
        return panelWidth(window.getWidth(), window.getHeight());
    }

    public static float panelHeight(Window window) {
        return panelHeight(window.getHeight());
    }

    /**
     * Content-only zoom axis, clamped to the config's slider span [0.8, 1.5].
     * Multiplies the density (so every inner token's pixel size) while
     * panelWidth/panelHeight divide their virtual size by the same factor:
     * the two cancel exactly on the panel frame, so the box's pixel size
     * tracks uiScale only and the content inside zooms.
     */
    public static float contentScale() {
        return contentScaleValue();
    }

    /**
     * Design density: the 1080p-anchored base times the AtomChat UI scale
     * times the content zoom. Every virtual-coordinate conversion in this
     * screen funnels through here, so scaling this one value scales the whole
     * virtual space — fonts, icons, hit rects and the blur pre-pass included
     * — without touching UiTokens. The panel frame opts back out via the
     * division in panelWidth/panelHeight/panelX.
     */
    public static float uiDensity(Window window) {
        return uiDensity(window.getHeight());
    }

    public static float vw(Window window) {
        return vw(window.getWidth(), window.getHeight());
    }

    public static float vh(Window window) {
        return vh(window.getHeight());
    }

    /**
     * The anchor is part of the panel frame, whose pixel size is
     * contentScale-invariant, so the physical left margin must be too: the
     * virtual constant divides back out against the density's content factor.
     */
    public static float panelX() {
        return panelXValue();
    }

    public static float panelY(Window window) {
        return panelY(window.getHeight());
    }

    /** GUI-space pointer x → panel space. The event domains' one coordinate entry. */
    public static float toVirtualX(Window window, double guiX) {
        return toVirtualX(guiX, window.getGuiScale(), window.getHeight());
    }

    /** GUI-space pointer y → panel space. */
    public static float toVirtualY(Window window, double guiY) {
        return toVirtualY(guiY, window.getGuiScale(), window.getHeight());
    }

    // ---------------------------------------------------------- the layouts

    /** A root tab page: tab bar, no composer. */
    public static UiLayout rootLayout(Window window) {
        return UiLayout.ofRoot(panelX(), panelY(window), panelWidth(window), panelHeight(window), AtomChatConfig.get().halfScreenMode);
    }

    /** A pushed settings sub-page: no composer, no tab bar, tallest possible list. */
    public static UiLayout detailLayout(Window window) {
        return UiLayout.ofDetail(panelX(), panelY(window), panelWidth(window), panelHeight(window), AtomChatConfig.get().halfScreenMode);
    }

    /** Layout of whatever list the top page is showing (D24 supplies the page). */
    public static UiLayout listLayout(Window window, AppPage topPage) {
        return topPage == AppPage.SETTINGS_SECTION || topPage == AppPage.PROFILE_DETAIL
                ? detailLayout(window) : rootLayout(window);
    }

    // ------------------------------------------------- the arithmetic itself
    //
    // Package-private on purpose: the offline test drives these with fixed
    // window measurements, and nothing else needs them.

    static float panelWidth(int width, int height) {
        return Math.min(UiTokens.s(AtomChatConfig.get().activePanelWidth()) / contentScaleValue(), vw(width, height) - 32.0F);
    }

    static float panelHeight(int height) {
        if (AtomChatConfig.get().halfScreenMode) return vh(height);
        return Math.min(UiTokens.s(AtomChatConfig.get().panelHeight) / contentScaleValue(), vh(height) - 32.0F);
    }

    static float contentScaleValue() {
        return Math.max(0.8F, Math.min(AtomChatConfig.get().contentScale, 1.5F));
    }

    static float uiDensity(int height) {
        float base = Math.max(1.0F, height / 1080.0F);
        return base * Math.max(0.5F, AtomChatConfig.get().uiScale) * contentScaleValue();
    }

    static float vw(int width, int height) {
        return width / uiDensity(height);
    }

    static float vh(int height) {
        return height / uiDensity(height);
    }

    static float panelXValue() {
        if (AtomChatConfig.get().halfScreenMode) return 0F;
        return UiTokens.PANEL_ANCHOR_X / contentScaleValue();
    }

    static float panelY(int height) {
        if (AtomChatConfig.get().halfScreenMode) return 0F;
        return (vh(height) - panelHeight(height)) / 2.0F;
    }

    static float toVirtualX(double guiX, double guiScale, int height) {
        return (float) (guiX * guiScale / uiDensity(height));
    }

    static float toVirtualY(double guiY, double guiScale, int height) {
        return (float) (guiY * guiScale / uiDensity(height));
    }
}
