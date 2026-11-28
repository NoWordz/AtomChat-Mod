package com.atom.chat.net;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.MinimapPreviewCodec;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.card.MinimapTerrainSampler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.20.1 server-side card broadcaster with automatic vanilla-client fallback,
 * packet-size safeguards and safe C2S player-share sanitization.
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

    private static boolean hasChannel(ServerPlayer target) {
        return CardPayloads.CHANNEL != null && target != null
                && target.connection != null && target.connection.connection != null
                && CardPayloads.CHANNEL.isRemotePresent(target.connection.connection);
    }

    public static void sendToAll(MinecraftServer server, ServerPlayer sender,
                                 ShareCard card, Component vanillaFallback) {
        if (server == null || card == null) return;
        ShareCard prepared = enrichLocationPreview(server, card.hidePrivateRedPacketPassword());
        Component fallback = vanillaFallback != null && !prepared.hasPrivateRedPacketPassword()
                ? vanillaFallback
                : Component.literal(prepared.fallbackPlainText());
        try {
            server.sendSystemMessage(fallback);
        } catch (Throwable ignored) {
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                sendToPlayer(player, sender, prepared, fallback);
            } catch (Throwable ignored) {
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
        if (hasChannel(target)) {
            ShareCard signed = prepared.withServerAuthoritative(true);
            byte[] bytes = encodeSafely(signed);
            if (bytes == null) {
                if (sender == null) target.sendSystemMessage(fallback);
                return;
            }
            String fallbackJson = encodeFallbackJsonSafely(fallback, prepared.fallbackPlainText());
            UUID senderUuid = sender != null ? sender.getUUID() : null;
            String senderName = sender != null ? sender.getGameProfile().getName() : "";
            boolean system = sender == null;
            CardPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                    new CardPayloads.CardBroadcastPayload(senderUuid, senderName, system, bytes, fallbackJson));
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
        if (target == null || state == null || !(hasChannel(target))) return;
        var delivery = CardAudience.find(target.server, target.getUUID(), state.cardId());
        if (delivery == null || !acceptsCard(target, delivery.system())) return;
        byte[] bytes = CardWireCodec.encodeState(delivery.applyPrivacy(state));
        if (bytes.length > CardWireCodec.MAX_STATE_BYTES) return;
        var payload = new CardPayloads.CardStateUpdatePayload(bytes);
        CardPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), payload);
    }

    /** Handles C2S player share requests (runs on server main thread via consumerMainThread). */
    public static void handleClientShare(CardPayloads.ClientCardSharePayload payload, NetworkEvent.Context ctx) {
        try {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || sender.server == null) return;
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
        } finally {
            ctx.setPacketHandled(true);
        }
    }

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
        if (!acceptsCard(target, false) || filtered && !share.unfiltered() || !hasChannel(target)) {
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
            if (convertedFingerprint == null || !(CardPayloads.CONVERTED != null && CardPayloads.CONVERTED.isRemotePresent(target.connection.connection))) return;
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
                CardPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), payload);
            } else {
                CardPayloads.CONVERTED.send(PacketDistributor.PLAYER.with(() -> target), payload);
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

    private static String encodeFallbackJsonSafely(Component fallback, String plainText) {
        try {
            String json = Component.Serializer.toJson(fallback);
            if (json.length() <= MAX_FALLBACK_JSON_CHARS) {
                return json;
            }
        } catch (Throwable ignored) {
        }
        String safePlain = BuiltInCardParsers.sanitizeLabel(plainText, 200);
        return Component.Serializer.toJson(Component.literal(safePlain));
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
            String senderDim = sender.level().dimension().location().toString();
            double horizDist = Math.hypot(x - sender.getX(), z - sender.getZ());
            if (safeDim.equalsIgnoreCase(senderDim) && horizDist <= 64.0) {
                return enrichLocationPreview(sender.server, built);
            }
            return built;
        }
        if (card.template() == ShareCard.Template.ITEM) {
            ItemStack held = sender.getMainHandItem();
            if (held.isEmpty()) return null;
            String name = BuiltInCardParsers.sanitizeLabel(held.getHoverName().getString(), 48);
            String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            String snbt = "";
            try {
                CompoundTag tag = new CompoundTag();
                held.save(tag);
                String rawSnbt = tag.toString();
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
            Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                parts.add(entry.getKey().getFullname(entry.getValue()).getString());
                if (parts.size() >= 3) break;
            }
        } catch (Throwable ignored) {
        }
        if (parts.isEmpty()) {
            if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                int remain = stack.getMaxDamage() - stack.getDamageValue();
                parts.add("耐久 " + remain + "/" + stack.getMaxDamage());
            } else {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
