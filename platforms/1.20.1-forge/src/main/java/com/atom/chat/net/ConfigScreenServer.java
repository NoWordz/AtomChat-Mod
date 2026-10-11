package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.config.ServerConfigValues;
import com.atom.chat.pack.ServerPack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server half of the config screen (0.2.9): the {@code /atomchat gui} command and
 * the save path behind it.
 *
 * <p>The screen is never built here - it is built by the client of whoever ran the
 * command, which is what makes this work on a rented server with no display at
 * all. All this side does is decide whether that player may edit, hand out a
 * snapshot, and re-check everything they send back.
 *
 * <p>{@link #VERSION} is the concurrency guard: two operators can open the screen,
 * and the second save carries a stale version, so it is refused with "reopen the
 * screen" instead of silently overwriting the first.
 */
public final class ConfigScreenServer {
    /** Vanilla permission level for "can run most commands". */
    private static final int REQUIRED_LEVEL = 2;
    private static final AtomicInteger VERSION = new AtomicInteger(1);

    private ConfigScreenServer() {
    }

    /** Registers the command; called from the common entrypoint (game bus). */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("atomchat")
                        .then(Commands.literal("gui")
                                .requires(ConfigScreenServer::mayEdit)
                                .executes(context -> openGui(context.getSource())))));
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("config", "AtomChat config screen registered (server side)"));
    }

    /**
     * A single-player host may always edit their own settings, cheats or not;
     * everyone else needs OP level 2.
     */
    private static boolean mayEdit(CommandSourceStack source) {
        if (source.getServer() != null && source.getServer().isSingleplayer()) {
            return true;
        }
        return source.hasPermission(REQUIRED_LEVEL);
    }

    private static int openGui(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            // A console (or a hosting panel) has no client to draw the screen on.
            source.sendFailure(Component.translatable("atomchat.server.console_only"));
            return 0;
        }
        sendSnapshot(player);
        return 1;
    }

    /** C2S: the client saved the screen. */
    public static void onSave(Player sender, ConfigPayloads.Save payload) {
        ServerPlayer player = sender instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null) {
            return;
        }
        if (payload.configVersion() != VERSION.get()) {
            deny(player, "conflict");
            return;
        }
        ServerConfigValues values = payload.values().sanitize();
        String error = values.validate();
        if (error != null) {
            deny(player, error);
            return;
        }
        AtomChatServerConfig config = AtomChatServerConfig.get();
        values.applyTo(config);
        AtomChatServerConfig.save(config);
        // The running instance is the cache; reloading keeps get() and the file
        // in step without waiting for the next join.
        AtomChatServerConfig.reload();
        PackSyncServer.invalidate();
        int version = VERSION.incrementAndGet();
        ConfigPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigPayloads.Result(true, ""));
        player.sendSystemMessage(Component.translatable("atomchat.server.saved"));
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("config", "Server config saved by {} (version {}, hosting {}, packs {})"),
                player.getName().getString(), version, values.hostingEnabled(), values.packEnabled());
    }

    /** C2S: "re-scan the emote folder and tell me what the pack looks like now". */
    public static void onRefresh(Player sender) {
        ServerPlayer player = sender instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null) {
            return;
        }
        // Drop the cached pack so the folders are scanned again, then report the
        // new summary. The values are deliberately not resent: the screen may be
        // holding edits the operator has not saved yet.
        PackSyncServer.invalidate();
        ConfigPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigPayloads.Status(packStatus(player)));
    }

    private static void sendSnapshot(ServerPlayer player) {
        ConfigPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigPayloads.Snapshot(
                VERSION.get(), ServerConfigValues.of(AtomChatServerConfig.get()), packStatus(player)));
    }

    private static ConfigPayloads.PackStatus packStatus(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerPack pack = PackSyncServer.peek(server);
        return new ConfigPayloads.PackStatus(pack.files().size(), pack.totalBytes(), pack.packHash());
    }

    private static void deny(ServerPlayer player, String error) {
        ConfigPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConfigPayloads.Result(false, error));
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("config", "Refused a server config save from {}: {}"),
                player.getName().getString(), error);
    }
}
