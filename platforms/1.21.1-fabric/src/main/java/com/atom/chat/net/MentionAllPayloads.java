package com.atom.chat.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** The server resolves the operator, sender identity and complete online audience. */
public final class MentionAllPayloads {
    private MentionAllPayloads() {}

    public record Request() implements CustomPayload {
        static final Id<Request> ID = new Id<>(Identifier.of("atomchat", "mention_all"));
        static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.unit(new Request());

        @Override public Id<Request> getId() { return ID; }
    }

    public record Notice(UUID sender, String name) implements CustomPayload {
        static final Id<Notice> ID = new Id<>(Identifier.of("atomchat", "mention_all_notice"));
        static final PacketCodec<RegistryByteBuf, Notice> CODEC = PacketCodec.of(
                (notice, buf) -> { buf.writeUuid(notice.sender()); buf.writeString(notice.name(), 64); },
                buf -> new Notice(buf.readUuid(), buf.readString(64)));

        @Override public Id<Notice> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Notice.ID, Notice.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,
                (payload, context) -> context.server().execute(() -> broadcast(context.player())));
    }

    private static void broadcast(ServerPlayerEntity sender) {
        var players = sender.getServer().getPlayerManager();
        if (!players.isOperator(sender.getGameProfile())) return;
        if (!com.atom.chat.config.AtomChatServerConfig.get().hostingEnabled) {
            sender.sendMessage(Text.translatable("atomchat.tools.mention_all.unavailable"), true);
            return;
        }
        String name = sender.getGameProfile().getName();
        var notice = new Notice(sender.getUuid(), name);
        for (ServerPlayerEntity recipient : players.getPlayerList()) {
            if (ServerPlayNetworking.canSend(recipient, Notice.ID)) ServerPlayNetworking.send(recipient, notice);
            else recipient.sendMessage(Text.literal("<" + name + "> @everyone"), false);
        }
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(Notice.ID,
                (notice, context) -> {
                    if (context.client().getNetworkHandler() != null
                            && context.responseSender() == ClientPlayNetworking.getSender()) {
                        receive(context.client(), notice);
                    }
                });
    }

    public static boolean send() {
        if (!ClientPlayNetworking.canSend(Request.ID)) return false;
        ClientPlayNetworking.send(new Request());
        return true;
    }

    private static void receive(MinecraftClient client, Notice notice) {
        var social = client.getSocialInteractionsManager();
        if (client.player == null || !client.getChatRestriction().allowsChat(client.isInSingleplayer())
                || client.options.getChatVisibility().getValue() == net.minecraft.network.message.ChatVisibility.HIDDEN
                || social.isPlayerBlocked(notice.sender()) || social.isPlayerHidden(notice.sender())
                || com.atom.chat.chat.BlockList.isBlocked(notice.name())) return;
        com.atom.chat.notification.NotificationController.onMentionAll(notice.sender(), notice.name());
    }
}
