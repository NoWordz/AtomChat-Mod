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
        // Idempotent second call: the client-only mixin plugin claimed AWT
        // before Minecraft's Main ran. This log line reports the outcome of
        // whichever call won, so it is the picker's field diagnostic.
        AtomChat.LOGGER.info("AWT toolkit: {}", AwtDisplay.claim());

        modEventBus.addListener(PanelBlurRenderer::registerShaders);
        modEventBus.addListener(AtomChatClient::onClientSetup);
        modEventBus.addListener(AtomChatClient::registerKeyMappings);
        modEventBus.addListener(AtomChatClient::registerReloadListener);
        // Sound events must go through RegisterEvent: constructing the mod can
        // happen after the registries are already frozen (see
        // NotificationController#registerSound).
        modEventBus.addListener(NotificationController::registerSound);

        NeoForge.EVENT_BUS.addListener(AtomChatClient::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onPlayerDisconnect);
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onClientTick);
    }


    private static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        AtomChatConfig.get();
        CacheDirs.migrateFromOldConfigPaths();
        WallpaperStore.init(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("atomchat/wallpaper"));
        BannerStore.init(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("atomchat"));
        ImageLoader.get().init(CacheDirs.imageCacheDir());
        com.atom.chat.net.PackNetClient.install();
        com.atom.chat.net.AvatarCompanionClient.init();
        com.atom.chat.net.MediaCompanionClient.init();
        com.atom.chat.history.ChatHistory.init(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("atomchat/history"));
        AtomChat.LOGGER.info("AtomChat client initialized");
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ATOMCHAT_KEY);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> PanelBlurRenderer.resetShader());
    }

    private static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        // Adopt the world key first so a saved history can be loaded before
        // join-time system lines start arriving.
        com.atom.chat.history.ChatHistory.onJoin(client);
        com.atom.chat.page.ProfilePage.noteJoin();
        com.atom.chat.net.AvatarCompanionClient.onJoin();
        com.atom.chat.net.MediaCompanionClient.onJoin();
        com.atom.chat.net.PackSyncClient.onJoin();
        com.atom.chat.chat.OwnIdentity.reset();
        com.atom.chat.chat.TeleportCommands.reset();
    }

    private static void onPlayerDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft client = Minecraft.getInstance();
        // Save before the stores are cleared, then drop the world key.
        com.atom.chat.history.ChatHistory.onDisconnect(client);
        PrivateChatStore.reset();
        PrivateEchoTracker.clear();
        com.atom.chat.chat.PublicEchoTracker.clear();
        ChatStore.reset();
        com.atom.chat.chat.SeenPlayers.clear();
        com.atom.chat.chat.OwnIdentity.reset();
        com.atom.chat.net.MediaCompanionClient.onDisconnect();
        com.atom.chat.net.PackSyncClient.onDisconnect();
        com.atom.chat.net.ConfigScreenClient.onDisconnect();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        // First tick only (self-guarded): identity always, the detailed block
        // when debug is on. It lives here rather than in client setup because
        // the GPU rows need a current GL context, which only the render thread
        // has — and the tick runs on it.
        com.atom.chat.diagnostics.EnvironmentSummary.logOnce(AtomChat.version(), AtomChat.artifactPath());
        com.atom.chat.history.ChatHistory.tick(client);
        com.atom.chat.net.MediaCompanionClient.tick();
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
                client.setScreen(new AtomChatScreen("", AtomChatScreen.AtomChatOpenMode.RESTORE));
            }
        }
    }
}
