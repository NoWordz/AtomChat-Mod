package com.atom.chat.net;
import java.util.UUID;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
/** One native broadcast, including console /say, has one identity across its actual recipients. */
public final class ReactionBroadcastMessage implements OutgoingChatMessage {
    private final OutgoingChatMessage delegate;
    private final UUID id=UUID.randomUUID();
    public ReactionBroadcastMessage(OutgoingChatMessage delegate) { this.delegate=delegate; }
    @Override public Component content() { return delegate.content(); }
    @Override public void sendToPlayer(ServerPlayer receiver,boolean filtered,net.minecraft.network.chat.ChatType.Bound bound) {
        Object source=delegate instanceof OutgoingChatMessage.Disguised ? delegate.content() : null;
        try(var scope=ReactionCompanionServer.broadcastDelivery(receiver,source,id)) { delegate.sendToPlayer(receiver,filtered,bound); }
    }
}
