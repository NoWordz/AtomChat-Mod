package com.atom.chat.notification;

import com.atom.chat.AtomChat;
import com.atom.chat.render.ItemIconRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.screen.ScreenMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Dedicated, non-interactive HUD surface; never borrows a screen's canvas. */
public final class NotificationHudOverlay {
    private static final SkiaGraphics GRAPHICS = new SkiaGraphics();
    private static boolean failed;
    private NotificationHudOverlay() { }
    public static void render(GuiGraphics gui) {
        NotificationBanner.INSTANCE.tick();
        if (failed || !NotificationHudVisibility.visible() || !NotificationBanner.INSTANCE.hasActive()) return;
        Minecraft client = Minecraft.getInstance();
        try {
            gui.flush();
            GRAPHICS.checkFrameBufferId();
            float density = ScreenMetrics.uiDensity(client.getWindow());
            float width = client.getMainRenderTarget().width / density;
            float height = client.getMainRenderTarget().height / density;
            GRAPHICS.draw(() -> ItemIconRenderer.prepare(gui, GRAPHICS), density,
                    (canvas, snapshot) -> NotificationBanner.INSTANCE.renderInHud(canvas, width, height));
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("notify", "HUD notification renderer disabled after failure; panel notifications remain available"), e);
        }
    }
}
