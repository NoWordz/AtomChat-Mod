package com.atom.chat.card;

import com.atom.chat.chat.ChatMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Coordinates S2C {@link com.atom.chat.api.card.ShareCard} messages with vanilla's
 * {@code ChatComponent} so they also appear in AtomChat's gameplay HUD without being
 * captured a second time into {@code ChatStore}.
 */
public final class CardHudBridge {
    private static ChatMessage staged;

    private CardHudBridge() {
    }

    /** Queues an already-stored card message into the vanilla chat HUD pipeline. */
    public static void queueCardInHud(Minecraft client, ChatMessage cardMessage) {
        if (client == null || client.gui == null || client.gui.getChat() == null || cardMessage == null) {
            return;
        }
        Component line = cardMessage.getComponent();
        if (line == null) {
            line = Component.literal(cardMessage.getContentText());
        }
        staged = cardMessage;
        try {
            client.gui.getChat().addMessage(line);
        } finally {
            staged = null;
        }
    }

    /** Consumed by {@code ChatHudMixin} on the render thread when a staged card arrives. */
    public static ChatMessage consumeStaged() {
        ChatMessage m = staged;
        staged = null;
        return m;
    }
}
