package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.config.ServerConfigValues;
import com.atom.chat.pack.ServerPack;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server half of the config screen (0.2.9): the {@code /atomchat gui} command
 * and the save path behind it.
 *
 * <p>The screen is never built here - it is built by the client of whoever ran
 * the command, which is what makes this work on a rented server with no display
 * at all. All this side does is decide whether that player may edit, hand out a
 * snapshot, and re-check everything they send back.
 *
 * <p>{@link #VERSION} is the concurrency guard (decision 25): two operators can
 * open the screen, and the second save carries a stale version, so it is refused
 * with "reopen the screen" instead of silently overwriting the first.
 */
public final class ConfigScreenServer {
    /** Vanilla permission level for "can run most commands". */
    private static final int REQUIRED_LEVEL = 2;
    private static final AtomicInteger VERSION = new AtomicInteger(1);

    private ConfigScreenServer() {
    }

    /** Registers the command and the receivers; runs on both logical sides. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(CommandManager.literal("atomchat")
                        .then(CommandManager.literal("gui")
                                .requires(ConfigScreenServer::mayEdit)
                                .executes(context -> openGui(context.getSource())))));
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayloads.Save.ID, (payload, context) ->
                onServerThread(context.player(), () -> onSave(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayloads.Refresh.ID, (payload, context) ->
                onServerThread(context.player(), () -> onRefresh(context.player())));
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("config", "AtomChat config screen registered (server side)"));
    }

    /**
     * A single-player host may always edit their own settings, cheats or not
     * (decision: single-player is let through); everyone else needs OP level 2.
     */
    private static boolean mayEdit(ServerCommandSource source) {
        if (source.getServer() != null && source.getServer().isSingleplayer()) {
            return true;
        }
        return source.hasPermissionLevel(REQUIRED_LEVEL);
    }

    private static int openGui(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            // A console (or a hosting panel) has no client to draw the screen on.
            source.sendError(Text.translatable("atomchat.server.console_only"));
            return 0;
        }
        sendSnapshot(player);
        return 1;
    }

    private static void sendSnapshot(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new ConfigPayloads.Snapshot(
                VERSION.get(), ServerConfigValues.of(AtomChatServerConfig.get()), packStatus(player)));
    }

    private static ConfigPayloads.PackStatus packStatus(ServerPlayerEntity player) {
        ServerPack pack = PackSyncServer.peek(player.getServer());
        return new ConfigPayloads.PackStatus(pack.files().size(), pack.totalBytes(), pack.packHash());
    }

    private static void onServerThread(ServerPlayerEntity player, Runnable work) {
        MinecraftServer server = player == null ? null : player.getServer();
        // Writing a config file and re-hashing the emote folder belong off the
        // network thread.
        if (server != null) {
            server.execute(work);
        }
    }

    private static void onRefresh(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        // Drop the cached pack so the folders are scanned again, then report the
        // new summary. The values are deliberately not resent: the screen may be
        // holding edits the operator has not saved yet.
        PackSyncServer.invalidate();
        ServerPlayNetworking.send(player, new ConfigPayloads.Status(packStatus(player)));
    }

    private static void onSave(ServerPlayerEntity player, ConfigPayloads.Save payload) {
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
        ServerPlayNetworking.send(player, new ConfigPayloads.Result(true, ""));
        player.sendMessage(Text.translatable("atomchat.server.saved"), false);
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("config", "Server config saved by {} (version {}, hosting {}, packs {})"),
                player.getName().getString(), version, values.hostingEnabled(), values.packEnabled());
    }

    private static void deny(ServerPlayerEntity player, String error) {
        ServerPlayNetworking.send(player, new ConfigPayloads.Result(false, error));
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("config", "Refused a server config save from {}: {}"),
                player.getName().getString(), error);
    }
}
