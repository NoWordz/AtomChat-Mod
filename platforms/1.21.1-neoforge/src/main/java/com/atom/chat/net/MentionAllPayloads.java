package com.atom.chat.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

/** The server resolves the operator, sender identity and complete online audience. */
public final class MentionAllPayloads {
    private MentionAllPayloads() {}

    public record Request() implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atomchat", "mention_all"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override public Type<Request> type() { return TYPE; }
    }

    public record Notice(UUID sender, String name) implements CustomPacketPayload {
        static final Type<Notice> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atomchat", "mention_all_notice"));
        static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC = StreamCodec.of(
                (buf, notice) -> { buf.writeUUID(notice.sender()); buf.writeUtf(notice.name(), 64); },
                buf -> new Notice(buf.readUUID(), buf.readUtf(64)));

        @Override public Type<Notice> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) broadcast(sender);
        }));
        registrar.playToClient(Notice.TYPE, Notice.CODEC, (notice, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> {
                var client = Minecraft.getInstance();
                if (client.getConnection() != null && client.getConnection().getConnection() == context.connection()) {
                    receive(client, notice);
                }
            });
        });
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
            if (recipient.connection.hasChannel(Notice.TYPE)) PacketDistributor.sendToPlayer(recipient, notice);
            else recipient.sendSystemMessage(Component.literal("<" + name + "> @everyone"));
        }
    }

    public static boolean send() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(Request.TYPE)) return false;
        PacketDistributor.sendToServer(new Request());
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
