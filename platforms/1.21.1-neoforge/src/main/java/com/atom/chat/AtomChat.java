package com.atom.chat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AtomChat.MOD_ID)
public class AtomChat {
    public static final String MOD_ID = "atomchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Captured from the NeoForge mod container at construction time. */
    private static volatile String version = "unknown";

    public AtomChat(IEventBus modEventBus, ModContainer modContainer) {
        // 第一件事：把平台门面装上（共享层要问加载器的事情都走它）。
        // 必须在任何配置/路径访问之前 —— 没装就抛错，不给默认值。
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.NeoForgePlatform());
        // The pack seams go up in the same breath: the shared sender and the
        // server facts it asks for. Both sides of the network seam live here and
        // in AtomChatClient; a missing install throws at the first packet.
        com.atom.chat.net.PackNetServer.install();
        // Config seam for anti-spam: wire the pure logic to the real config
        // only in a real launch (unit tests keep the safe no-merge default).
        com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier =
                () -> com.atom.chat.config.AtomChatConfig.get().antiSpamEnabled;
        // Payload codecs and avatar-companion receivers are registered on the
        // NeoForge payload bus; this event fires for both logical sides.
        modEventBus.addListener(AtomChat::onRegisterPayloads);
        // Retention and lifecycle housekeeping for the two hosted stores
        // (atomchat-data/media and /avatars). These sit on the game bus: the
        // tick and start events fire for a dedicated server and for the
        // integrated server of a single-player world alike, while a client
        // connected to someone else's server never sees them.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            com.atom.chat.net.CompanionMaintenance.tick();
            // The pack download queue: a few chunks per player per tick.
            com.atom.chat.net.PackSyncServer.tick(event.getServer());
        });
        com.atom.chat.net.ConfigScreenServer.register();
        // Simulation harness for a single-player session: /atomchat test, which
        // can only be reached with the debug switch on (see the class docs).
        NeoForge.EVENT_BUS.addListener(com.atom.chat.net.TestCompanionServer::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                com.atom.chat.net.CompanionMaintenance.onServerStarted());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() != null) {
                com.atom.chat.net.CompanionMaintenance.onPlayerLogout(event.getEntity().getUUID());
            }
        });
        version = modContainer.getModInfo().getVersion().toString();
        LOGGER.info("AtomChat initialized");
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        com.atom.chat.net.AvatarPayloads.register(event);
        // Poke companion: a remote poke, rate limited server-side.
        com.atom.chat.net.PokePayloads.register(event);
        // Simulation harness for a single-player session (/atomchat test).
        com.atom.chat.net.TestPayloads.register(event);
        // Server pack distribution (0.2.9): emotes, phrases and the server
        // identity, plus the config screen's snapshot/save pair.
        com.atom.chat.net.PackPayloads.register(event);
        com.atom.chat.net.ConfigPayloads.register(event);
        // Media companion: server-hosted chat images / GIFs, same dual
        // entrypoint pattern and master hosting switch as the avatar side.
        com.atom.chat.net.MediaPayloads.register(event);
    }

    /**
     * Friendly mod version for the settings about page. Read from the loader
     * metadata rather than a hardcoded constant so it can never drift away
     * from gradle.properties.
     */
    public static String version() {
        return version;
    }

    /**
     * The jar this build was loaded from, asked of the loader rather than
     * derived from our own code source: NeoForge runs mods off a transformed
     * class path, so a protection-domain lookup can name something other than
     * the file the player installed. The environment summary prints this next
     * to its content hash — a jar can be renamed to anything, a hash cannot
     * (see the "file says 0.2.8, metadata says 0.2.7" report that motivated it).
     * Null outside a real launch.
     */
    public static java.nio.file.Path artifactPath() {
        try {
            return net.neoforged.fml.ModList.get()
                    .getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getOwningFile())
                    .map(file -> file.getFile().getFilePath())
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
