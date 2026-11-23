package com.atom.chat.net;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardWireCodec;
import com.atom.chat.api.card.MinimapPreviewCodec;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.card.MinimapTerrainSampler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

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

    private CardCompanionServer() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CardPayloads.ClientCardSharePayload.ID,
                CardCompanionServer::handleClientShare);
    }

    public static void onPlayerLogout(UUID uuid) {
        if (uuid != null) LAST_CLIENT_SHARE.remove(uuid);
    }

    public static void sendToAll(MinecraftServer server, ServerPlayerEntity sender,
                                 ShareCard card, Text vanillaFallback) {
        if (server == null || card == null) return;
        ShareCard prepared = enrichLocationPreview(server, card);
        Text fallback = vanillaFallback != null
                ? vanillaFallback
                : Text.literal(prepared.fallbackPlainText());
        // Mirror to dedicated server console log like broadcast
        try {
            server.sendMessage(fallback);
        } catch (Throwable ignored) {
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                sendToPlayer(player, sender, prepared, fallback);
            } catch (Throwable ignored) {
                // Never let one player's transport issue disrupt other recipients.
            }
        }
    }

    public static void sendToPlayer(ServerPlayerEntity target, ServerPlayerEntity sender,
                                    ShareCard card, Text vanillaFallback) {
        if (target == null || card == null) return;
        MinecraftServer server = target.getServer();
        ShareCard prepared = server != null ? enrichLocationPreview(server, card) : card;
        Text fallback = vanillaFallback != null
                ? vanillaFallback
                : Text.literal(prepared.fallbackPlainText());
        if (server != null && ServerPlayNetworking.canSend(target, CardPayloads.CardBroadcastPayload.ID)) {
            ShareCard signed = prepared.withServerAuthoritative(true);
            byte[] bytes = encodeSafely(signed);
            if (bytes == null) {
                target.sendMessage(fallback, false);
                return;
            }
            String fallbackJson = encodeFallbackJsonSafely(fallback, prepared.fallbackPlainText(), server);
            UUID senderUuid = sender != null ? sender.getUuid() : null;
            String senderName = sender != null ? sender.getGameProfile().getName() : "";
            boolean system = sender == null;
            ServerPlayNetworking.send(target, new CardPayloads.CardBroadcastPayload(
                    senderUuid, senderName, system, bytes, fallbackJson));
        } else {
            target.sendMessage(fallback, false);
        }
    }

    public static void updateStateForAll(MinecraftServer server, CardState state) {
        if (server == null || state == null) return;
        byte[] bytes = CardWireCodec.encodeState(state);
        if (bytes.length > CardWireCodec.MAX_STATE_BYTES) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, CardPayloads.CardStateUpdatePayload.ID)) {
                ServerPlayNetworking.send(player, new CardPayloads.CardStateUpdatePayload(bytes));
            }
        }
    }

    public static void updateStateFor(ServerPlayerEntity target, CardState state) {
        if (target == null || state == null) return;
        if (ServerPlayNetworking.canSend(target, CardPayloads.CardStateUpdatePayload.ID)) {
            byte[] bytes = CardWireCodec.encodeState(state);
            if (bytes.length <= CardWireCodec.MAX_STATE_BYTES) {
                ServerPlayNetworking.send(target, new CardPayloads.CardStateUpdatePayload(bytes));
            }
        }
    }

    /**
     * Handles C2S player share requests (only LOCATION and ITEM templates are accepted;
     * all labels/dimensions/items are re-validated and re-authored server-side).
     */
    private static void handleClientShare(CardPayloads.ClientCardSharePayload payload,
                                          ServerPlayNetworking.Context ctx) {
        MinecraftServer server = ctx.server();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayerEntity sender = ctx.player();
            if (sender == null) return;
            long now = System.currentTimeMillis();
            Long prev = LAST_CLIENT_SHARE.get(sender.getUuid());
            if (prev != null && now - prev < SHARE_COOLDOWN_MS) return;
            LAST_CLIENT_SHARE.put(sender.getUuid(), now);

            ShareCard incoming = CardWireCodec.decodeCard(payload.cardBytes());
            if (incoming == null) return;
            if (incoming.template() != ShareCard.Template.LOCATION
                    && incoming.template() != ShareCard.Template.ITEM) {
                return;
            }

            ShareCard sanitized = sanitizeClientCard(incoming, sender, server);
            if (sanitized == null) return;

            byte[] bytes = encodeSafely(sanitized.withServerAuthoritative(false));
            if (bytes == null) return;
            Text vanillaFallback = buildVanillaFallback(sender, sanitized);
            try {
                server.sendMessage(vanillaFallback);
            } catch (Throwable ignored) {
            }
            String fallbackJson = encodeFallbackJsonSafely(vanillaFallback, sanitized.fallbackPlainText(), server);

            for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
                try {
                    if (ServerPlayNetworking.canSend(target, CardPayloads.CardBroadcastPayload.ID)) {
                        ServerPlayNetworking.send(target, new CardPayloads.CardBroadcastPayload(
                                sender.getUuid(), sender.getGameProfile().getName(), false, bytes, fallbackJson));
                    } else {
                        target.sendMessage(vanillaFallback, false);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** Opportunistically attaches a terrain minimap preview from already-loaded server chunks (never force-loads). */
    private static ShareCard enrichLocationPreview(MinecraftServer server, ShareCard card) {
        if (server == null || card == null || card.template() != ShareCard.Template.LOCATION || card.location() == null) {
            return card;
        }
        if (card.customData().containsKey(MinimapPreviewCodec.DATA_KEY)) {
            return card;
        }
        try {
            ShareCard.LocationPayload loc = card.location();
            Identifier dimId = Identifier.tryParse(loc.dimension());
            if (dimId == null) return card;
            ServerWorld level = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
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

    private static String encodeFallbackJsonSafely(Text fallback, String plainText, MinecraftServer server) {
        try {
            String json = Text.Serialization.toJsonString(fallback, server.getRegistryManager());
            if (json.length() <= MAX_FALLBACK_JSON_CHARS) {
                return json;
            }
        } catch (Throwable ignored) {
        }
        String safePlain = BuiltInCardParsers.sanitizeLabel(plainText, 200);
        return Text.Serialization.toJsonString(Text.literal(safePlain), server.getRegistryManager());
    }

    private static ShareCard sanitizeClientCard(ShareCard card, ServerPlayerEntity sender, MinecraftServer server) {
        if (card.template() == ShareCard.Template.LOCATION && card.location() != null) {
            ShareCard.LocationPayload loc = card.location();
            String safeName = BuiltInCardParsers.sanitizeLabel(loc.name(), 32);
            if (safeName.isEmpty()) safeName = sender.getGameProfile().getName() + " 的位置";
            String safeDim = BuiltInCardParsers.normalizeDimension(loc.dimension());
            double x = Double.isFinite(loc.x()) ? Math.max(-30_000_000, Math.min(30_000_000, loc.x())) : sender.getX();
            double y = Double.isFinite(loc.y()) ? Math.max(-2048, Math.min(4096, loc.y())) : sender.getY();
            double z = Double.isFinite(loc.z()) ? Math.max(-30_000_000, Math.min(30_000_000, loc.z())) : sender.getZ();
            ShareCard built = BuiltInCardParsers.buildLocationCard(
                    safeName, x, y, z, safeDim,
                    ShareCard.dimensionAccent(safeDim), "位置分享", "");
            // Security: NEVER relay untrusted client-supplied minimap bytes (prevents arbitrary image broadcast),
            // and ONLY server-sample chunks within 64 blocks of sender's real position & dimension
            // (prevents remote enemy-base recon and player-presence oracle probing).
            String senderDim = sender.getWorld().getRegistryKey().getValue().toString();
            double horizDist = Math.hypot(x - sender.getX(), z - sender.getZ());
            if (safeDim.equalsIgnoreCase(senderDim) && horizDist <= 64.0) {
                return enrichLocationPreview(server, built);
            }
            return built;
        }
        if (card.template() == ShareCard.Template.ITEM) {
            // Re-author completely from the server's real main-hand item (prevents NBT/text spoofing).
            ItemStack held = sender.getMainHandStack();
            if (held.isEmpty()) return null;
            String name = BuiltInCardParsers.sanitizeLabel(held.getName().getString(), 48);
            String itemId = Registries.ITEM.getId(held.getItem()).toString();
            String snbt = "";
            try {
                var nbt = held.encode(server.getRegistryManager());
                if (nbt != null) {
                    String rawSnbt = nbt.toString();
                    if (rawSnbt.length() <= 16_000) {
                        snbt = rawSnbt;
                    }
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
        if (stack.hasEnchantments()) return 0xFF60A5FA;
        return 0xFF64748B;
    }

    private static String rarityLabelServer(ItemStack stack) {
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return "史诗";
        if (r == Rarity.RARE) return "稀有";
        if (r == Rarity.UNCOMMON) return "罕见";
        return stack.hasEnchantments() ? "附魔" : "普通";
    }

    private static String summarizeItemServer(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        try {
            ItemEnchantmentsComponent enchants = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
            for (var entry : enchants.getEnchantmentEntries()) {
                RegistryEntry<Enchantment> holder = entry.getKey();
                parts.add(Enchantment.getName(holder, entry.getIntValue()).getString());
                if (parts.size() >= 3) break;
            }
        } catch (Throwable ignored) {
        }
        if (parts.isEmpty()) {
            if (stack.isDamageable() && stack.getMaxDamage() > 0) {
                int remain = stack.getMaxDamage() - stack.getDamage();
                parts.add("耐久 " + remain + "/" + stack.getMaxDamage());
            } else {
                Identifier key = Registries.ITEM.getId(stack.getItem());
                parts.add(key.getNamespace());
            }
        }
        return String.join(" · ", parts);
    }

    private static Text buildVanillaFallback(ServerPlayerEntity sender, ShareCard card) {
        String name = sender.getGameProfile().getName();
        if (card.template() == ShareCard.Template.LOCATION && card.location() != null) {
            ShareCard.LocationPayload loc = card.location();
            return Text.literal("<" + name + "> ")
                    .append(Text.literal("[name:\"" + loc.name() + "\", x:" + loc.blockX()
                                    + ", y:" + loc.blockY() + ", z:" + loc.blockZ()
                                    + ", dim:" + loc.dimension() + "]")
                            .formatted(Formatting.GREEN));
        }
        if (card.template() == ShareCard.Template.ITEM) {
            ItemStack held = sender.getMainHandStack();
            if (!held.isEmpty()) {
                // Only attach SHOW_ITEM hover when SNBT is compact (avoids oversized vanilla packet)
                boolean safeHover = card.item() != null && !card.item().itemSnbt().isEmpty();
                Style st = Style.EMPTY.withColor(Formatting.LIGHT_PURPLE);
                if (safeHover) {
                    st = st.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(held.copy())));
                }
                return Text.literal("<" + name + "> ")
                        .append(Text.literal("[" + held.getName().getString()
                                        + (held.getCount() > 1 ? " ×" + held.getCount() : "") + "]")
                                .setStyle(st));
            }
        }
        return Text.literal("<" + name + "> " + card.fallbackPlainText());
    }
}
