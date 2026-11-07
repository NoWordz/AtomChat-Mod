package com.atom.chat;

import com.atom.chat.banner.BannerStore;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.PrivateEchoTracker;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.notification.NotificationController;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.screen.AtomChatScreen;
import com.atom.chat.util.AwtDisplay;
import com.atom.chat.util.CacheDirs;
import com.atom.chat.wallpaper.WallpaperStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = AtomChat.MOD_ID, dist = Dist.CLIENT)
public class AtomChatClient {
    public static final KeyMapping OPEN_ATOMCHAT_KEY = new KeyMapping(
            "key.atomchat.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.atomchat.category");

    public AtomChatClient(ModContainer container, IEventBus modEventBus) {
        com.atom.chat.compat.AtomLibDependencyCheck.require(net.neoforged.fml.ModList.get()
                .getModContainerById("atomlib").map(mod -> mod.getModInfo().getVersion().toString()).orElse(null));
        // Idempotent second call: the client-only mixin plugin claimed AWT
        // before Minecraft's Main ran. This log line reports the outcome of
        // whichever call won, so it is the picker's field diagnostic.
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("startup", "AWT toolkit: {}"), AwtDisplay.claim());

        modEventBus.addListener(PanelBlurRenderer::registerShaders);
        modEventBus.addListener(AtomChatClient::onClientSetup);
        modEventBus.addListener(AtomChatClient::registerKeyMappings);
        modEventBus.addListener(AtomChatClient::registerReloadListener);
        modEventBus.addListener(AtomChatClient::registerNotificationOverlay);
        // Sound events must go through RegisterEvent: constructing the mod can
        // happen after the registries are already frozen (see
        // NotificationController#registerSound).
        modEventBus.addListener(NotificationController::registerSound);
        modEventBus.addListener(com.atom.chat.audio.FeedbackSoundController::registerSound);

