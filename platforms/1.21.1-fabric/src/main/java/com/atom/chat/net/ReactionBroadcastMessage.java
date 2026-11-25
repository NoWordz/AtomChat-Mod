package com.atom.chat.net;
import java.util.UUID;
import net.minecraft.network.message.SentMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
/** One native broadcast, including console /say, has one identity across its actual recipients. */
public final class ReactionBroadcastMessage implements SentMessage {
    private final SentMessage delegate;
    private final UUID id=UUID.randomUUID();
    private final CardCompanionServer.PlayerShareDelivery cardDelivery;
    public ReactionBroadcastMessage(SentMessage delegate, CardCompanionServer.PlayerShareDelivery cardDelivery) {
        this.delegate=delegate;
        this.cardDelivery=cardDelivery;
    }
    @Override public Text content() { return delegate.content(); }
    @Override public void send(ServerPlayerEntity receiver,boolean filtered,net.minecraft.network.message.MessageType.Parameters bound) {
        Object source = delegate instanceof SentMessage.Profileless ? delegate.content() : null;
        try (var scope = ReactionCompanionServer.broadcastDelivery(receiver,source,id)) {
            if (cardDelivery == null) {
                delegate.send(receiver,filtered,bound);
            } else {
                CardCompanionServer.sendPlayerShare(receiver, filtered, bound.applyChatDecoration(delegate.content()),
                        cardDelivery, () -> delegate.send(receiver,filtered,bound));
            }
        }
    }
}
