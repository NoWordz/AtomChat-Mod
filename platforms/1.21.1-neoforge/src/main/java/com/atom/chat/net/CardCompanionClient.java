package com.atom.chat.net;

import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardStateStore;
import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.card.BuiltInCardAdapter;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.IdentityEvidence;
import com.atom.chat.config.AtomChatConfig;
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
    private static final ClientShareSubmission SHARES = new ClientShareSubmission();

    private static final ConvertedSharePairing CONVERTED_SHARES = new ConvertedSharePairing();

    public static void resetNativeShares() {
        SHARES.clear();
        CONVERTED_SHARES.clear();
    }

    public static void deliverConvertedShare(UUID fingerprint, Object component, Runnable delivery) {
        long now = System.nanoTime();
        CONVERTED_SHARES.deliver(fingerprint, component, now, paired -> {
            if (paired == null) return component instanceof Component line ? localConvertedShare(line, now) : null;
            var local = SHARES.takeLocal(paired.author(), paired.body(), now);
            return local == null ? paired : paired.correlate(local.receipt());
        }, () -> {
            var share = CONVERTED_SHARES.current();
            if (share == null || shouldDisplayConverted(share)) delivery.run();
        });
    }

    private static ConvertedSharePairing.Share localConvertedShare(Component component, long now) {
        if (!SHARES.hasLocalShares(now)) return null;
        var config = AtomChatConfig.get();
        if (!config.enabled || !config.shareCardsEnabled) return null;
        // NCR's decorated player message retains the sender's entity UUID. A name or plain text alone cannot claim the echo.
        var meta = ChatPipeline.structuredPlayerLine(component);
        if (meta == null || meta.evidence() != IdentityEvidence.ENTITY) return null;
        var local = SHARES.takeLocal(meta.senderUuid(), meta.contentText(), now);
        if (local == null) return null;
        String profile = meta.profileName() == null ? meta.senderName() : meta.profileName();
        return new ConvertedSharePairing.Share(meta.senderUuid(), profile, meta.contentText(),
                BuiltInCardAdapter.localizeComposerLocation(local.card()), local.receipt());
    }

    public static ConvertedSharePairing.Share takeConvertedShare(Object component, long receipt) {
        var share = AtomChatConfig.get().shareCardsEnabled
                ? CONVERTED_SHARES.takeVisible(component) : null;
        if (share != null) ClientShareSubmission.correlate(share.sendReceipt(), receipt);
        return share;
    }

    public static void discardConvertedShare() {
        CONVERTED_SHARES.discardPending();
    }

    public static ShareCard takeNativeShare(UUID sender, String originalBody, String visibleBody, long receipt) {
        if (sender == null || originalBody == null || !AtomChatConfig.get().shareCardsEnabled) return null;
        ShareCard card = SHARES.takeNative(sender, originalBody, visibleBody, receipt);
        if (card != null) {
            card = BuiltInCardAdapter.localizeComposerLocation(card);
            CardStateStore.received(card);
        }
        return card;
    }

    public static java.util.function.BooleanSupplier deferNativeShare(
            UUID sender, String originalBody, boolean unfiltered, java.util.function.BooleanSupplier callback) {
        return SHARES.deferNative(sender, originalBody, unfiltered, System.nanoTime(), callback);
    }


    private CardCompanionClient() {
    }

    public static boolean supported() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null && client.getConnection() != null
                && client.getConnection().hasChannel(CardPayloads.ClientCardSharePayload.TYPE);
    }

    private static boolean shouldDisplayConverted(ConvertedSharePairing.Share share) {
        var client = Minecraft.getInstance();
        var config = AtomChatConfig.get();
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
            if (!own && AtomChatConfig.get().hideBlockedMessages
                    && com.atom.chat.chat.BlockList.isBlocked(senderName)) return;
        }

        ShareCard card = CardWireCodec.decodeRemoteCard(payload.cardBytes());
        if (card == null) return;

        // Rehydrate live ItemStack hover for ITEM cards so native tooltips work immediately.
        if (card.template() == ShareCard.Template.ITEM && card.nativeHoverObject() == null) {
            HoverEvent hover = BuiltInCardAdapter.resolveItemHover(card);
            if (hover != null) {
                card = card.withNativeHoverObject(hover);
            }
        }

        String pairedBody = card.customData().get(PlayerCardPairing.NATIVE_BODY);
        if (converted || pairedBody != null) {
            var config = AtomChatConfig.get();
            if (system || pairedBody == null || !config.enabled
                    || card.template() != ShareCard.Template.LOCATION && card.template() != ShareCard.Template.ITEM) return;
            ShareCard presentation = ComposerLocationCard.fromPairedWaypoint(card, senderName);
            if (converted) {
                String fingerprint = card.customData().get(ConvertedSharePairing.FINGERPRINT);
                if (fingerprint == null) return;
                try {
                    var share = new ConvertedSharePairing.Share(senderUuid, senderName, pairedBody,
                            BuiltInCardAdapter.localizeComposerLocation(presentation));
                    CONVERTED_SHARES.arm(UUID.fromString(fingerprint), share, System.nanoTime());
                } catch (IllegalArgumentException invalidFingerprint) {
                    // Invalid sidecar leaves the native message alone.
                }
            } else if (config.shareCardsEnabled && !card.customData().containsKey(ConvertedSharePairing.FINGERPRINT)) {
                SHARES.armNative(senderUuid, pairedBody, presentation, System.nanoTime());
            }
            return;
        }
        Component fallback = null;
        if (!card.hasPrivateRedPacketPassword() && payload.fallbackJson() != null && !payload.fallbackJson().isBlank()) {
            try {
                fallback = Component.Serializer.fromJson(payload.fallbackJson(), client.level.registryAccess());
            } catch (Throwable ignored) {
            }
        }
        if (fallback == null) {
            fallback = Component.literal(card.fallbackPlainText());
        }

        if (!AtomChatConfig.get().enabled) {
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

        // Also surface a clean one-line entry into vanilla ChatComponent / gameplay HUD queue
        // without re-capturing a duplicate row in ChatStore.
        if (client.gui != null && client.gui.getChat() != null) {
            com.atom.chat.card.CardHudBridge.queueCardInHud(client, msg);
        }
    }

    public static void onCardStateUpdate(CardPayloads.CardStateUpdatePayload payload) {
        CardState state = CardWireCodec.decodeRemoteState(payload.stateBytes());
        if (state != null) {
            CardStateStore.updateFromServer(state);
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
        boolean companion = supported();
        boolean paired = companion && client.getConnection().hasChannel(CardPayloads.ConvertedCardPayload.TYPE);
        var config = AtomChatConfig.get();
        UUID localAuthor = config.enabled && config.shareCardsEnabled ? client.player.getUUID() : null;
        return SHARES.send(card, paired, localAuthor,
                bytes -> PacketDistributor.sendToServer(new CardPayloads.ClientCardSharePayload(bytes)),
                client.getConnection()::sendChat);
    }
}
