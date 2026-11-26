package com.atom.chat.card;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.CardRegistry;
import com.atom.chat.api.card.CardRuleEngine;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardStateStore;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.UniversalCardMatcher;
import com.atom.chat.api.card.TeleportRequestSession;
import com.atom.chat.config.AtomChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mapping-aware adapter that converts common mod chat lines into rich {@link ShareCard}s
 * with zero upstream mod changes.
 *
 * <p>Uses version-safe reflection for 1.21.1 vs 1.20.1 item-stack serialization and
 * enchantment inspection so {@code layers/mapping/official} stays compatible with
 * both NeoForge 1.21.1 and Forge 1.20.1.
 */
public final class BuiltInCardAdapter {

    /** Internal client-only metadata key storing the inferred sharer/sender player name for adapted system cards. */
    public static final String CARD_SENDER_DATA_KEY = "_sender";

    private static final java.util.regex.Pattern RP_CLAIM_CMD =
            java.util.regex.Pattern.compile("(?i)^/?redpacket\\s+claim\\s+([a-z0-9_-]{1,24})$");
    private static final java.util.regex.Pattern RP_INFO_CMD =
            java.util.regex.Pattern.compile("(?i)^/?redpacket\\s+info\\s+([a-z0-9_-]{1,24})$");
    private static final java.util.regex.Pattern RP_GREETING =
            java.util.regex.Pattern.compile("「([^」]{1,40})」");
    private static final java.util.regex.Pattern RP_SUMMARY_COUNT =
            java.util.regex.Pattern.compile("共\\s*(\\d{1,5})\\s*个(?:.*?总额\\s*\\d+\\s*([^\\s\\[\\n]{1,12}))?");
    private static final java.util.regex.Pattern RP_SENDER_FALLBACK =
            java.util.regex.Pattern.compile("(?:\\[红包\\]\\s*)?([A-Za-z0-9_]{2,20})\\s*发了一个");
    private static final java.util.regex.Pattern RP_CLAIMED_AMOUNT =
            java.util.regex.Pattern.compile("获得\\s*(\\d+\\s*[^\\s\\[\\n]{1,12})");

    private static final Map<ShareCard, Optional<HoverEvent>> HOVER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<ComponentCardAdapter> COMPONENT_ADAPTERS = new CopyOnWriteArrayList<>();

    private BuiltInCardAdapter() {
    }

    /** Returns the inferred sender player name attached to a client-adapted card (or {@code null}). */
    public static String cardSenderOf(ShareCard card) {
        if (card == null) return null;
        String metaSender = card.customData().get(CARD_SENDER_DATA_KEY);
        if (metaSender != null && !metaSender.isBlank()) {
            return metaSender;
        }
        if (card.general() != null && !card.general().avatarPlayerName().isBlank()) {
            return card.general().avatarPlayerName();
        }
        return null;
    }

    /** Registers a mapping-aware third-party chat-to-card adapter (sorted by descending priority). */
    public static void registerComponentAdapter(ComponentCardAdapter adapter) {
        if (adapter == null) return;
        synchronized (COMPONENT_ADAPTERS) {
            COMPONENT_ADAPTERS.add(adapter);
            COMPONENT_ADAPTERS.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        }
    }

    public static void unregisterComponentAdapter(ComponentCardAdapter adapter) {
        if (adapter != null) {
            COMPONENT_ADAPTERS.remove(adapter);
        }
    }

    public static void resetSession() {
        TeleportRequestSession.reset();
        com.atom.chat.net.CardCompanionClient.resetNativeShares();
        HOVER_CACHE.clear();
        MinimapPreviewSampler.clearCache();
    }

