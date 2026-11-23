package com.atom.chat.net;

import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardStateStore;
import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.card.BuiltInCardAdapter;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.text.RichText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Client-side receiver &amp; sender for {@link CardPayloads}.
 */
public final class CardCompanionClient {

    private CardCompanionClient() {
    }

    public static boolean supported() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null && client.getConnection() != null
                && client.getConnection().hasChannel(CardPayloads.ClientCardSharePayload.TYPE);
    }

    public static void onCardBroadcast(CardPayloads.CardBroadcastPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        ShareCard card = CardWireCodec.decodeCard(payload.cardBytes());
        if (card == null) return;

        // Rehydrate live ItemStack hover for ITEM cards so native tooltips work immediately.
        if (card.template() == ShareCard.Template.ITEM && card.nativeHoverObject() == null) {
            HoverEvent hover = BuiltInCardAdapter.resolveItemHover(card);
            if (hover != null) {
                card = card.withNativeHoverObject(hover);
            }
        }

        Component fallback = null;
        if (payload.fallbackJson() != null && !payload.fallbackJson().isBlank()) {
            try {
                fallback = Component.Serializer.fromJson(payload.fallbackJson(), client.level.registryAccess());
            } catch (Throwable ignored) {
            }
        }
        if (fallback == null) {
            fallback = Component.literal(card.fallbackPlainText());
        }

        UUID senderUuid = payload.senderUuid();
        String senderName = payload.senderName();
        boolean own = client.player != null && senderUuid != null && senderUuid.equals(client.player.getUUID());
        boolean system = payload.system() && (senderName == null || senderName.isBlank());
        if (!own && !system && senderName != null && !senderName.isBlank()
                && com.atom.chat.config.AtomChatConfig.get().hideBlockedMessages
                && com.atom.chat.chat.BlockList.isBlocked(senderName)) {
            return;
        }

        RichText senderRich = system ? RichText.empty() : RichText.literal(senderName == null ? "" : senderName);
        RichText contentRich = RichText.literal(card.fallbackPlainText());
        ChatMessage msg = new ChatMessage(
                fallback, own, system, null, null,
                senderUuid, system ? null : senderName, system ? null : senderName,
                card.fallbackPlainText(), senderRich, contentRich)
                .withCard(card);
        ChatStore.get().add(msg);

        // Also surface a clean one-line entry into vanilla ChatComponent / gameplay HUD queue
        // without re-capturing a duplicate row in ChatStore.
        if (client.gui != null && client.gui.getChat() != null) {
            com.atom.chat.card.CardHudBridge.queueCardInHud(client, msg);
        }
    }

    public static void onCardStateUpdate(CardPayloads.CardStateUpdatePayload payload) {
        CardState state = CardWireCodec.decodeState(payload.stateBytes());
        if (state != null) {
            CardStateStore.update(state);
        }
    }

    /**
     * Shares a client-initiated LOCATION or ITEM card. Uses C2S channel when server
     * runs AtomChat, or falls back to standard chat text (e.g. JourneyMap bracket syntax
     * for coordinates) so even vanilla servers work seamlessly.
     */
    public static boolean shareFromClient(ShareCard card) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null || card == null) {
            return false;
        }
        if (supported()) {
            // Strip itemSnbt before C2S upload: server re-reads real main-hand ItemStack,
            // preventing heavy items (e.g. Shulker Boxes) from exceeding packet limits.
            ShareCard toSend = card;
            if (card.template() == ShareCard.Template.ITEM && card.item() != null && !card.item().itemSnbt().isEmpty()) {
                ShareCard.ItemPayload slim = new ShareCard.ItemPayload(
                        card.item().itemId(), "", card.item().displayName(),
                        card.item().summary(), card.item().rarityLabel(),
                        card.item().accentColor(), card.item().count());
                toSend = card.toBuilder().itemPayload(slim).build();
            }
            byte[] encoded = CardWireCodec.encodeCard(toSend);
            if (encoded.length <= CardWireCodec.MAX_CARD_BYTES) {
                PacketDistributor.sendToServer(new CardPayloads.ClientCardSharePayload(encoded));
                return true;
            }
            return false;
        }
        // Fallback for servers without AtomChat installed: sanitize and clamp to vanilla 256-char limit.
        if (card.template() == ShareCard.Template.LOCATION && card.location() != null) {
            ShareCard.LocationPayload loc = card.location();
            String safeName = com.atom.chat.api.card.BuiltInCardParsers.sanitizeLabel(loc.name(), 32).replace('"', '\'');
            String safeDim = com.atom.chat.api.card.BuiltInCardParsers.normalizeDimension(loc.dimension());
            String msg = "[name:\"" + safeName + "\", x:" + loc.blockX()
                    + ", y:" + loc.blockY() + ", z:" + loc.blockZ()
                    + ", dim:" + safeDim + "]";
            client.getConnection().sendChat(clampChat(msg));
            return true;
        }
        if (card.template() == ShareCard.Template.ITEM && card.item() != null) {
            String safeName = com.atom.chat.api.card.BuiltInCardParsers.sanitizeLabel(card.item().displayName(), 48);
            String safeSummary = com.atom.chat.api.card.BuiltInCardParsers.sanitizeLabel(card.item().summary(), 80);
            String msg = "[装备] " + safeName
                    + (card.item().count() > 1 ? " ×" + card.item().count() : "")
                    + (safeSummary.isBlank() ? "" : " (" + safeSummary + ")");
            client.getConnection().sendChat(clampChat(msg));
            return true;
        }
        return false;
    }

    private static String clampChat(String raw) {
        String clean = com.atom.chat.api.card.BuiltInCardParsers.sanitizeLabel(raw, 240);
        return clean.isEmpty() ? "[分享]" : clean;
    }
}
