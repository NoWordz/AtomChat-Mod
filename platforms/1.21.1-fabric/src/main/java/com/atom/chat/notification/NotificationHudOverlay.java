package com.atom.chat.notification;

import com.atom.chat.AtomChat;
import com.atom.chat.render.ItemIconRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.screen.ScreenMetrics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Dedicated, non-interactive HUD surface; never borrows a screen's canvas. */
public final class NotificationHudOverlay {
    private static final SkiaGraphics GRAPHICS = new SkiaGraphics();
    private static boolean failed;
    private NotificationHudOverlay() { }
    public static void render(DrawContext gui) {
        NotificationBanner.INSTANCE.tick();
        if (failed || !NotificationHudVisibility.visible() || !NotificationBanner.INSTANCE.hasActive()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            gui.draw();
            GRAPHICS.checkFrameBufferId();
            float density = ScreenMetrics.uiDensity(client.getWindow());
            float width = client.getFramebuffer().textureWidth / density;
            float height = client.getFramebuffer().textureHeight / density;
            GRAPHICS.draw(() -> ItemIconRenderer.prepare(gui, GRAPHICS), density,
                    (canvas, snapshot) -> NotificationBanner.INSTANCE.renderInHud(canvas, width, height));
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("notify", "HUD notification renderer disabled after failure; panel notifications remain available"), e);
        }
    }
}