        NeoForge.EVENT_BUS.addListener(AtomChatClient::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onPlayerDisconnect);
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onClientTick);
        // E1 外部尺子：与当前屏幕无关的每帧采样点（G0–G3 共用，先于 Screen.render）。
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onRenderGuiPost);
    }

    /** RenderGuiEvent.Post 每渲染帧恰好一次（GuiLayerManager.render 末尾）。 */
    private static void onRenderGuiPost(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        com.atom.chat.diagnostics.FramePump.onFrame();
    }


    /** Render-thread, once per process: create the Skija bridge/surface and blur textures. */
    private static void warmupGl(Minecraft client, boolean blurEnabled) {
        if (!com.atom.chat.render.SkiaSupport.available()) {
            return;
        }
        long started = System.nanoTime();
        try {
            com.atom.lib.client.render.MinecraftRenderBridge.get().prepare();
            if (blurEnabled
                    && !com.atom.chat.wallpaper.WallpaperStore.isSet()) {
                com.atom.chat.render.PanelBlurRenderer.prewarm();
            }
        } catch (Throwable t) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                    "AtomChat first-open warmup failed; the panel still opens"), t);
        } finally {
            com.atom.chat.diagnostics.FirstOpenBudget.setPrewarmGl(System.nanoTime() - started);
        }
    }

    /** Current backlog text + reaction emoji, for the emoji prewarm scanner. */
    private static java.util.List<String> firstOpenWarmupTexts() {
        java.util.List<String> messages = new java.util.ArrayList<>();
        java.util.List<String> reactions = new java.util.ArrayList<>();
        for (com.atom.chat.chat.ChatMessage message : com.atom.chat.chat.ChatStore.get().snapshot()) {
            messages.add(message.getContentText());
            for (com.atom.chat.reaction.ReactionWire.Entry entry
                    : com.atom.chat.reaction.ReactionClient.entries(message)) {
                reactions.add(entry.emoji());
            }
        }
        return com.atom.chat.render.FirstOpenWarmup.warmupTexts(messages, reactions);
    }

    private static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        AtomChatConfig.get();
        CacheDirs.migrateFromOldConfigPaths();
        CacheDirs.initializePersonalDirectories();
        WallpaperStore.init(CacheDirs.wallpaperDir());
        com.atom.chat.theme.AppearancePresetStore.init(CacheDirs.presetsDir());
        BannerStore.init(CacheDirs.bannerDir());
        ImageLoader.get().init(CacheDirs.imageCacheDir());
        com.atom.chat.net.PackNetClient.install();
        com.atom.chat.net.AvatarCompanionClient.init();
        com.atom.chat.net.PokeCompanionClient.init();
        com.atom.chat.net.ReactionCompanionClient.init();
        com.atom.chat.net.MediaCompanionClient.init();
        com.atom.chat.net.ProfileCompanionClient.init();
        com.atom.chat.history.ChatHistory.init(CacheDirs.historyDir());
        // Cold-start the bundled font decoder now; the first panel open must not
        // be the first time the game reads a 3.9 MB OTF.
        com.atom.chat.render.FirstOpenWarmup.prewarmFontsAsync();
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("startup", "AtomChat client initialized"));
    }

    private static void registerNotificationOverlay(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("atomchat", "notifications"),
                (graphics, ticks) -> com.atom.chat.notification.NotificationHudOverlay.render(graphics));
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ATOMCHAT_KEY);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            PanelBlurRenderer.resetShader();
            // Decimated copies were built from the old resource-supplied
            // bitmaps; drop them with the shader so nothing points at a
            // generation that no longer exists.
            com.atom.chat.render.ImageQuality.release();
            com.atom.chat.settings.SettingsSectionPage.clearTranslationCache();
        });
    }

    private static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        // Adopt the world key first so a saved history can be loaded before
        // join-time system lines start arriving.
        com.atom.chat.history.ChatHistory.onJoin(client);
        com.atom.chat.chat.DraftSessionStore.reset();
        NotificationBanner.INSTANCE.reset();
        com.atom.chat.page.ProfilePage.noteJoin();
        com.atom.chat.net.AvatarCompanionClient.onJoin();
        com.atom.chat.net.MediaCompanionClient.onJoin();
            com.atom.chat.net.ProfileCompanionClient.onJoin();
        com.atom.chat.net.PackSyncClient.onJoin();
        com.atom.chat.chat.OwnIdentity.reset();
        com.atom.chat.chat.TeleportCommands.reset();
        com.atom.chat.render.FirstOpenWarmup.requestGlWarmup();
    }

    private static void onPlayerDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft client = Minecraft.getInstance();
        // Save before the stores are cleared, then drop the world key.
        com.atom.chat.history.ChatHistory.onDisconnect(client);
        com.atom.chat.chat.DraftSessionStore.reset();
        NotificationBanner.INSTANCE.reset();
        PrivateChatStore.reset();
        PrivateEchoTracker.clear();
        com.atom.chat.chat.PublicEchoTracker.clear();
        ChatStore.reset();
        com.atom.chat.chat.SeenPlayers.clear();
        com.atom.chat.chat.OwnIdentity.reset();
        com.atom.chat.net.MediaCompanionClient.onDisconnect();
            com.atom.chat.net.ProfileCompanionClient.onLeave();
            com.atom.chat.reaction.ReactionClient.reset();
        com.atom.chat.net.PokeCompanionClient.setHandler(null);
        com.atom.chat.net.PackSyncClient.onDisconnect();
        com.atom.chat.net.ConfigScreenClient.onDisconnect();
        com.atom.chat.render.FirstOpenWarmup.onDisconnect();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        // Emoji rasterization must not happen on the render thread of the
        // first panel frame. The backlog only exists after join, so the
        // warmup follows the content instead of the join event: poll the
        // cheap generation counter and start a warmup when it moved.
        if (client.screen == null) {
            com.atom.chat.render.FirstOpenWarmup.pollEmojiPrewarm(
                    com.atom.chat.chat.ChatStore.generation(),
                    () -> firstOpenWarmupTexts());
        }
        if (com.atom.chat.render.FirstOpenWarmup.consumeGlWarmup()) {
            warmupGl(client, com.atom.chat.config.AtomChatConfig.get().blurEnabled);
        }
        // First tick only (self-guarded): identity always, the detailed block
        // when debug is on. It lives here rather than in client setup because
        // the GPU rows need a current GL context, which only the render thread
        // has — and the tick runs on it.
        com.atom.chat.diagnostics.EnvironmentSummary.logOnce(AtomChat.version(), AtomChat.artifactPath());
        com.atom.chat.history.ChatHistory.tick(client);
        com.atom.chat.net.MediaCompanionClient.tick();
        com.atom.chat.net.ProfileCompanionClient.tick();
        com.atom.chat.net.PackSyncClient.tick();
        // Expires banners regardless of whether the panel is open: their 4s
        // lifetime starts at enqueue, so a banner queued while the panel was
        // closed is already gone if you open the panel later than that.
        NotificationBanner.INSTANCE.tick();
        while (OPEN_ATOMCHAT_KEY.consumeClick()) {
            // 原生库不在就别开：面板在那些平台上开出来是必崩的（Skija 的失败落在原生层，
            // try/catch 拦不住）。键位照旧注册，按下去只是什么都不发生 —— 原因在启动时
            // 由 SkiaSupport 写进日志了。
            if (client.screen == null && com.atom.chat.render.SkiaSupport.available()) {
                if (!com.atom.chat.config.AtomChatConfig.get().enabled) {
                    com.atom.chat.config.AtomChatConfig.get().enabled = true;
                    com.atom.chat.config.AtomChatConfig.save(com.atom.chat.config.AtomChatConfig.get());
                }
                client.setScreen(new AtomChatScreen("", AtomChatScreen.AtomChatOpenMode.RESTORE));
            }
        }
    }
}
