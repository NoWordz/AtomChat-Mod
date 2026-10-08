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
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only entrypoint. Nothing references this class except the dist-guarded
 * branch in {@link AtomChat}'s constructor, so a dedicated server never loads it.
 */
public final class AtomChatClient {
    private AtomChatClient() {
    }

    public static final KeyMapping OPEN_ATOMCHAT_KEY = new KeyMapping(
            "key.atomchat.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.atomchat.category");

    /** Called from the common mod constructor on the client only. */
    public static void init(IEventBus modEventBus) {
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

        MinecraftForge.EVENT_BUS.addListener(AtomChatClient::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(AtomChatClient::onPlayerDisconnect);
        MinecraftForge.EVENT_BUS.addListener(AtomChatClient::onClientTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        AtomChatConfig.get();
        CacheDirs.migrateFromOldConfigPaths();
        WallpaperStore.init(FMLPaths.CONFIGDIR.get().resolve("atomchat/wallpaper"));
        BannerStore.init(FMLPaths.CONFIGDIR.get().resolve("atomchat"));
        ImageLoader.get().init(CacheDirs.imageCacheDir());
        com.atom.chat.net.PackNetClient.install();
        com.atom.chat.net.AvatarCompanionClient.init();
        com.atom.chat.net.PokeCompanionClient.init();
        com.atom.chat.net.MediaCompanionClient.init();
        com.atom.chat.history.ChatHistory.init(FMLPaths.CONFIGDIR.get().resolve("atomchat/history"));
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
        com.atom.chat.net.PokeCompanionClient.setHandler(null);
        com.atom.chat.net.PackSyncClient.onDisconnect();
        com.atom.chat.net.ConfigScreenClient.onDisconnect();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
