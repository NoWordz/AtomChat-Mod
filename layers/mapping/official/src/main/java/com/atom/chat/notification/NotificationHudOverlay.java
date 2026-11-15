package com.atom.chat.notification;

import com.atom.chat.AtomChat;
import com.atom.chat.render.ItemIconRenderer;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.screen.ScreenMetrics;
import com.atom.lib.design.atomchat.UiTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/** Dedicated, non-interactive HUD surface; never borrows a screen's canvas. */
public final class NotificationHudOverlay {
    private static final SkiaGraphics GRAPHICS = new SkiaGraphics();
    private static boolean failed;
    private NotificationHudOverlay() { }
    public static void render(GuiGraphics gui) {
        if (!com.atom.chat.config.AtomChatConfig.get().enabled) { com.atom.chat.ui.PanelCloseOverlay.INSTANCE.clear(); return; }
        NotificationBanner.INSTANCE.tick();
        // Under Gnetum the HUD layer pipeline is re-routed into its own
        // multi-pass cache; keep this layer on its uncached path so the
        // raw-GL frosted backdrop and the Skia card both land in the main
        // framebuffer every frame (see GnetumHudCompat).
        GnetumHudCompat.keepOutOfHudCache();
        if (failed) { com.atom.chat.ui.PanelCloseOverlay.INSTANCE.clear(); return; }
        Minecraft client = Minecraft.getInstance();
        var exit = com.atom.chat.ui.PanelCloseOverlay.INSTANCE;
        long now = System.nanoTime();
        float density = ScreenMetrics.uiDensity(client.getWindow());
        float width = ScreenMetrics.vw(client.getWindow());
        float height = ScreenMetrics.vh(client.getWindow());
        if (client.screen != null || client.level == null || client.player == null) exit.clear();
        boolean exiting = exit.active(client.level, density, width, height, now, com.atom.chat.ui.Animations.enabled());
        boolean paintExit = exiting && !client.options.hideGui;
        boolean banner = !exiting && NotificationHudVisibility.visible() && NotificationBanner.INSTANCE.hasActive();
        float hint = client.screen == null ? 1F - exit.progress(now)
                : client.screen instanceof com.atom.chat.screen.AtomChatScreen panel ? panel.shortcutVisibility() : 0F;
        if (client.level == null || client.player == null || client.options.hideGui) hint = 0F;
        if (!banner && hint <= 0F && !paintExit) return;
        float hintVisibility = hint;
        long hudStart = com.atom.chat.diagnostics.FrameProfile.startHud();
        try {
            gui.flush();
            float guiScale=(float)client.getWindow().getGuiScale();
            // The frosted backdrop is pre-drawn with raw GL (panel-blur style)
            // before the Skia pass: a Skia backdrop filter would read the
            // framebuffer as premul, and shader packs leave undefined per-frame
            // alpha there — under Iris that flickered, and every attempt to
            // normalize the framebuffer alpha blackened the display. The raw-GL
            // pass samples RGB only and owns its own output alpha, so it never
            // reads or writes the framebuffer alpha channel.
            var rect = NotificationBanner.hudPlacement(width, height).rect();
            GRAPHICS.checkFrameBufferId();
            GRAPHICS.draw(() -> {
                if (paintExit && exit.blur() && PanelBlurRenderer.isAvailable()) {
                    var bounds = exit.bounds();
                    var capture = com.atom.chat.ui.PanelOpenMotion.blurCapture(exit.progress(now), exit.style(),
                            bounds.x(), bounds.y(), bounds.w(), bounds.h(),
                            com.atom.chat.ui.PanelBackground.outlineInset(), density, guiScale);
                    PanelBlurRenderer.render(new Matrix4f(), capture.x(), capture.y(), capture.w(), capture.h(),
                            capture.radius(), exit.progress(now), com.atom.chat.config.AtomChatConfig.get().effectiveBlurStrength());
                }
                if (banner && PanelBlurRenderer.isAvailable()) {
                    boolean drew = PanelBlurRenderer.render(new Matrix4f(), rect.x(), rect.y(),
                            rect.w(), rect.h(), UiTokens.cardRadius(), NotificationBanner.hudBackdropAlpha());
                    if (drew) NotificationBanner.hudBackdropPreDrawn();
                }
                if (banner) ItemIconRenderer.prepare(gui, GRAPHICS);
            }, density,
                    (canvas, snapshot) -> {
                        if (paintExit) exit.draw(canvas, GRAPHICS.contextIdentity(), now);
                        if (banner) NotificationBanner.INSTANCE.renderInHud(canvas, width, height);
                        com.atom.chat.ui.ChatShortcutHint.renderGuiScaled(canvas, width, height, density, guiScale,
                                com.atom.chat.AtomChatClient.OPEN_ATOMCHAT_KEY.getTranslatedKeyMessage().getString(), hintVisibility);
                    });
        } catch (RuntimeException | LinkageError e) {
            com.atom.chat.ui.PanelCloseOverlay.INSTANCE.clear();
            failed = true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("notify", "HUD notification renderer disabled after failure; panel notifications remain available"), e);
        } finally {
            if (com.atom.chat.diagnostics.FrameProfile.hudFrame(hudStart))
                AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message(
                        "render", com.atom.chat.diagnostics.FrameProfile.hudSummary()));
        }
    }
}
