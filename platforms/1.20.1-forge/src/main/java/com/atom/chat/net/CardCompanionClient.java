package com.atom.chat.net;

import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardStateStore;
import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.card.BuiltInCardAdapter;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.text.RichText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.UUID;

/**
 * Forge 1.20.1 client-side receiver &amp; sender for {@link CardPayloads}.
 */
public final class CardCompanionClient {
    private static final PlayerCardPairing<UUID> NATIVE_SHARES = PlayerCardPairing.byUuid();

    private static final ConvertedSharePairing CONVERTED_SHARES = new ConvertedSharePairing();

    public static void resetNativeShares() {
        NATIVE_SHARES.clear();
        CONVERTED_SHARES.clear();
    }

    public static void deliverConvertedShare(UUID fingerprint, Object component, Runnable delivery) {
        CONVERTED_SHARES.deliver(fingerprint, component, System.nanoTime(), () -> {
            var share = CONVERTED_SHARES.current();
            if (share == null || shouldDisplayConverted(share)) delivery.run();
        });
    }

    public static ConvertedSharePairing.Share takeConvertedShare(Object component) {
        return com.atom.chat.config.AtomChatConfig.get().shareCardsEnabled
                ? CONVERTED_SHARES.takeVisible(component) : null;
    }

    public static void discardConvertedShare() {
        CONVERTED_SHARES.discardPending();
    }

    public static ShareCard takeNativeShare(UUID sender, String originalBody) {
        if (sender == null || originalBody == null) return null;
        ShareCard card = NATIVE_SHARES.takeVisible(sender, originalBody);
        if (card != null) CardStateStore.received(card);
        return card;
    }

    public static java.util.function.BooleanSupplier deferNativeShare(
            UUID sender, String originalBody, java.util.function.BooleanSupplier callback) {
        return sender == null ? callback : NATIVE_SHARES.defer(sender, originalBody, System.nanoTime(), callback);
    }


    private CardCompanionClient() {
    }

    public static boolean supported() {
        Minecraft client = Minecraft.getInstance();
        return CardPayloads.CHANNEL != null && client != null && client.player != null
                && client.getConnection() != null && client.getConnection().getConnection() != null
                && CardPayloads.CHANNEL.isRemotePresent(client.getConnection().getConnection());
    }

    private static boolean shouldDisplayConverted(ConvertedSharePairing.Share share) {
        var client = Minecraft.getInstance();
        var config = com.atom.chat.config.AtomChatConfig.get();
        return !config.enabled || (client.getChatStatus().isChatAllowed(client.isLocalServer())
                && !client.isBlocked(share.author()) && !client.getPlayerSocialManager().isHidden(share.author())
                && !(config.hideBlockedMessages && com.atom.chat.chat.BlockList.isBlocked(share.name())));
    }

    public static void onCardBroadcast(CardPayloads.CardBroadcastPayload payload) {
        onCardBroadcast(payload, false);
    }

    public static void onConvertedBroadcast(CardPayloads.CardBroadcastPayload payload) {
        onCardBroadcast(payload, true);
    }

    private static void onCardBroadcast(CardPayloads.CardBroadcastPayload payload, boolean converted) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        UUID senderUuid = payload.senderUuid();
        String senderName = payload.senderName();
        boolean system = payload.system() && senderUuid == null;
        if (!system && (senderUuid == null || senderName == null || senderName.isBlank())) return;
        var visibility = client.options.chatVisibility().get();
        if (visibility == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN || !system && visibility != net.minecraft.world.entity.player.ChatVisiblity.FULL) return;
        boolean own = !system && client.player != null && senderUuid.equals(client.player.getUUID());
        if (!system && !converted) {
            if (!client.getChatStatus().isChatAllowed(client.isLocalServer()) || !own && client.isBlocked(senderUuid)) return;
            if (!own && com.atom.chat.config.AtomChatConfig.get().hideBlockedMessages
                    && com.atom.chat.chat.BlockList.isBlocked(senderName)) return;
        }

        ShareCard card = CardWireCodec.decodeCard(payload.cardBytes());
        if (card == null) return;

        if (card.template() == ShareCard.Template.ITEM && card.nativeHoverObject() == null) {
            HoverEvent hover = BuiltInCardAdapter.resolveItemHover(card);
            if (hover != null) {
                card = card.withNativeHoverObject(hover);
            }
        }

        String pairedBody = card.customData().get(PlayerCardPairing.NATIVE_BODY);
        if (converted || pairedBody != null) {
            var config = com.atom.chat.config.AtomChatConfig.get();
            if (system || pairedBody == null || !config.enabled
                    || card.template() != ShareCard.Template.LOCATION && card.template() != ShareCard.Template.ITEM) return;
            ShareCard presentation = BuiltInCardAdapter.localizeComposerLocation(
                    ComposerLocationCard.fromPairedWaypoint(card, senderName));
            if (converted) {
                String fingerprint = card.customData().get(ConvertedSharePairing.FINGERPRINT);
                if (fingerprint == null) return;
                try {
                    var share = new ConvertedSharePairing.Share(senderUuid, senderName, pairedBody, presentation);
                    CONVERTED_SHARES.arm(UUID.fromString(fingerprint), share, System.nanoTime());
                } catch (IllegalArgumentException invalidFingerprint) {
                    // Invalid sidecar leaves the native message alone.
                }
            } else if (config.shareCardsEnabled && !card.customData().containsKey(ConvertedSharePairing.FINGERPRINT)) {
                NATIVE_SHARES.arm(senderUuid, pairedBody, presentation, System.nanoTime());
            }
            return;
        }
        Component fallback = null;
        if (!card.hasPrivateRedPacketPassword() && payload.fallbackJson() != null && !payload.fallbackJson().isBlank()) {
            try {
                fallback = Component.Serializer.fromJson(payload.fallbackJson());
            } catch (Throwable ignored) {
            }
        }
        if (fallback == null) {
            fallback = Component.literal(card.fallbackPlainText());
        }

        if (!com.atom.chat.config.AtomChatConfig.get().enabled) {
            client.gui.getChat().addMessage(fallback);
            return;
        }

        RichText senderRich = system ? RichText.empty() : RichText.literal(senderName == null ? "" : senderName);
        RichText contentRich = RichText.literal(card.fallbackPlainText());
        ChatMessage msg = new ChatMessage(
                fallback, own, system, null, null,
                senderUuid, system ? null : senderName, system ? null : senderName,
                card.fallbackPlainText(), senderRich, contentRich)
                .withCard(card);
        CardStateStore.received(card);
        ChatStore.get().add(msg);

        if (client.gui != null && client.gui.getChat() != null) {
            com.atom.chat.card.CardHudBridge.queueCardInHud(client, msg);
        }
    }

    public static void onCardStateUpdate(CardPayloads.CardStateUpdatePayload payload) {
        CardState state = CardWireCodec.decodeState(payload.stateBytes());
        if (state != null) {
            CardStateStore.updateFromServer(state);
        }
    }

    public static boolean shareFromClient(ShareCard card) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null || card == null) {
            return false;
        }
        String nativeText = PlayerCardPairing.nativeText(card);
        if (nativeText == null || nativeText.isBlank()) return false;
        if (supported()) {
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
                CardPayloads.CHANNEL.sendToServer(new CardPayloads.ClientCardSharePayload(encoded));
            }
        }
        client.getConnection().sendChat(nativeText);
        return true;
    }
}
