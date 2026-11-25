package com.atom.chat.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/** The server resolves the operator, sender identity and complete online audience. */
public final class MentionAllPayloads {
    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;

    private MentionAllPayloads() {}

    private record Request() {}
    private record Notice(UUID sender, String name) {}

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("atomchat", "mention_all"),
                () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
        channel.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buf) -> {})
                .decoder(buf -> new Request())
                .consumerMainThread((request, context) -> {
                    var sender = context.get().getSender();
                    if (sender != null) broadcast(sender);
                    context.get().setPacketHandled(true);
                }).add();
        channel.messageBuilder(Notice.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((notice, buf) -> { buf.writeUUID(notice.sender()); buf.writeUtf(notice.name(), 64); })
                .decoder(buf -> new Notice(buf.readUUID(), buf.readUtf(64)))
                .consumerMainThread((notice, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        var client = Minecraft.getInstance();
                        if (client.getConnection() != null
                                && client.getConnection().getConnection() == context.get().getNetworkManager()) {
                            receive(client, notice);
                        }
                    }
                    context.get().setPacketHandled(true);
                }).add();
    }

    private static void broadcast(ServerPlayer sender) {
        var players = sender.server.getPlayerList();
        if (!players.isOp(sender.getGameProfile())) return;
        if (!com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) {
            sender.displayClientMessage(Component.translatable("atomchat.tools.mention_all.unavailable"), true);
            return;
        }
        String name = sender.getGameProfile().getName();
        var notice = new Notice(sender.getUUID(), name);
        for (ServerPlayer recipient : players.getPlayers()) {
            if (channel.isRemotePresent(recipient.connection.connection)) {
                channel.send(PacketDistributor.PLAYER.with(() -> recipient), notice);
            } else {
                recipient.sendSystemMessage(Component.literal("<" + name + "> @everyone"));
            }
        }
    }

    public static boolean send() {
        var connection = Minecraft.getInstance().getConnection();
        if (channel == null || connection == null || !channel.isRemotePresent(connection.getConnection())) return false;
        channel.sendToServer(new Request());
        return true;
    }

    private static void receive(Minecraft client, Notice notice) {
        if (client.player == null || !client.getChatStatus().isChatAllowed(client.isLocalServer())
                || client.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN
                || client.isBlocked(notice.sender()) || client.getPlayerSocialManager().isHidden(notice.sender())
                || com.atom.chat.chat.BlockList.isBlocked(notice.name())) return;
        com.atom.chat.notification.NotificationController.onMentionAll(notice.sender(), notice.name());
    }
}
