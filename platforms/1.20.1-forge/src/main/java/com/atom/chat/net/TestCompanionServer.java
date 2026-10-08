package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * {@code /atomchat test} — the server half of the simulation harness.
 *
 * <p>A single-player session cannot produce a mention banner, a whisper banner or
 * a received poke: there is nobody else on the server to trigger them. Testing
 * them the honest way means two clients, which doubles the log noise the tester
 * is reading to diagnose with. This command asks the server to act as the other
 * party instead, sending the same payload the real event would.</p>
 *
 * <p>Deliberately gated behind the debug switch, and behind the same permission
 * rule as {@code /atomchat gui}: single-player hosts always, everyone else at OP
 * level 2. Nothing here is reachable in a normal session.</p>
 */
public final class TestCompanionServer {
    private TestCompanionServer() {
    }

    /** Vanilla permission level for "can run most commands", same as the GUI. */
    private static final int REQUIRED_LEVEL = 2;

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("atomchat")
                .then(Commands.literal("test")
                        .requires(TestCompanionServer::mayTest)
                        .then(Commands.literal("banner")
                                .then(bannerBranch(TestPayloads.KIND_MENTION))
                                .then(bannerBranch(TestPayloads.KIND_QUOTE))
                                .then(bannerBranch(TestPayloads.KIND_WHISPER)))
                        .then(Commands.literal("poke")
                                .executes(context -> firePoke(context.getSource(), ""))
                                .then(Commands.argument("sender", StringArgumentType.greedyString())
                                        .executes(context -> firePoke(context.getSource(),
                                                StringArgumentType.getString(context, "sender")))))));
        AtomChat.LOGGER.info("AtomChat test command registered (server side)");
    }

    /** {@code … banner <kind> [text]} — one branch per kind, so tab-completion spells them out. */
    private static LiteralArgumentBuilder<CommandSourceStack> bannerBranch(String kind) {
        return Commands.literal(kind)
                .executes(context -> fireBanner(context.getSource(), kind, ""))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> fireBanner(context.getSource(), kind,
                                StringArgumentType.getString(context, "text"))));
    }

    /**
     * Host of a single-player world may always test their own settings, cheats or
     * not; everyone else needs OP level 2 — the same rule the config screen uses.
     */
    private static boolean mayTest(CommandSourceStack source) {
        if (source.getServer() != null && source.getServer().isSingleplayer()) {
            return true;
        }
        return source.hasPermission(REQUIRED_LEVEL);
    }

    private static int fireBanner(CommandSourceStack source, String kind, String text) {
        ServerPlayer player = requirePlayer(source);
        if (player == null || !requireDebug(source)) {
            return 0;
        }
        if (!TestPayloads.CHANNEL.isRemotePresent(player.connection.connection)) {
            source.sendFailure(Component.translatable("atomchat.command.test.noChannel"));
            return 0;
        }
        TestPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TestPayloads.TestEventS2CPayload(kind, player.getUUID(),
                        player.getName().getString(), text));
        AtomChat.LOGGER.info("[test] {} asked for a {} banner", player.getName().getString(), kind);
        source.sendSuccess(() -> Component.translatable("atomchat.command.test.bannerSent",
                Component.translatable("atomchat.test.kind." + kind)), false);
        return 1;
    }

    private static int firePoke(CommandSourceStack source, String sender) {
        ServerPlayer player = requirePlayer(source);
        if (player == null || !requireDebug(source)) {
            return 0;
        }
        if (!TestPayloads.CHANNEL.isRemotePresent(player.connection.connection)) {
            source.sendFailure(Component.translatable("atomchat.command.test.noChannel"));
            return 0;
        }
        TestPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TestPayloads.TestEventS2CPayload(TestPayloads.KIND_POKE,
                        player.getUUID(), sender, ""));
        AtomChat.LOGGER.info("[test] {} asked for a poke cue", player.getName().getString());
        source.sendSuccess(() -> Component.translatable("atomchat.command.test.pokeSent"), false);
        return 1;
    }

    /** {@code null} (and a console error) when a console or command block ran this. */
    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("atomchat.server.console_only"));
        }
        return player;
    }

    /** The debug switch is the gate the user asked for; report why it refused. */
    private static boolean requireDebug(CommandSourceStack source) {
        if (AtomChatConfig.get().debug) {
            return true;
        }
        source.sendFailure(Component.translatable("atomchat.command.test.debugRequired"));
        return false;
    }
}
