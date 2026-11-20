package com.atom.chat.notification;

import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.PanelCloseOverlay;
import com.atom.chat.AtomChat;
import com.atom.chat.fun.NewYearFireworks;
import com.atom.chat.render.ItemIconRenderer;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.screen.ScreenMetrics;
import com.atom.lib.design.atomchat.UiTokens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

/** Dedicated, non-interactive HUD surface; never borrows a screen's canvas. */
public final class NotificationHudOverlay {
    private static final SkiaGraphics GRAPHICS = new SkiaGraphics();
    private static boolean failed;
    private NotificationHudOverlay() { }
    public interface PanelChatHud {
        void atomchat$renderPanelHud(DrawContext gui, float visibility, long now);
    }

    /** Both the chat stack and shortcut sample the same panel-exit timeline. */
    public static float gameplayChatVisibility(MinecraftClient client, long now) {
        if (client.currentScreen != null) return 1F;
        var exit = PanelCloseOverlay.INSTANCE;
        if (exit.progress(now) <= 0F) return 1F;
        var window = client.getWindow();
        return exit.active(client.world, ScreenMetrics.uiDensity(window), ScreenMetrics.vw(window),
                ScreenMetrics.vh(window), now, Animations.enabled()) ? 1F - exit.progress(now) : 1F;
    }

    public static void render(DrawContext gui) {
        if (!com.atom.chat.config.AtomChatConfig.get().enabled) { com.atom.chat.ui.PanelCloseOverlay.INSTANCE.clear(); return; }
        NotificationBanner.INSTANCE.tick();
        // Under Gnetum the HUD layer pipeline is re-routed into its own
        // multi-pass cache; keep this layer on its uncached path so the
        // raw-GL frosted backdrop and the Skia card both land in the main
        // framebuffer every frame (see GnetumHudCompat). Gnetum 3.3.6 is
        // NeoForge-only, so this is a cheap no-op probe on Fabric — the twin
        // is kept mirrored in case a Fabric build appears.
        GnetumHudCompat.keepOutOfHudCache();
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        float hint = 0F;
        if (client.currentScreen instanceof net.minecraft.client.gui.screen.AtomChatScreen panel) {
            hint = panel.shortcutVisibility();
            // In 1.21.1 the vanilla HUD skips its chat layer while ChatScreen is focused.
            // This pre-Screen HUD pass also keeps Forge from drawing a second stack.
            if (client.inGameHud.getChatHud() instanceof PanelChatHud hud) {
                hud.atomchat$renderPanelHud(gui, hint, now);
            }
        } else if (client.currentScreen == null) {
            hint = gameplayChatVisibility(client, now);
        }
        if (failed) { PanelCloseOverlay.INSTANCE.clear(); return; }
        var exit = PanelCloseOverlay.INSTANCE;
        float density = ScreenMetrics.uiDensity(client.getWindow());
        float width = ScreenMetrics.vw(client.getWindow());
        float height = ScreenMetrics.vh(client.getWindow());
        if (client.currentScreen != null || client.world == null || client.player == null) exit.clear();
        boolean exiting = exit.active(client.world, density, width, height, now, Animations.enabled());
        boolean paintExit = exiting && !client.options.hudHidden;
        boolean banner = !exiting && NotificationHudVisibility.visible() && NotificationBanner.INSTANCE.hasActive();
        if (!com.atom.chat.config.AtomChatConfig.get().hudIconEnabled
                || client.world == null || client.player == null || client.options.hudHidden) hint = 0F;
        long fireworksTime = now / 1_000_000L;
        boolean fireworks = NewYearFireworks.INSTANCE.active(fireworksTime)
                && client.world != null && client.player != null && !client.options.hudHidden
                && !(client.currentScreen instanceof net.minecraft.client.gui.screen.AtomChatScreen);
        if (!banner && hint <= 0F && !paintExit && !fireworks) return;
        float hintVisibility = hint;
        long hudStart = com.atom.chat.diagnostics.FrameProfile.startHud();
        boolean exitGlass = paintExit && exit.hasCardMasks();
        float bannerBlur = com.atom.chat.config.AtomChatConfig.get().effectiveCardBlurStrength();
        try {
            gui.draw();
            try (var cardWorld = exitGlass && !com.atom.chat.wallpaper.WallpaperStore.isSet()
                    ? com.atom.chat.render.CardBackdrop.captureWorld(
                            com.atom.lib.client.render.MinecraftRenderBridge.get()::captureFrame) : null) {
                float guiScale=(float)client.getWindow().getScaleFactor();
                // The frosted backdrop is pre-drawn with raw GL (panel-blur style)
                // before the Skia pass: a Skia backdrop filter would read the
                // framebuffer as premul, and shader packs leave undefined per-frame
                // alpha there — under Iris that flickered, and every attempt to
                // normalize the framebuffer alpha blackened the display. The raw-GL
                // pass samples RGB only and owns its own output alpha, so it never
                // reads or writes the framebuffer alpha channel.
                var blurPlacement = banner && bannerBlur > 0F
                        ? NotificationBanner.hudPlacement(width, height).toGui(density, guiScale) : null;
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
                    if (blurPlacement != null && PanelBlurRenderer.isAvailable()) {
                        var rect = blurPlacement.rect();
                        PanelBlurRenderer.render(new Matrix4f(), rect.x(), rect.y(),
                                rect.w(), rect.h(), UiTokens.cardRadius(AtomChatAppearance.theme()) * blurPlacement.scale(),
                                NotificationBanner.hudBackdropAlpha(), bannerBlur);
                    }
                    if (banner) ItemIconRenderer.prepare(gui, GRAPHICS);
                }, density,
                        (canvas, snapshot) -> {
                            if (paintExit) {
                                var wallpaper = exitGlass ? com.atom.chat.wallpaper.WallpaperImage.current(
                                        com.atom.chat.wallpaper.WallpaperStore.current()) : null;
                                exit.draw(canvas, GRAPHICS.contextIdentity(), now, GRAPHICS.requireSurface(), cardWorld, wallpaper);
                            }
                            if (banner) NotificationBanner.INSTANCE.renderInHud(canvas, width, height);
                            com.atom.chat.ui.ChatShortcutHint.renderGuiScaled(canvas, width, height, density, guiScale,
                                    com.atom.chat.AtomChatClient.OPEN_ATOMCHAT_KEY.getBoundKeyLocalizedText().getString(), hintVisibility);
                            if (fireworks) NewYearFireworks.INSTANCE.draw(canvas, width, height, fireworksTime);
                        });
            }
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
