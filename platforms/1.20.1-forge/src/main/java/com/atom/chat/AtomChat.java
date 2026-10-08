package com.atom.chat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AtomChat.MOD_ID)
public class AtomChat {
    public static final String MOD_ID = "atomchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Resolved lazily from the Forge mod list (see {@link #version()}). */
    private static volatile String version;

    public AtomChat() {
        // 第一件事：把平台门面装上（共享层要问加载器的事情都走它）。
        // 必须在任何配置/路径访问之前 —— 没装就抛错，不给默认值。
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.ForgePlatform());
        // The pack seams go up in the same breath: the shared sender and the
        // server facts it asks for. Both sides of the network seam live here and
        // in AtomChatClient; a missing install throws at the first packet.
        com.atom.chat.net.PackNetServer.install();
        // Config seam for anti-spam: wire the pure logic to the real config
        // only in a real launch (unit tests keep the safe no-merge default).
        com.atom.chat.chat.MessageMerge.antiSpamEnabledSupplier =
                () -> com.atom.chat.config.AtomChatConfig.get().antiSpamEnabled;

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // The avatar channel is common: a dedicated server and the integrated
        // server of a double-open client both need it (this event fires for
        // both logical sides).
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            com.atom.chat.net.AvatarPayloads.register();
            // Poke companion: a remote poke, rate limited server-side.
            com.atom.chat.net.PokePayloads.register();
            // Media companion: server-hosted chat images / GIFs, same dual
            // entrypoint pattern and master hosting switch as the avatar side.
            com.atom.chat.net.MediaPayloads.register();
            // Server pack distribution (0.2.9) and the config screen's
            // snapshot/save pair; both are common-side channels.
            com.atom.chat.net.PackPayloads.register();
            com.atom.chat.net.ConfigPayloads.register();
        }));

        // Retention and lifecycle housekeeping for the two hosted stores
        // (atomchat-data/media and /avatars). These sit on the game bus: the
        // tick fires for a dedicated server and for the integrated server of a
        // single-player world alike, while a client connected to someone else's
        // server never sees either event.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                com.atom.chat.net.CompanionMaintenance.tick();
                // The pack download queue: a few chunks per player per tick.
                com.atom.chat.net.PackSyncServer.tick(event.getServer());
            }
        });
        com.atom.chat.net.ConfigScreenServer.register();
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                com.atom.chat.net.CompanionMaintenance.onServerStarted());
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() != null) {
                com.atom.chat.net.CompanionMaintenance.onPlayerLogout(event.getEntity().getUUID());
            }
        });

        if (FMLEnvironment.dist.isClient()) {
            // Client-only listeners, key mappings, shaders and the AWT setup.
            // Reached only on a client, so a dedicated server never resolves
            // the client class.
            AtomChatClient.init(modEventBus);
        }

        LOGGER.info("AtomChat initialized");
    }

    /**
     * Friendly mod version for the settings about page. Read from the loader
     * metadata rather than a hardcoded constant so it can never drift away
     * from gradle.properties. Falls back to "unknown" outside a real launch
     * (unit tests), where no mod list exists.
     */
    public static String version() {
        String resolved = version;
        if (resolved == null) {
            resolved = "unknown";
            try {
                resolved = net.minecraftforge.fml.ModList.get()
                        .getModContainerById(MOD_ID)
                        .map(container -> container.getModInfo().getVersion().toString())
                        .orElse("unknown");
            } catch (Throwable ignored) {
            }
            version = resolved;
        }
        return resolved;
    }

    /**
     * The jar this build was loaded from, asked of the loader rather than
     * derived from our own code source: Forge runs mods off a transformed
     * class path, so a protection-domain lookup can name something other than
     * the file the player installed. The environment summary prints this next
     * to its content hash — a jar can be renamed to anything, a hash cannot
     * (see the "file says 0.2.8, metadata says 0.2.7" report that motivated it).
     * Null outside a real launch.
     */
    public static java.nio.file.Path artifactPath() {
        try {
            return net.minecraftforge.fml.ModList.get()
                    .getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getOwningFile())
                    .map(file -> file.getFile().getFilePath())
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
