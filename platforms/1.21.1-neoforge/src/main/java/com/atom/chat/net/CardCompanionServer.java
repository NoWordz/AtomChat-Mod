package com.atom.chat.net;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.MinimapPreviewCodec;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.card.MinimapTerrainSampler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side card broadcaster with automatic vanilla-client fallback, packet-size
 * safeguards (e.g. heavy Shulker Box NBT), and safe C2S player-share sanitization.
 */
public final class CardCompanionServer {
    private static final long SHARE_COOLDOWN_MS = 1_500L;
    private static final int MAX_FALLBACK_JSON_CHARS = 15_500;
    private static final Map<UUID, Long> LAST_CLIENT_SHARE = new ConcurrentHashMap<>();
    private static final PlayerCardPairing<ServerPlayer> PLAYER_SHARES = new PlayerCardPairing<>();

    private CardCompanionServer() {
    }

    public static void onPlayerLogout(UUID uuid) {
        if (uuid == null) return;
        LAST_CLIENT_SHARE.remove(uuid);
        PLAYER_SHARES.discard(player -> uuid.equals(player.getUUID()));
    }

    public static void sendToAll(MinecraftServer server, ServerPlayer sender,
                                 ShareCard card, Component vanillaFallback) {
        if (server == null || card == null) return;
        ShareCard prepared = enrichLocationPreview(server, card.hidePrivateRedPacketPassword());
        Component fallback = vanillaFallback != null && !prepared.hasPrivateRedPacketPassword()
                ? vanillaFallback
                : Component.literal(prepared.fallbackPlainText());
        // Mirror to dedicated server console log like broadcastSystemMessage
        try {
            server.sendSystemMessage(fallback);
        } catch (Throwable ignored) {
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                sendToPlayer(player, sender, prepared, fallback);
            } catch (Throwable ignored) {
                // Never let one player's transport issue disrupt other recipients.
            }
        }
    }

    public static void sendToPlayer(ServerPlayer target, ServerPlayer sender,
                                    ShareCard card, Component vanillaFallback) {
        if (target == null || card == null || !acceptsCard(target, sender == null)) return;
        ShareCard prepared = target.server != null ? enrichLocationPreview(target.server, card.hidePrivateRedPacketPassword()) : card.hidePrivateRedPacketPassword();
        Component fallback = vanillaFallback != null && !prepared.hasPrivateRedPacketPassword()
                ? vanillaFallback
                : Component.literal(prepared.fallbackPlainText());
        if (target.connection != null && target.connection.hasChannel(CardPayloads.CardBroadcastPayload.TYPE)) {
            ShareCard signed = prepared.withServerAuthoritative(true);
            byte[] bytes = encodeSafely(signed);
            if (bytes == null) {
                if (sender == null) target.sendSystemMessage(fallback);
                return;
            }
            String fallbackJson = encodeFallbackJsonSafely(fallback, prepared.fallbackPlainText(), target.server);
            UUID senderUuid = sender != null ? sender.getUUID() : null;
            String senderName = sender != null ? sender.getGameProfile().getName() : "";
            boolean system = sender == null;
            PacketDistributor.sendToPlayer(target, new CardPayloads.CardBroadcastPayload(
                    senderUuid, senderName, system, bytes, fallbackJson));
            CardAudience.delivered(target.server, target.getUUID(), prepared, system);
        } else {
            if (sender == null) target.sendSystemMessage(fallback);
        }
    }

    public static void updateStateForAll(MinecraftServer server, CardState state) {
        if (server == null || state == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) updateStateFor(player, state);
    }

    public static void updateStateFor(ServerPlayer target, CardState state) {
        if (target == null || state == null || !(target.connection != null && target.connection.hasChannel(CardPayloads.CardStateUpdatePayload.TYPE))) return;
        var delivery = CardAudience.find(target.server, target.getUUID(), state.cardId());
        if (delivery == null || !acceptsCard(target, delivery.system())) return;
        byte[] bytes = CardWireCodec.encodeState(delivery.applyPrivacy(state));
        if (bytes.length > CardWireCodec.MAX_STATE_BYTES) return;
        var payload = new CardPayloads.CardStateUpdatePayload(bytes);
        PacketDistributor.sendToPlayer(target, payload);
    }

    /**
     * Handles C2S player share requests (only LOCATION and ITEM templates are accepted;
     * all labels/dimensions/items are re-validated and re-authored server-side).
     */
    public static void handleClientShare(CardPayloads.ClientCardSharePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender) || sender.server == null) return;
            long now = System.currentTimeMillis();
            Long prev = LAST_CLIENT_SHARE.get(sender.getUUID());
            if (prev != null && now - prev < SHARE_COOLDOWN_MS) return;
            LAST_CLIENT_SHARE.put(sender.getUUID(), now);

            ShareCard incoming = CardWireCodec.decodeCard(payload.cardBytes());
            if (incoming == null) return;
            if (incoming.template() != ShareCard.Template.LOCATION
                    && incoming.template() != ShareCard.Template.ITEM) {
                return;
            }

            ShareCard sanitized = sanitizeClientCard(incoming, sender);
            if (sanitized == null) return;

            String nativeBody = PlayerCardPairing.nativeText(incoming);
            if (nativeBody != null) PLAYER_SHARES.arm(sender, nativeBody, sanitized, System.nanoTime());
        });
    }

    /** Opportunistically attaches a terrain minimap preview from already-loaded server chunks (never force-loads). */
    public record PlayerShareDelivery(UUID sender, String name, String body, ShareCard card,
                                      boolean unfiltered) {}

    private record OutboundShare(ServerPlayer target, Component convertedLine, PlayerShareDelivery share) {}
    private static final ThreadLocal<OutboundShare> OUTBOUND_SHARE = new ThreadLocal<>();

    public static void bindAcceptedPlayerShare(ServerPlayer sender, PlayerChatMessage nativeMessage) {
        if (sender == null || !sender.getUUID().equals(nativeMessage.sender())) return;
        PLAYER_SHARES.bindAccepted(sender, nativeMessage.signedBody(), nativeMessage.signedContent(), System.nanoTime());
    }

    public static PlayerShareDelivery claimPlayerShare(ServerPlayer sender, net.minecraft.network.chat.PlayerChatMessage nativeMessage) {
        if (sender == null || !sender.getUUID().equals(nativeMessage.sender())) return null;
        String body = nativeMessage.signedContent();
        ShareCard card = PLAYER_SHARES.takeAccepted(sender, nativeMessage.signedBody(), System.nanoTime());
        if (card == null) return null;
        return new PlayerShareDelivery(sender.getUUID(), sender.getGameProfile().getName(), body, card, nativeMessage.filterMask().isEmpty());
    }

    static void sendPlayerShare(ServerPlayer target, boolean filtered, Component convertedLine,
                                PlayerShareDelivery share, Runnable send) {
        if (!acceptsCard(target, false) || filtered && !share.unfiltered() || (target.connection == null || !target.connection.hasChannel(CardPayloads.CardBroadcastPayload.TYPE))) {
            send.run();
            return;
        }
        var previous = OUTBOUND_SHARE.get();
        OUTBOUND_SHARE.set(new OutboundShare(target, convertedLine, share));
        try {
            send.run();
        } finally {
            if (previous == null) OUTBOUND_SHARE.remove();
            else OUTBOUND_SHARE.set(previous);
        }
    }

    /** Called immediately before the actual packet, including a system chat replacement. */
    public static void onOutboundPlayerShare(ServerPlayer target, net.minecraft.network.protocol.Packet<?> packet) {
        var delivery = OUTBOUND_SHARE.get();
        if (delivery == null || delivery.target() != target) return;
        var share = delivery.share();
        UUID convertedFingerprint = null;
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket player) {
            if (!share.sender().equals(player.sender()) || !share.body().equals(player.body().content())) return;
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket system) {
            if (system.overlay() || !delivery.convertedLine().equals(system.content())) return;
            convertedFingerprint = ReactionPacketIdentity.of(packet, target.server.registryAccess());
            if (convertedFingerprint == null || !(target.connection.hasChannel(CardPayloads.ConvertedCardPayload.TYPE))) return;
        } else {
            return;
        }
        OUTBOUND_SHARE.remove();
        try {
            var metadata = share.card().toBuilder().serverAuthoritative(false)
                    .putData(PlayerCardPairing.NATIVE_BODY, share.body());
            if (convertedFingerprint != null) metadata.putData(ConvertedSharePairing.FINGERPRINT, convertedFingerprint.toString());
            byte[] bytes = encodeSafely(metadata.build());
            if (bytes == null) return;
            var payload = new CardPayloads.CardBroadcastPayload(share.sender(), share.name(), false, bytes, "");
            if (convertedFingerprint == null) {
                PacketDistributor.sendToPlayer(target, payload);
            } else {
                PacketDistributor.sendToPlayer(target, new CardPayloads.ConvertedCardPayload(payload));
            }
            CardAudience.delivered(target.server, target.getUUID(), share.card(), false);
        } catch (RuntimeException failure) {
            com.atom.chat.AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message("card",
                    "Card delivery failed; forwarding native chat"), failure);
        }
    }

    private static boolean acceptsCard(ServerPlayer target, boolean system) {
        var visibility = target.getChatVisibility();
        return system ? visibility != net.minecraft.world.entity.player.ChatVisiblity.HIDDEN : visibility == net.minecraft.world.entity.player.ChatVisiblity.FULL;
    }

    private static ShareCard enrichLocationPreview(MinecraftServer server, ShareCard card) {
        if (server == null || card == null || card.template() != ShareCard.Template.LOCATION || card.location() == null) {
            return card;
        }
        if (card.customData().containsKey(MinimapPreviewCodec.DATA_KEY)) {
            return card;
        }
        try {
            ShareCard.LocationPayload loc = card.location();
            ResourceLocation dimId = ResourceLocation.tryParse(loc.dimension());
            if (dimId == null) return card;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
            if (level == null) return card;
            String preview = MinimapTerrainSampler.sampleFromLevel(
                    level, loc.blockX(), loc.blockY(), loc.blockZ(), loc.dimension());
            if (preview != null) {
                return card.toBuilder().putData(MinimapPreviewCodec.DATA_KEY, preview).build();
            }
        } catch (Throwable ignored) {
        }
        return card;
    }

    /** Encodes card within MAX_CARD_BYTES, stripping heavy item SNBT first if oversized (e.g. full Shulker Box). */
    private static byte[] encodeSafely(ShareCard card) {
        byte[] bytes = CardWireCodec.encodeCard(card);
        if (bytes.length <= CardWireCodec.MAX_CARD_BYTES) {
            return bytes;
        }
        if (card.item() != null && !card.item().itemSnbt().isEmpty()) {
            ShareCard.ItemPayload slim = new ShareCard.ItemPayload(
                    card.item().itemId(), "", card.item().displayName(),
                    card.item().summary(), card.item().rarityLabel(),
                    card.item().accentColor(), card.item().count());
            bytes = CardWireCodec.encodeCard(card.toBuilder().itemPayload(slim).build());
            if (bytes.length <= CardWireCodec.MAX_CARD_BYTES) {
                return bytes;
            }
        }
        return null;
    }

    private static String encodeFallbackJsonSafely(Component fallback, String plainText, MinecraftServer server) {
        try {
            String json = Component.Serializer.toJson(fallback, server.registryAccess());
            if (json.length() <= MAX_FALLBACK_JSON_CHARS) {
                return json;
            }
        } catch (Throwable ignored) {
        }
        String safePlain = BuiltInCardParsers.sanitizeLabel(plainText, 200);
        return Component.Serializer.toJson(Component.literal(safePlain), server.registryAccess());
    }

    private static ShareCard sanitizeClientCard(ShareCard card, ServerPlayer sender) {
        if (card.template() == ShareCard.Template.LOCATION && card.location() != null) {
            ShareCard.LocationPayload loc = card.location();
            String safeName = BuiltInCardParsers.sanitizeLabel(loc.name(), 32);
            if (safeName.isEmpty()) safeName = sender.getGameProfile().getName() + " 的位置";
            String safeDim = BuiltInCardParsers.normalizeDimension(loc.dimension());
            double x = Double.isFinite(loc.x()) ? Math.max(-30_000_000, Math.min(30_000_000, loc.x())) : sender.getX();
            double y = Double.isFinite(loc.y()) ? Math.max(-2048, Math.min(4096, loc.y())) : sender.getY();
            double z = Double.isFinite(loc.z()) ? Math.max(-30_000_000, Math.min(30_000_000, loc.z())) : sender.getZ();
            String author = sender.getGameProfile().getName();
            ShareCard built = ComposerLocationCard.isComposerLocation(card)
                    || ComposerLocationCard.isLegacyComposerLocation(card, author)
                    ? ComposerLocationCard.create(author, x, y, z, safeDim)
                    : BuiltInCardParsers.buildLocationCard(safeName, x, y, z, safeDim,
                            ShareCard.dimensionAccent(safeDim), "位置分享", "");
            // Security: NEVER relay untrusted client-supplied minimap bytes (prevents arbitrary image broadcast),
            // and ONLY server-sample chunks within 64 blocks of sender's real position & dimension
            // (prevents remote enemy-base recon and player-presence oracle probing).
            String senderDim = sender.level().dimension().location().toString();
            double horizDist = Math.hypot(x - sender.getX(), z - sender.getZ());
            if (safeDim.equalsIgnoreCase(senderDim) && horizDist <= 64.0) {
                return enrichLocationPreview(sender.server, built);
            }
            return built;
        }
        if (card.template() == ShareCard.Template.ITEM) {
            // Re-author completely from the server's real main-hand item (prevents NBT/text spoofing).
            ItemStack held = sender.getMainHandItem();
            if (held.isEmpty()) return null;
            String name = BuiltInCardParsers.sanitizeLabel(held.getHoverName().getString(), 48);
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            String snbt = "";
            try {
                String rawSnbt = held.save(sender.server.registryAccess()).toString();
                if (rawSnbt.length() <= 16_000) {
                    snbt = rawSnbt;
                }
            } catch (Throwable ignored) {
            }
            String summary = summarizeItemServer(held);
            String rarity = rarityLabelServer(held);
            int accent = rarityAccentServer(held);
            ShareCard.ItemPayload payload = new ShareCard.ItemPayload(
                    itemId, snbt, name, summary, rarity, accent, held.getCount());
            return ShareCard.item(name)
                    .title(name)
                    .subtitle(summary)
                    .source("装备分享")
                    .badge(rarity)
                    .accentColor(accent)
                    .itemPayload(payload)
                    .hudTag("[装备]")
                    .hudSummary(name + (held.getCount() > 1 ? " ×" + held.getCount() : ""))
                    .fallbackPlainText("[装备] " + name + (held.getCount() > 1 ? " ×" + held.getCount() : ""))
                    .build();
        }
        return null;
    }

    private static int rarityAccentServer(ItemStack stack) {
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return 0xFFA855F7;
        if (r == Rarity.RARE) return 0xFF06B6D4;
        if (r == Rarity.UNCOMMON) return 0xFFEAB308;
        if (stack.isEnchanted()) return 0xFF60A5FA;
        return 0xFF64748B;
    }

    private static String rarityLabelServer(ItemStack stack) {
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return "史诗";
        if (r == Rarity.RARE) return "稀有";
        if (r == Rarity.UNCOMMON) return "罕见";
        return stack.isEnchanted() ? "附魔" : "普通";
    }

    private static String summarizeItemServer(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        try {
            ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var entry : enchants.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                parts.add(Enchantment.getFullname(holder, entry.getIntValue()).getString());
                if (parts.size() >= 3) break;
            }
        } catch (Throwable ignored) {
        }
        if (parts.isEmpty()) {
            if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                int remain = stack.getMaxDamage() - stack.getDamageValue();
                parts.add("耐久 " + remain + "/" + stack.getMaxDamage());
            } else {
                ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                parts.add(key.getNamespace());
            }
        }
        return String.join(" · ", parts);
    }

    private static Component buildVanillaFallback(ServerPlayer sender, ShareCard card) {
        String name = sender.getGameProfile().getName();
        if (card.template() == ShareCard.Template.LOCATION && card.location() != null) {
            ShareCard.LocationPayload loc = card.location();
            return Component.literal("<" + name + "> ")
                    .append(Component.literal("[name:\"" + loc.name() + "\", x:" + loc.blockX()
                                    + ", y:" + loc.blockY() + ", z:" + loc.blockZ()
                                    + ", dim:" + loc.dimension() + "]")
                            .withStyle(ChatFormatting.GREEN));
        }
        if (card.template() == ShareCard.Template.ITEM) {
            ItemStack held = sender.getMainHandItem();
            if (!held.isEmpty()) {
                // Only attach SHOW_ITEM hover when SNBT is compact (avoids oversized vanilla packet)
                boolean safeHover = card.item() != null && !card.item().itemSnbt().isEmpty();
                Style st = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);
                if (safeHover) {
                    st = st.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(held.copy())));
                }
                return Component.literal("<" + name + "> ")
                        .append(Component.literal("[" + held.getHoverName().getString()
                                        + (held.getCount() > 1 ? " ×" + held.getCount() : "") + "]")
                                .setStyle(st));
            }
        }
        return Component.literal("<" + name + "> " + card.fallbackPlainText());
    }
}
