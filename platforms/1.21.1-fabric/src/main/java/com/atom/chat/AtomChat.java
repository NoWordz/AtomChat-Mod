package com.atom.chat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomChat implements ModInitializer {
    public static final String MOD_ID = "atomchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 第一件事：把平台门面装上（共享层要问加载器的事情都走它）。
        // 必须在任何配置/路径访问之前 —— 没装就抛错，不给默认值。
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.FabricPlatform());
        // Config seam for anti-spam: wire the pure logic to the real config
        // only in a real launch (unit tests keep the safe no-merge default).
        com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier =
                () -> com.atom.chat.config.AtomChatConfig.get().antiSpamEnabled;
        // Avatar companion: codecs on both logical sides, receivers on the
        // game-server side (dedicated server or an integrated double-open).
        com.atom.chat.net.AvatarPayloads.register();
        com.atom.chat.net.AvatarCompanionServer.register();
        // Poke companion: a remote poke, rate limited server-side.
        com.atom.chat.net.PokePayloads.register();
        com.atom.chat.net.PokeCompanionServer.register();
        // Media companion: server-hosted chat images / GIFs, same dual
        // entrypoint pattern and master hosting switch as the avatar side.
        com.atom.chat.net.MediaPayloads.register();
        com.atom.chat.net.MediaCompanionServer.register();
        // Server pack distribution (0.2.9): emotes, phrases and the server
        // identity, on its own channel so it can be switched off alone.
        com.atom.chat.net.PackPayloads.register();
        // Server pack distribution (0.2.9): the receivers, the send port and the
        // server facts the shared logic asks for. The tick that drains the queues
        // is hooked below, next to the other server-side housekeeping.
        com.atom.chat.net.PackNetServer.install();
        // Server config screen (0.2.9): /atomchat gui + the save path. The screen
        // itself is built by the editing player's client, never by the server.
        com.atom.chat.net.ConfigPayloads.register();
        com.atom.chat.net.ConfigScreenServer.register();
        // Retention and lifecycle housekeeping for the two hosted stores
        // (atomchat-data/media and /avatars). The start and tick hooks only run
        // for a server - a dedicated one, or the integrated one of a
        // single-player world - so a plain client connected to someone else's
        // server never sweeps its own game directory.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.atom.chat.net.CompanionMaintenance.tick();
            // The pack download queue: a few chunks per player per tick.
            com.atom.chat.net.PackSyncServer.tick(server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                com.atom.chat.net.CompanionMaintenance.onServerStarted());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                com.atom.chat.net.CompanionMaintenance.onPlayerLogout(handler.player.getUuid());
            }
        });
        LOGGER.info("AtomChat initialized");
    }

    /**
     * Friendly mod version for the settings about page. Read from the loader
     * metadata rather than a hardcoded constant so it can never drift away
     * from gradle.properties. Guarded because the container is absent outside
     * a real Fabric launch.
     */
    public static String version() {
        try {
            return FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * The jar this build was loaded from, asked of the loader rather than
     * derived from our own code source: Fabric runs mods off its own class
     * path, so a protection-domain lookup can name something other than the
     * file the player installed. The environment summary prints this next to
     * its content hash — a jar can be renamed to anything, a hash cannot (see
     * the "file says 0.2.8, metadata says 0.2.7" report that motivated it).
     * Null outside a real launch, and in a dev run, where the origin is a
     * directory rather than a jar.
     */
    public static java.nio.file.Path artifactPath() {
        try {
            return FabricLoader.getInstance().getModContainer(MOD_ID)
                    .flatMap(container -> container.getOrigin().getPaths().stream()
                            .filter(java.nio.file.Files::isRegularFile)
                            .findFirst())
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
