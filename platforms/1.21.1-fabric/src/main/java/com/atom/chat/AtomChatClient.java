package com.atom.chat;

import com.atom.chat.banner.BannerStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.notification.NotificationController;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.util.AwtDisplay;
import com.atom.chat.util.CacheDirs;
import com.atom.chat.wallpaper.WallpaperStore;
import net.fabricmc.api.ClientModInitializer;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.PrivateEchoTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.screen.AtomChatScreen;
import net.minecraft.client.gui.screen.AtomChatScreen.AtomChatOpenMode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AtomChatClient implements ClientModInitializer {
    public static final KeyBinding OPEN_ATOMCHAT_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.atomchat.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y,
                    "key.atomchat.category"));

    @Override
    public void onInitializeClient() {
        // Idempotent second call: the client-only mixin plugin claimed AWT
        // before Minecraft's Main ran. This log line reports the outcome of
        // whichever call won, so it is the picker's field diagnostic.
        AtomChat.LOGGER.info("AWT toolkit: {}", AwtDisplay.claim());
        AtomChatConfig.get();
        CacheDirs.migrateFromOldConfigPaths();
        WallpaperStore.init(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                        .resolve("atomchat/wallpaper"));
        BannerStore.init(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                        .resolve("atomchat"));
        ImageLoader.get().init(CacheDirs.imageCacheDir());
        com.atom.chat.net.AvatarCompanionClient.init();
        com.atom.chat.net.MediaCompanionClient.init();
        com.atom.chat.net.PackNetClient.install();
        com.atom.chat.net.ConfigScreenClient.init();
        com.atom.chat.history.ChatHistory.init(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                        .resolve("atomchat/history"));
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(AtomChat.MOD_ID, "panel_blur_shaders");
            }

            @Override
            public void reload(ResourceManager manager) {
                PanelBlurRenderer.resetShader();
            }
        });
        AtomChat.LOGGER.info("AtomChat client initialized");

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Adopt the world key first so a saved history can be loaded before
            // join-time system lines start arriving.
            com.atom.chat.history.ChatHistory.onJoin(client);
            com.atom.chat.page.ProfilePage.noteJoin();
            com.atom.chat.net.AvatarCompanionClient.onJoin();
            com.atom.chat.net.MediaCompanionClient.onJoin();
            com.atom.chat.net.PackSyncClient.onJoin();
            com.atom.chat.chat.OwnIdentity.reset();
            com.atom.chat.chat.TeleportCommands.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
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
        });
        NotificationController.registerSound();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // First tick only (self-guarded): identity always, the detailed
            // block when debug is on. It lives here rather than in client init
            // because the GPU rows need a current GL context, which only the
            // render thread has — and the tick runs on it.
            com.atom.chat.diagnostics.EnvironmentSummary.logOnce(AtomChat.version(), AtomChat.artifactPath());
            com.atom.chat.history.ChatHistory.tick(client);
            com.atom.chat.net.MediaCompanionClient.tick();
        com.atom.chat.net.PackSyncClient.tick();
            // Expires banners regardless of whether the panel is open: their 4s
            // lifetime starts at enqueue, so a banner queued while the panel was
            // closed is already gone if you open the panel later than that.
            NotificationBanner.INSTANCE.tick();
            while (OPEN_ATOMCHAT_KEY.wasPressed()) {
                // 原生库不在就别开：面板在那些平台上开出来是必崩的（Skija 的失败落在原生层，
                // try/catch 拦不住）。键位照旧注册，按下去只是什么都不发生 —— 原因在启动时
                // 由 SkiaSupport 写进日志了。
                if (client.currentScreen == null && com.atom.chat.render.SkiaSupport.available()) {
                    client.setScreen(new AtomChatScreen("", AtomChatOpenMode.RESTORE));
                }
            }
        });
    }
}