    /**
     * Inspects an incoming chat {@link Component} and returns a {@link ShareCard} when
     * recognized, or {@code null} to keep normal text/image rendering. Never throws.
     */
    public static ShareCard adapt(Component component, String senderName, boolean system) {
        try {
            return adaptUnchecked(component, senderName, system);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isLegacyCredentialAnnouncement(Component component, String raw) {
        if (raw == null || component == null) return false;
        if (findTranslationKey(component, "msg.module_shop_redpacket.btn_password") != null) return true;
        String visible = raw.replaceAll("§.", "").strip();
        String folded = visible.toLowerCase(Locale.ROOT);
        boolean envelope = visible.startsWith("[红包]") || folded.startsWith("[red packet]") || findTranslationKey(component,
                "msg.module_shop_redpacket.sent_lucky", "msg.module_shop_redpacket.sent_even") != null;
        return envelope && (visible.contains("发送口令领取") || visible.contains("口令：") || visible.contains("口令:")
                || visible.contains("发了一个") && visible.contains("口令") || folded.contains("password:"));
    }

    private static ShareCard adaptUnchecked(Component component, String senderName, boolean system) {
        if (component == null) {
            return null;
        }
        String raw = component.getString();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        // Extract normalized platform-independent signals once for all compatibility layers.
        List<ClickEvent> rawClicks = collectClickEvents(component);
        if (isLegacyCredentialAnnouncement(component, raw)) {
            var protectedCard = adaptRedPacketAnnouncement(component, raw, rawClicks, Set.of());
            if (protectedCard != null) return protectedCard;
        }
        var cfg = AtomChatConfig.get();
        if (!cfg.shareCardsEnabled) return null;

        // 0. Observe TPA & RedPacket follow-up status messages ONLY for real system messages (prevents chat spoofing).
        if (system) {
            observeRedPacketFollowUp(component, raw, rawClicks);
        }

        List<UniversalCardMatcher.ClickActionSpec> clickSpecs = new ArrayList<>();
        collectClickActionSpecs(component, null, clickSpecs);
        List<String> translationKeys = new ArrayList<>();
        collectTranslationKeys(component, translationKeys);
        List<HoverEvent> itemHovers = new ArrayList<>();
        collectItemHovers(component, itemHovers);
        Set<String> nativeCmds = collectNativeCommandSet(rawClicks);

        UniversalCardMatcher.MessageSignals signals = new UniversalCardMatcher.MessageSignals(
                raw, senderName, system, translationKeys, clickSpecs, !itemHovers.isEmpty(), currentDimension());

        // Tier 1: Third-party mapping-aware ComponentCardAdapters (highest priority).
        for (ComponentCardAdapter adapter : COMPONENT_ADAPTERS) {
            try {
                ShareCard custom = adapter.adapt(component, signals);
                if (custom != null) {
                    return attachVerifiedNativeCommands(custom, nativeCmds);
                }
            } catch (Throwable ignored) {
            }
        }

        // Tier 2: Cross-loader SignalCardAdapters registered via CardRegistry.
        ShareCard signalCard = CardRegistry.adaptSignals(signals);
        if (signalCard != null) {
            return attachVerifiedNativeCommands(signalCard, nativeCmds);
        }

        // Tier 3: Server/modpack data-driven JSON rules (config/atomchat/card_rules.json).
        if (cfg.customCardRulesEnabled) {
            ShareCard ruleCard = CardRuleEngine.match(signals);
            if (ruleCard != null) {
                return attachVerifiedNativeCommands(ruleCard, nativeCmds);
            }
        }

        // Tier 4a: High-precision StellarRTP TPA requests (preserves exact requester extraction & follow-up dimming).
        if (cfg.autoConvertRequestCards) {
            ShareCard tpa = adaptTpaRequest(component, signals);
            if (tpa != null) {
                return tpa;
            }
        }

        // Tier 4a.5: Fallback Module-Shop RedPacket announcements (when server runs older RedPacket or lacks AtomChat).
        if (system) {
            ShareCard redPacket = adaptRedPacketAnnouncement(component, raw, rawClicks, nativeCmds);
            if (redPacket != null) {
                return redPacket;
            }
        }

        // Tier 4b: High-precision map waypoint shares (Xaero raw & localized ClickEvent / JourneyMap / VoxelMap / FTB Chunks).
        String firstCmd = firstCommand(rawClicks);
        if (cfg.autoConvertLocationCards) {
            String xaeroCmd = findXaeroCommand(rawClicks);
            ShareCard xaero = BuiltInCardParsers.parseXaeroWaypoint(
                    raw, !xaeroCmd.isBlank() ? xaeroCmd : firstCmd, currentDimension());
            if (xaero != null) {
                xaero = enrichXaeroMetadata(component, raw, xaero);
                return attachVerifiedNativeCommands(xaero, nativeCmds);
            }
            if (!system || !firstCmd.isBlank() || looksLikeMapSystemLine(raw)) {
                ShareCard bracket = BuiltInCardParsers.parseBracketWaypoint(raw, firstCmd, detectBracketMapSource());
                if (bracket != null) {
                    return attachVerifiedNativeCommands(
                            localizeComposerLocation(ComposerLocationCard.fromWaypoint(bracket, senderName)), nativeCmds);
                }
                ShareCard ftb = BuiltInCardParsers.parseFtbCoordinate(raw, currentDimension(), firstCmd);
                if (ftb != null) {
                    return attachVerifiedNativeCommands(ftb, nativeCmds);
                }
            }
        }

        // Tier 5a: Universal pair-button requests / invitations (Party, Guild, Trade, Duel, Friend, Claim, TPA).
        if (cfg.autoConvertRequestCards) {
            ShareCard pair = UniversalCardMatcher.matchPairRequest(signals);
            if (pair != null) {
                return pair;
            }
        }

        // Tier 5b: Universal single-item SHOW_ITEM shares (plus optional safe action button like Auction Buy).
        if (cfg.autoConvertItemLinksToCards && !itemHovers.isEmpty()) {
            ShareCard itemCard = adaptSingleItemShare(itemHovers, raw, system, clickSpecs, nativeCmds);
            if (itemCard != null) {
                return itemCard;
            }
        }

        // Tier 5c: Universal generic coordinate + teleport/waypoint command shares.
        if (cfg.autoConvertLocationCards) {
            ShareCard genericLoc = UniversalCardMatcher.matchGenericLocation(signals);
            if (genericLoc != null) {
                return genericLoc;
            }
        }

        // Tier 5d: Universal single-button system action notifications (Mail, Auction, Quest, Reward).
        if (cfg.autoConvertActionCards) {
            ShareCard singleAct = UniversalCardMatcher.matchSingleAction(signals);
            if (singleAct != null) {
                return singleAct;
            }
        }

        // Tier 6: Legacy plain-text adapters registered via CardRegistry.
        return CardRegistry.adaptText(senderName, raw);
    }

    /** Stamps ONLY commands that genuinely existed on the incoming MC Component and pass the dangerous-command check. */
    private static ShareCard attachVerifiedNativeCommands(ShareCard card, Set<String> nativeCmds) {
        if (card == null || nativeCmds == null || nativeCmds.isEmpty()) return card;
        List<String> safe = new ArrayList<>();
        for (String cmd : nativeCmds) {
            if (!UniversalCardMatcher.isDangerousCommand(cmd)) {
                safe.add(cmd);
            }
        }
        return safe.isEmpty() ? card : card.withNativeClickCommands(safe);
    }

    private static boolean looksLikeMapSystemLine(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("journeymap") || lower.contains("voxelmap") || lower.contains("xaero")
                || lower.contains("waypoint") || lower.contains("dim:");
    }

    /** Builds a share card for the local player's current position with a real terrain minimap preview. */
    public static ShareCard fromPlayerLocation(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return null;
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        String dim = client.level.dimension().location().toString();
        ShareCard card = ComposerLocationCard.create(client.player.getGameProfile().getName(), x, y, z, dim);
        String preview = MinimapPreviewSampler.sampleFromLevel(
                client.level, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), dim);
        if (preview != null) {
            card = card.toBuilder()
                    .putData(com.atom.chat.api.card.MinimapPreviewCodec.DATA_KEY, preview)
                    .build();
        }
        return card;
    }

    /** Builds an equipment/item share card from a live {@link ItemStack}. */
    public static ShareCard localizeComposerLocation(ShareCard card) {
        return ComposerLocationCard.localize(card, net.minecraft.client.resources.language.I18n::get);
    }

    public static ShareCard fromItemStack(ItemStack stack, String sourceLabel) {
        if (stack == null || stack.isEmpty()) return null;
        String name = BuiltInCardParsers.sanitizeLabel(stack.getHoverName().getString(), 48);
        int accent = rarityAccent(stack);
        String rarity = rarityLabel(stack);
        String summary = summarizeItem(stack);
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String snbt = encodeItemSnbt(stack);
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack.copy()));
        ShareCard.ItemPayload payload = new ShareCard.ItemPayload(
                itemId, snbt, name, summary, rarity, accent, stack.getCount());
        return ShareCard.item(name)
                .title(name)
                .subtitle(summary)
                .source(sourceLabel == null || sourceLabel.isBlank() ? "装备分享" : sourceLabel)
                .badge(rarity)
                .accentColor(accent)
                .itemPayload(payload)
                .nativeHoverObject(hover)
                .hudTag("[装备]")
                .hudSummary(name + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""))
                .fallbackPlainText("[装备] " + name + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""))
                .build();
    }

    /** Resolves and caches a live {@link HoverEvent} for an ITEM card (negative-cached on parse failure). */
    public static HoverEvent resolveItemHover(ShareCard card) {
        if (card == null) return null;
        if (card.nativeHoverObject() instanceof HoverEvent h) {
            return h;
        }
        Optional<HoverEvent> cached = HOVER_CACHE.get(card);
        if (cached != null) {
            return cached.orElse(null);
        }
        HoverEvent resolved = null;
        ShareCard.ItemPayload item = card.item();
        if (item != null) {
            ItemStack stack = decodeItemSnbt(item.itemSnbt(), item.itemId(), item.count());
            if (stack != null && !stack.isEmpty()) {
                resolved = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack));
            }
        }
        HOVER_CACHE.put(card, Optional.ofNullable(resolved));
        return resolved;
    }

    private static ShareCard adaptTpaRequest(Component component, UniversalCardMatcher.MessageSignals signals) {
        if (!signals.system()) return null;
        var receipt = findTranslatable(component, "stellarrtp.tpa.request_accepted",
                "stellarrtp.tpa.request_denied", "stellarrtp.failure.request_expired", "stellarrtp.tpa.no_pending");
        TeleportRequestSession.observe(signals.rawText(), receipt == null ? "" : receipt.getKey(),
                receipt == null || receipt.getArgs().length == 0 ? "" : argString(receipt.getArgs()[0]));
        var request = findTranslatable(component, "stellarrtp.tpa.request_received_to_player",
                "stellarrtp.tpa.request_received_here");
        String requester = request == null || request.getArgs().length == 0 ? "" : argString(request.getArgs()[0]);
        var card = UniversalCardMatcher.matchTeleportRequest(signals, requester,
                request != null && "stellarrtp.tpa.request_received_here".equals(request.getKey()),
                root -> CardActionExecutor.hasServerCommand(Minecraft.getInstance(), root));
        if (card != null) TeleportRequestSession.receive(card);
        return card;
    }

    private static void observeRedPacketFollowUp(Component component, String raw, List<ClickEvent> rawClicks) {
        if (rawClicks == null || rawClicks.isEmpty()) return;
        String infoId = null;
        for (ClickEvent click : rawClicks) {
            String val = click.getValue() == null ? "" : click.getValue().trim();
            if (click.getAction() == ClickEvent.Action.RUN_COMMAND) {
                if (RP_CLAIM_CMD.matcher(val).matches()) {
                    return; // This is the original announcement, not a follow-up receipt.
                }
                var mInfo = RP_INFO_CMD.matcher(val);
                if (mInfo.matches()) {
                    infoId = mInfo.group(1);
                }
            } else if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                return; // Password announcement or password reminder, not a terminal receipt.
            }
        }
        if (infoId == null || infoId.isBlank()) return;

        String cardId = "rp:" + infoId;
        String detailCmd = "/redpacket info " + infoId;
        String cleanRaw = raw.replaceAll("§.", "");

        TranslatableContents claimedTc = findTranslatable(component, "msg.module_shop_redpacket.claimed");
        if (claimedTc != null || cleanRaw.contains("你领取了")) {
            String amountText = "";
            if (claimedTc != null && claimedTc.getArgs().length >= 3) {
                amountText = BuiltInCardParsers.sanitizeLabel(
                        argString(claimedTc.getArgs()[1]) + " " + argString(claimedTc.getArgs()[2]), 24);
            } else {
                var mAmt = RP_CLAIMED_AMOUNT.matcher(cleanRaw);
                if (mAmt.find()) {
                    amountText = BuiltInCardParsers.sanitizeLabel(mAmt.group(1), 24);
                }
            }
            String status = amountText.isEmpty()
                    ? "已领取 · 点看详情"
                    : "已领取 · " + amountText + "（点看详情）";
            CardStateStore.update(CardState.dimmed(cardId, status)
                    .badgeText("已领取").onClickCommand(detailCmd).build());
            return;
        }
        if (findTranslationKey(component, "msg.module_shop_redpacket.already") != null
                || cleanRaw.contains("已经领过")) {
            CardStateStore.update(CardState.dimmed(cardId, "已领取 · 点看详情")
                    .badgeText("已领取").onClickCommand(detailCmd).build());
            return;
        }
        CardState existing = CardStateStore.get(cardId);
        if (existing != null && "已领取".equals(existing.badgeText())) {
            return; // Keep this player's own claimed amount rather than overwriting with "已领完/已过期".
        }
        if (findTranslationKey(component, "msg.module_shop_redpacket.finished", "msg.module_shop_redpacket.empty") != null
                || cleanRaw.contains("已被领完") || cleanRaw.contains("已被抢光")) {
            CardStateStore.update(CardState.dimmed(cardId, "红包已被抢光 · 点看手气最佳")
                    .badgeText("已领完").onClickCommand(detailCmd).build());
        } else if (findTranslationKey(component, "msg.module_shop_redpacket.expired") != null
                || cleanRaw.contains("已过期")) {
            CardStateStore.update(CardState.dimmed(cardId, "红包已过期 · 点看详情")
                    .badgeText("已过期").onClickCommand(detailCmd).build());
        }
    }

    private static ShareCard adaptRedPacketAnnouncement(Component component, String raw,
                                                        List<ClickEvent> rawClicks, Set<String> nativeCmds) {
        String claimId = null;
        String claimCmd = null;
        String infoId = null;
        String suggestPassword = null;
        for (ClickEvent click : rawClicks) {
            String val = click.getValue() == null ? "" : click.getValue().trim();
            if (click.getAction() == ClickEvent.Action.RUN_COMMAND) {
                var mClaim = RP_CLAIM_CMD.matcher(val);
                if (mClaim.matches()) {
                    claimId = mClaim.group(1);
                    claimCmd = val.startsWith("/") ? val : "/" + val;
                    continue;
                }
                var mInfo = RP_INFO_CMD.matcher(val);
                if (mInfo.matches()) {
                    infoId = mInfo.group(1);
                }
            } else if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND && !val.isEmpty()) {
                suggestPassword = val;
            }
        }
        boolean visibleCredential = isLegacyCredentialAnnouncement(component, raw);
        boolean hasPassword = visibleCredential || claimId == null && suggestPassword != null;
        String packetId = claimId != null ? claimId : infoId;
        if (packetId == null && visibleCredential) {
            packetId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        }
        if (packetId == null || packetId.isBlank()) return null;
        if (claimId == null && !visibleCredential) return null;

        String cleanRaw = raw.replaceAll("§.", "");
        TranslatableContents sentTc = findTranslatable(component,
                "msg.module_shop_redpacket.sent_lucky", "msg.module_shop_redpacket.sent_even");
        boolean lucky = sentTc != null
                ? "msg.module_shop_redpacket.sent_lucky".equals(sentTc.getKey())
                : !cleanRaw.contains("普通红包");

        String senderName = "";
        if (sentTc != null && sentTc.getArgs().length >= 1) {
            senderName = BuiltInCardParsers.sanitizeLabel(argString(sentTc.getArgs()[0]), 24);
        } else {
            var mSender = RP_SENDER_FALLBACK.matcher(cleanRaw);
            if (mSender.find()) {
                senderName = BuiltInCardParsers.sanitizeLabel(mSender.group(1), 24);
            }
        }

        String greeting = "恭喜发财，大吉大利";
        var mGreet = RP_GREETING.matcher(cleanRaw);
        if (mGreet.find()) {
            String extracted = BuiltInCardParsers.sanitizeLabel(mGreet.group(1), 32);
            if (!extracted.isEmpty()) greeting = extracted;
        }

        int totalCount = 0;
        String currency = "";
        TranslatableContents summaryTc = findTranslatable(component, "msg.module_shop_redpacket.summary");
        if (summaryTc != null && summaryTc.getArgs().length >= 1) {
            try {
                totalCount = Integer.parseInt(argString(summaryTc.getArgs()[0]).trim());
            } catch (NumberFormatException ignored) {
            }
            if (summaryTc.getArgs().length >= 3) {
                currency = BuiltInCardParsers.sanitizeLabel(argString(summaryTc.getArgs()[2]), 16);
            }
        } else {
            var mSum = RP_SUMMARY_COUNT.matcher(cleanRaw);
            if (mSum.find()) {
                try {
                    totalCount = Integer.parseInt(mSum.group(1));
                } catch (NumberFormatException ignored) {
                }
                if (mSum.group(2) != null) {
                    currency = BuiltInCardParsers.sanitizeLabel(mSum.group(2), 16);
                }
            }
        }

        ShareCard card = BuiltInCardParsers.buildRedPacketCard(
                packetId, greeting, lucky, hasPassword, suggestPassword,
                totalCount, currency, hasPassword ? suggestPassword : claimCmd, hasPassword);
        if (card == null) return null;
        if (!senderName.isEmpty()) {
            card = card.toBuilder().putData(CARD_SENDER_DATA_KEY, senderName).build();
        }
        return attachVerifiedNativeCommands(card, nativeCmds);
    }

    private static String findXaeroCommand(List<ClickEvent> clicks) {
        if (clicks == null) return "";
        for (ClickEvent click : clicks) {
            if (click.getAction() != ClickEvent.Action.RUN_COMMAND) continue;
            String val = click.getValue();
            if (val == null || val.isBlank()) continue;
            String low = val.trim().toLowerCase(Locale.ROOT);
            if (low.startsWith("/xaero_waypoint_add:") || low.startsWith("xaero_waypoint_add:")
                    || low.startsWith("/xaero-waypoint:") || low.startsWith("xaero-waypoint:")
                    || low.startsWith("/xaero_waypoint:") || low.startsWith("xaero_waypoint:")) {
                return val.trim();
            }
        }
        return "";
    }

    private static ShareCard enrichXaeroMetadata(Component component, String raw, ShareCard card) {
        if (card == null) return null;
        String sharer = "";
        String localizedName = "";
        TranslatableContents tc = findTranslatable(component,
                "gui.xaero_waypoint_shared_dimension2",
                "gui.xaero_waypoint_shared2",
                "gui.xaero_waypoint_shared_dimension",
                "gui.xaero_waypoint_shared");
        if (tc != null && tc.getArgs().length >= 1) {
            sharer = BuiltInCardParsers.sanitizeLabel(argString(tc.getArgs()[0]), 24);
            if (tc.getArgs().length >= 2) {
                localizedName = BuiltInCardParsers.sanitizeLabel(argString(tc.getArgs()[1]), 32);
            }
        } else if (raw != null) {
            String clean = raw.replaceAll("§.", "").trim();
            int idx = clean.indexOf(" 从 ");
            if (idx <= 0) idx = clean.indexOf(" 分享了一个");
            if (idx <= 0) idx = clean.toLowerCase(Locale.ROOT).indexOf(" shared a waypoint");
            if (idx > 0 && idx <= 24) {
                String cand = BuiltInCardParsers.sanitizeLabel(clean.substring(0, idx), 24);
                if (!cand.contains(" ")) sharer = cand;
            }
        }
        if ("Server".equalsIgnoreCase(sharer) || "Somebody".equalsIgnoreCase(sharer)
                || "服务器".equals(sharer) || "某人".equals(sharer)) {
            sharer = "";
        }
        boolean updateName = !localizedName.isEmpty() && card.location() != null
                && !localizedName.equals(card.location().name());
        if (sharer.isEmpty() && !updateName) {
            return card;
        }
        ShareCard.Builder b = card.toBuilder();
        if (!sharer.isEmpty()) {
            b.putData(CARD_SENDER_DATA_KEY, sharer);
        }
        if (updateName) {
            ShareCard.LocationPayload loc = card.location();
            ShareCard.LocationPayload updatedLoc = new ShareCard.LocationPayload(
                    localizedName, loc.x(), loc.y(), loc.z(),
                    loc.dimension(), loc.accentColor(), loc.mapSource(), loc.nativeAddCommand());
            b.title(localizedName)
                    .locationPayload(updatedLoc)
                    .hudSummary(localizedName + " (" + loc.blockX() + ", " + loc.blockY() + ", " + loc.blockZ() + ")")
                    .fallbackPlainText("[坐标] " + localizedName + " (" + loc.blockX() + ", " + loc.blockY() + ", "
                            + loc.blockZ() + ", " + BuiltInCardParsers.dimensionDisplayName(loc.dimension()) + ")");
        }
        return b.build();
    }

    private static ShareCard adaptSingleItemShare(List<HoverEvent> itemHovers, String raw, boolean system,
                                                  List<UniversalCardMatcher.ClickActionSpec> clicks,
                                                  Set<String> nativeCmds) {
        if (itemHovers.size() != 1) {
            return null;
        }
        String body = raw;
        if (body.startsWith("<")) {
            int idx = body.indexOf("> ");
            if (idx > 0) body = body.substring(idx + 2);
        }
        body = body.replaceAll("§.", "").trim();
        HoverEvent hover = itemHovers.get(0);
        HoverEvent.ItemStackInfo info = hover.getValue(HoverEvent.Action.SHOW_ITEM);
        if (info == null) return null;
        ItemStack stack = info.getItemStack();
        if (stack == null || stack.isEmpty()) return null;

        // Find optional single safe executable action (e.g. Auction House "[购买]" / "[查看]").
        UniversalCardMatcher.ClickActionSpec attachedBtn = null;
        if (system) {
            for (UniversalCardMatcher.ClickActionSpec c : clicks) {
                if (c.isExecutableCommand() && !UniversalCardMatcher.isDangerousCommand(c.value())) {
                    if (attachedBtn != null) {
                        attachedBtn = null; // >1 buttons: do not guess
                        break;
                    }
                    attachedBtn = c;
                }
            }
        }

        String itemName = stack.getHoverName().getString();
        int extraChars = body.length() - itemName.length();
        // Player messages stay strict (avoid swallowing commentary); system item notices with an action button allow a short sentence.
        int maxExtra = (system && attachedBtn != null) ? 48 : 14;
        if (extraChars > maxExtra || (system && attachedBtn == null && extraChars > 14)) {
            return null;
        }

        ShareCard base = fromItemStack(stack, "装备展示").withNativeHoverObject(hover);
        if (attachedBtn != null) {
            String btnLabel = BuiltInCardParsers.sanitizeLabel(
                    attachedBtn.label().replaceAll("[\\[\\]【】]", ""), 8);
            if (btnLabel.isEmpty()) btnLabel = "查看";
            base = base.toBuilder()
                    .addAction("item_act", btnLabel, attachedBtn.kind(), attachedBtn.value(), true)
                    .build();
            return attachVerifiedNativeCommands(base, nativeCmds);
        }
        return base;
    }

    /** Version-safe SNBT encoding (works on both MC 1.21.1 HolderLookup.Provider and MC 1.20.1 save(CompoundTag)). */
    private static String encodeItemSnbt(ItemStack stack) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.level != null) {
                Object regAccess = client.level.registryAccess();
                for (Method m : ItemStack.class.getMethods()) {
                    if ("save".equals(m.getName()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isInstance(regAccess)) {
                        Object tag = m.invoke(stack, regAccess);
                        return tag == null ? "" : tag.toString();
                    }
                }
            }
            // MC 1.20.1 fallback: save(CompoundTag)
            Method legacySave = ItemStack.class.getMethod("save", CompoundTag.class);
            CompoundTag out = new CompoundTag();
            legacySave.invoke(stack, out);
            return out.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Version-safe SNBT decoding (works on both MC 1.21.1 parseOptional and MC 1.20.1 ItemStack.of). */
    private static ItemStack decodeItemSnbt(String snbt, String itemId, int count) {
        try {
            if (snbt != null && !snbt.isBlank()) {
                CompoundTag tag = TagParser.parseTag(snbt);
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.level != null) {
                    Object regAccess = client.level.registryAccess();
                    for (Method m : ItemStack.class.getMethods()) {
                        if ("parseOptional".equals(m.getName()) && m.getParameterCount() == 2
                                && m.getParameterTypes()[0].isInstance(regAccess)
                                && m.getParameterTypes()[1] == CompoundTag.class) {
                            Object parsed = m.invoke(null, regAccess, tag);
                            if (parsed instanceof ItemStack s && !s.isEmpty()) return s;
                        }
                    }
                }
                // MC 1.20.1 fallback: ItemStack.of(CompoundTag)
                Method legacyOf = ItemStack.class.getMethod("of", CompoundTag.class);
                Object parsed = legacyOf.invoke(null, tag);
                if (parsed instanceof ItemStack s && !s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {
        }
        if (itemId != null && !itemId.isBlank()) {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
                    return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl), Math.max(1, count));
                }
            } catch (Throwable ignored) {
            }
        }
        return ItemStack.EMPTY;
    }

    private static int rarityAccent(ItemStack stack) {
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return 0xFFA855F7;
        if (r == Rarity.RARE) return 0xFF06B6D4;
        if (r == Rarity.UNCOMMON) return 0xFFEAB308;
        if (stack.isEnchanted()) return 0xFF60A5FA;
        return 0xFF64748B;
    }

    private static String rarityLabel(ItemStack stack) {
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return "史诗";
        if (r == Rarity.RARE) return "稀有";
        if (r == Rarity.UNCOMMON) return "罕见";
        return stack.isEnchanted() ? "附魔" : "普通";
    }

    /** Version-safe item summary without direct 1.21.1 DataComponents imports. */
    private static String summarizeItem(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        try {
            // MC 1.21.1: stack.getEnchantments() -> ItemEnchantments.entrySet()
            Method getEnch = ItemStack.class.getMethod("getEnchantments");
            Object enchObj = getEnch.invoke(stack);
            if (enchObj != null) {
                Method entrySet = enchObj.getClass().getMethod("entrySet");
                Object entries = entrySet.invoke(enchObj);
                if (entries instanceof Iterable<?> iterable) {
                    Class<?> enchClass = Class.forName("net.minecraft.world.item.enchantment.Enchantment");
                    Method fullName = null;
                    for (Method m : enchClass.getMethods()) {
                        if ("getFullname".equals(m.getName()) && m.getParameterCount() == 2) {
                            fullName = m;
                            break;
                        }
                    }
                    for (Object entry : iterable) {
                        if (entry instanceof Map.Entry<?, ?> e && fullName != null) {
                            Object comp = fullName.invoke(null, e.getKey(), ((Number) e.getValue()).intValue());
                            if (comp instanceof Component c) {
                                parts.add(c.getString());
                                if (parts.size() >= 3) break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (parts.isEmpty()) {
            try {
                // MC 1.20.1 fallback: EnchantmentHelper.getEnchantments(ItemStack) -> Map<Enchantment, Integer>
                Class<?> helperCls = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper");
                Method getEnchs = helperCls.getMethod("getEnchantments", ItemStack.class);
                Object mapObj = getEnchs.invoke(null, stack);
                if (mapObj instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        if (e.getKey() == null || !(e.getValue() instanceof Number level)) continue;
                        Method getFull = e.getKey().getClass().getMethod("getFullname", int.class);
                        Object comp = getFull.invoke(e.getKey(), level.intValue());
                        if (comp instanceof Component c) {
                            parts.add(c.getString());
                            if (parts.size() >= 3) break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
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

    private static void collectItemHovers(Component c, List<HoverEvent> out) {
        if (c == null) return;
        Style s = c.getStyle();
        if (s != null && s.getHoverEvent() != null && s.getHoverEvent().getAction() == HoverEvent.Action.SHOW_ITEM) {
            out.add(s.getHoverEvent());
        }
        for (Component child : c.getSiblings()) {
            collectItemHovers(child, out);
        }
    }

    private static List<ClickEvent> collectClickEvents(Component c) {
        List<ClickEvent> out = new ArrayList<>();
        collectClickEventsRecursive(c, out);
        return out;
    }

    private static void collectClickEventsRecursive(Component c, List<ClickEvent> out) {
        if (c == null) return;
        Style s = c.getStyle();
        if (s != null && s.getClickEvent() != null) {
            out.add(s.getClickEvent());
        }
        for (Component child : c.getSiblings()) {
            collectClickEventsRecursive(child, out);
        }
    }

    /**
     * Traverses both {@code c}'s own content and its siblings with inherited {@link ClickEvent} tracking,
     * so wrapper ClickEvents on a root component never swallow child sibling buttons or pollute labels.
     */
    private static void collectClickActionSpecs(Component c, ClickEvent inherited,
                                                List<UniversalCardMatcher.ClickActionSpec> out) {
        if (c == null) return;
        Style s = c.getStyle();
        ClickEvent own = (s != null && s.getClickEvent() != null) ? s.getClickEvent() : inherited;

        String ownText = ownContentOnly(c);
        if (own != null && !ownText.isBlank()) {
            ShareCard.ActionKind kind = mapClickKind(own.getAction());
            if (kind != null && own.getValue() != null && !own.getValue().isBlank()) {
                // Merge adjacent leaf spans governed by the exact same ClickEvent into one button label.
                if (!out.isEmpty()) {
                    UniversalCardMatcher.ClickActionSpec prev = out.get(out.size() - 1);
                    if (prev.kind() == kind && prev.value().equals(own.getValue().trim())) {
                        out.set(out.size() - 1, new UniversalCardMatcher.ClickActionSpec(
                                prev.label() + ownText, kind, prev.value()));
                    } else {
                        out.add(new UniversalCardMatcher.ClickActionSpec(ownText, kind, own.getValue()));
                    }
                } else {
                    out.add(new UniversalCardMatcher.ClickActionSpec(ownText, kind, own.getValue()));
                }
            }
        }
        for (Component child : c.getSiblings()) {
            collectClickActionSpecs(child, own, out);
        }
    }

    private static String ownContentOnly(Component c) {
        StringBuilder sb = new StringBuilder();
        c.getContents().visit(part -> {
            sb.append(part);
            return Optional.empty();
        });
        return sb.toString();
    }

    private static ShareCard.ActionKind mapClickKind(ClickEvent.Action action) {
        if (action == null) return null;
        return switch (action) {
            case RUN_COMMAND -> ShareCard.ActionKind.RUN_COMMAND;
            case SUGGEST_COMMAND -> ShareCard.ActionKind.SUGGEST_COMMAND;
            case OPEN_URL -> ShareCard.ActionKind.OPEN_URL;
            case COPY_TO_CLIPBOARD -> ShareCard.ActionKind.COPY_TEXT;
            default -> null;
        };
    }

    private static void collectTranslationKeys(Component c, List<String> out) {
        if (c == null) return;
        if (c.getContents() instanceof TranslatableContents tc && tc.getKey() != null) {
            out.add(tc.getKey());
        }
        for (Component child : c.getSiblings()) {
            collectTranslationKeys(child, out);
        }
    }

    /** Records ONLY native RUN_COMMAND clicks (never SUGGEST_COMMAND, preventing 1-click escalation). */
    private static Set<String> collectNativeCommandSet(List<ClickEvent> clicks) {
        if (clicks == null || clicks.isEmpty()) return Set.of();
        Set<String> set = new LinkedHashSet<>();
        for (ClickEvent click : clicks) {
            if (click.getAction() == ClickEvent.Action.RUN_COMMAND
                    && click.getValue() != null && !click.getValue().isBlank()) {
                set.add(click.getValue().trim());
            }
        }
        return set;
    }

    private static String firstCommand(List<ClickEvent> clicks) {
        for (ClickEvent click : clicks) {
            if (click.getAction() == ClickEvent.Action.RUN_COMMAND
                    || click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                return click.getValue();
            }
        }
        return "";
    }

    private static TranslatableContents findTranslatable(Component c, String... keys) {
        if (c == null) return null;
        if (c.getContents() instanceof TranslatableContents tc) {
            for (String k : keys) {
                if (k.equals(tc.getKey())) return tc;
            }
        }
        for (Component child : c.getSiblings()) {
            TranslatableContents found = findTranslatable(child, keys);
            if (found != null) return found;
        }
        return null;
    }

    private static String findTranslationKey(Component c, String... keys) {
        TranslatableContents tc = findTranslatable(c, keys);
        return tc == null ? null : tc.getKey();
    }

    private static String argString(Object arg) {
        if (arg instanceof Component c) return c.getString();
        return arg == null ? "" : String.valueOf(arg);
    }


    private static String detectBracketMapSource() {
        if (isModLoaded("journeymap")) return "JourneyMap";
        if (isModLoaded("voxelmap")) return "VoxelMap";
        return "地图分享";
    }

    private static boolean isModLoaded(String modId) {
        try {
            Class<?> cls = Class.forName("net.neoforged.fml.ModList");
            Object list = cls.getMethod("get").invoke(null);
            if (list != null && Boolean.TRUE.equals(cls.getMethod("isLoaded", String.class).invoke(list, modId))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cls = Class.forName("net.minecraftforge.fml.ModList");
            Object list = cls.getMethod("get").invoke(null);
            if (list != null && Boolean.TRUE.equals(cls.getMethod("isLoaded", String.class).invoke(list, modId))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String currentDimension() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null) {
            return client.level.dimension().location().toString();
        }
        return "minecraft:overworld";
    }
}
