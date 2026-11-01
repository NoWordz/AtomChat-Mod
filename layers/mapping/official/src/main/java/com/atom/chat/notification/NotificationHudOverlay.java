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
        NotificationBanner.INSTANCE.tick();
        // Under Gnetum the HUD layer pipeline is re-routed into its own
        // multi-pass cache; keep this layer on its uncached path so the
        // raw-GL frosted backdrop and the Skia card both land in the main
        // framebuffer every frame (see GnetumHudCompat).
        GnetumHudCompat.keepOutOfHudCache();
        if (failed || !NotificationHudVisibility.visible() || !NotificationBanner.INSTANCE.hasActive()) return;
        Minecraft client = Minecraft.getInstance();
        try {
            gui.flush();
            float density = ScreenMetrics.uiDensity(client.getWindow());
            float width = client.getMainRenderTarget().width / density;
            float height = client.getMainRenderTarget().height / density;
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
                if (PanelBlurRenderer.isAvailable()) {
                    boolean drew = PanelBlurRenderer.render(new Matrix4f(), rect.x(), rect.y(),
                            rect.w(), rect.h(), UiTokens.cardRadius(), NotificationBanner.hudBackdropAlpha());
                    if (drew) NotificationBanner.hudBackdropPreDrawn();
                }
                ItemIconRenderer.prepare(gui, GRAPHICS);
            }, density,
                    (canvas, snapshot) -> NotificationBanner.INSTANCE.renderInHud(canvas, width, height));
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("notify", "HUD notification renderer disabled after failure; panel notifications remain available"), e);
        }
    }
}
