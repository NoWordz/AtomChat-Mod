package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /atomchat test} — the server half of the simulation harness.
 *
 * <p>A single-player session cannot produce a mention banner, a whisper banner or
 * a received poke: there is nobody else on the server to trigger them. Testing
 * them the honest way means two clients, which doubles the log noise the tester
 * is reading to diagnose with. This command asks the server to act as the other
 * party instead, sending the same payload the real event would.</p>
 *
 * <p>Deliberately gated behind the client's debug switch, and behind the same
 * permission rule as {@code /atomchat gui}: single-player hosts always, everyone
 * else at OP level 2. Nothing here is reachable in a normal session.</p>
 *
 * <p>The poke test bypasses the master hosting switch on purpose — it is
 * exercising the client's reaction, not the production relay, and making it
 * depend on a hosting toggle would leave the command silently inert exactly when
 * the tester needs it.</p>
 */
public final class TestCompanionServer {
    private TestCompanionServer() {
    }

    /** Vanilla permission level for "can run most commands", same as the GUI. */
    private static final int REQUIRED_LEVEL = 2;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(CommandManager.literal("atomchat")
                        .then(CommandManager.literal("test")
                                .requires(TestCompanionServer::mayTest)
                                .then(CommandManager.literal("banner")
                                        .then(bannerBranch(TestPayloads.KIND_MENTION))
                                        .then(bannerBranch(TestPayloads.KIND_QUOTE))
                                        .then(bannerBranch(TestPayloads.KIND_WHISPER)))
                                .then(CommandManager.literal("poke")
                                        .executes(context -> firePoke(context.getSource(), ""))
                                        .then(CommandManager.argument("sender", StringArgumentType.greedyString())
                                                .executes(context -> firePoke(context.getSource(),
                                                        StringArgumentType.getString(context, "sender"))))))));
        AtomChat.LOGGER.info("AtomChat test command registered (server side)");
    }

    /** {@code … banner <kind> [text]} — one branch per kind, so tab-completion spells them out. */
    private static LiteralArgumentBuilder<ServerCommandSource> bannerBranch(String kind) {
        return CommandManager.literal(kind)
                .executes(context -> fireBanner(context.getSource(), kind, ""))
                .then(CommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(context -> fireBanner(context.getSource(), kind,
                                StringArgumentType.getString(context, "text"))));
    }

    /**
     * Host of a single-player world may always test their own settings, cheats or
     * not; everyone else needs OP level 2 — the same rule the config screen uses.
     */
    private static boolean mayTest(ServerCommandSource source) {
        if (source.getServer() != null && source.getServer().isSingleplayer()) {
            return true;
        }
        return source.hasPermissionLevel(REQUIRED_LEVEL);
    }

    private static int fireBanner(ServerCommandSource source, String kind, String text) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null || !requireDebug(source)) {
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, TestPayloads.TestEventS2CPayload.ID)) {
            // The player's client has no AtomChat, or has not negotiated the
            // channel: there is nothing to render the banner with.
            source.sendError(Text.translatable("atomchat.command.test.noChannel"));
            return 0;
        }
        ServerPlayNetworking.send(player, new TestPayloads.TestEventS2CPayload(
                kind, player.getUuid(), player.getName().getString(), text));
        AtomChat.LOGGER.info("[test] {} asked for a {} banner", player.getName().getString(), kind);
        source.sendFeedback(() -> Text.translatable("atomchat.command.test.bannerSent",
                Text.translatable("atomchat.test.kind." + kind)), false);
        return 1;
    }

    private static int firePoke(ServerCommandSource source, String sender) {
        ServerPlayerEntity player = requirePlayer(source);
        if (player == null || !requireDebug(source)) {
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, TestPayloads.TestEventS2CPayload.ID)) {
            source.sendError(Text.translatable("atomchat.command.test.noChannel"));
            return 0;
        }
        ServerPlayNetworking.send(player, new TestPayloads.TestEventS2CPayload(
                TestPayloads.KIND_POKE, player.getUuid(), sender, ""));
        AtomChat.LOGGER.info("[test] {} asked for a poke cue", player.getName().getString());
        source.sendFeedback(() -> Text.translatable("atomchat.command.test.pokeSent"), false);
        return 1;
    }

    /** {@code null} (and a console error) when a console or command block ran this. */
    private static ServerPlayerEntity requirePlayer(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.translatable("atomchat.server.console_only"));
        }
        return player;
    }

    /** The debug switch is the gate the user asked for; report why it refused. */
    private static boolean requireDebug(ServerCommandSource source) {
        if (AtomChatConfig.get().debug) {
            return true;
        }
        source.sendError(Text.translatable("atomchat.command.test.debugRequired"));
        return false;
    }
}
