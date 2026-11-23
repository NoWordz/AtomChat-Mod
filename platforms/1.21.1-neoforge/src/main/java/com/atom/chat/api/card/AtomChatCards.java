package com.atom.chat.api.card;

import com.atom.chat.net.CardCompanionClient;
import com.atom.chat.net.CardCompanionServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public entry point for mods integrating with AtomChat's universal Share Card API.
 *
 * <p>Typical server-side usage (e.g. Module-Shop RedPacket / Auction / Blind Vault):
 * <pre>{@code
 * ShareCard card = ShareCard.redPacket("rp:" + id)
 *     .title("恭喜发财，大吉大利")
 *     .subtitle("拼手气红包 · 点击领取")
 *     .source("模块商店 · 红包")
 *     .badge("剩余 10/10")
 *     .onClickCommand("/redpacket claim " + id)
 *     .build();
 * AtomChatCards.sendToAll(server, sender, card, vanillaFallbackComponent);
 * }</pre>
 *
 * <p>Players without AtomChat installed automatically receive {@code vanillaFallbackComponent}.
 */
public final class AtomChatCards {

    /** Semantic API version for optional-dependency callers. */
    public static final int API_VERSION = 1;

    private AtomChatCards() {
    }

    /** Broadcasts a share card to all online players, falling back to vanilla rich text for non-AtomChat clients. */
    public static void sendToAll(MinecraftServer server, ServerPlayer sender,
                                 ShareCard card, Component vanillaFallback) {
        CardCompanionServer.sendToAll(server, sender, card, vanillaFallback);
    }

    /** Sends a share card to one player, falling back to vanilla rich text if the player does not run AtomChat. */
    public static void sendToPlayer(ServerPlayer target, ServerPlayer sender,
                                    ShareCard card, Component vanillaFallback) {
        CardCompanionServer.sendToPlayer(target, sender, card, vanillaFallback);
    }

    /** Updates an existing card's live visual state (e.g. claimed / finished / expired) for all online AtomChat clients. */
    public static void updateStateForAll(MinecraftServer server, CardState state) {
        CardCompanionServer.updateStateForAll(server, state);
    }

    /** Updates an existing card's live visual state for one player (e.g. dimming a red packet after that player claims it). */
    public static void updateStateFor(ServerPlayer target, CardState state) {
        CardCompanionServer.updateStateFor(target, state);
    }

    /** Client-side helper to share a player-initiated location or held-item card. */
    public static boolean shareFromClient(ShareCard card) {
        return CardCompanionClient.shareFromClient(card);
    }

    /**
     * Convenience reflection-friendly helper for soft-dependency mods (like Module-Shop-RedPacket)
     * so they can send a Red Packet card with a single static method call without referencing
     * nested builder classes if desired.
     */
    public static void sendRedPacket(MinecraftServer server, ServerPlayer sender,
                                     String packetId, String greeting, String statusText,
                                     boolean lucky, boolean hasPassword, String password,
                                     int claimedCount, int totalCount, String currency,
                                     String clickCommand, boolean suggestInsteadOfRun,
                                     Component vanillaFallback) {
        sendRedPacket(server, sender, packetId, greeting, statusText, lucky, hasPassword, password,
                claimedCount, totalCount, currency, clickCommand, suggestInsteadOfRun, null, vanillaFallback);
    }

    /** Overload allowing callers (e.g. Module-Shop with custom brand name) to specify the footer source label. */
    public static void sendRedPacket(MinecraftServer server, ServerPlayer sender,
                                     String packetId, String greeting, String statusText,
                                     boolean lucky, boolean hasPassword, String password,
                                     int claimedCount, int totalCount, String currency,
                                     String clickCommand, boolean suggestInsteadOfRun,
                                     String sourceLabel, Component vanillaFallback) {
        String cardId = "rp:" + packetId;
        String effectiveSource = (sourceLabel == null || sourceLabel.isBlank()) ? "模块商店 · 红包" : sourceLabel;
        ShareCard.Builder b = ShareCard.redPacket(cardId)
                .title(greeting)
                .subtitle(statusText)
                .source(effectiveSource)
                .badge("剩余 " + Math.max(0, totalCount - claimedCount) + "/" + totalCount)
                .redPacketPayload(new ShareCard.RedPacketPayload(
                        packetId, greeting, statusText, lucky, hasPassword,
                        password == null ? "" : password, claimedCount, totalCount, currency, false))
                .hudTag("[红包]")
                .hudSummary(greeting)
                .fallbackPlainText("[红包] " + greeting + " (" + statusText + ")");
        if (suggestInsteadOfRun) {
            b.onClickSuggest(clickCommand);
        } else {
            b.onClickCommand(clickCommand);
        }
        sendToAll(server, sender, b.build(), vanillaFallback);
    }

    /**
     * Reflection-friendly live state updater for Red Packets (single player or whole server).
     */
    public static void updateRedPacketState(MinecraftServer server, ServerPlayer targetOrNull,
                                            String packetId, boolean dimmed, String statusText,
                                            String badgeText, String overrideClickCommand) {
        CardState.Builder b = new CardState.Builder("rp:" + packetId, dimmed)
                .statusText(statusText)
                .badgeText(badgeText);
        if (overrideClickCommand != null && !overrideClickCommand.isBlank()) {
            b.onClickCommand(overrideClickCommand);
        }
        CardState state = b.build();
        if (targetOrNull != null) {
            updateStateFor(targetOrNull, state);
        } else if (server != null) {
            updateStateForAll(server, state);
        }
    }
}
