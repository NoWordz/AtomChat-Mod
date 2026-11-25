package com.atom.chat.card;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.CardRegistry;
import com.atom.chat.api.card.CardRuleEngine;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.CardStateStore;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.UniversalCardMatcher;
import com.atom.chat.config.AtomChatConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

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
import java.util.regex.Pattern;

/**
 * Yarn-mapping adapter that converts common mod chat lines into rich {@link ShareCard}s
 * with zero upstream mod changes (Fabric 1.21.1 twin of the official-mapping adapter).
 */
public final class BuiltInCardAdapter {

    public static final String CARD_SENDER_DATA_KEY = "_sender";

    private static final Pattern RP_CLAIM_CMD =
            Pattern.compile("(?i)^/?redpacket\\s+claim\\s+([a-z0-9_-]{1,24})$");
    private static final Pattern RP_INFO_CMD =
            Pattern.compile("(?i)^/?redpacket\\s+info\\s+([a-z0-9_-]{1,24})$");
    private static final Pattern RP_GREETING =
            Pattern.compile("「([^」]{1,40})」");
    private static final Pattern RP_SUMMARY_COUNT =
            Pattern.compile("共\\s*(\\d{1,5})\\s*个(?:.*?总额\\s*\\d+\\s*([^\\s\\[\\n]{1,12}))?");
    private static final Pattern RP_SENDER_FALLBACK =
            Pattern.compile("(?:\\[红包\\]\\s*)?([A-Za-z0-9_]{2,20})\\s*发了一个");
    private static final Pattern RP_CLAIMED_AMOUNT =
            Pattern.compile("获得\\s*(\\d+\\s*[^\\s\\[\\n]{1,12})");

    private static final Map<ShareCard, Optional<HoverEvent>> HOVER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<ComponentCardAdapter> COMPONENT_ADAPTERS = new CopyOnWriteArrayList<>();

    private BuiltInCardAdapter() {
    }

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
        com.atom.chat.net.CardCompanionClient.resetNativeShares();
        HOVER_CACHE.clear();
        MinimapPreviewSampler.clearCache();
    }

    public static ShareCard adapt(Text component, String senderName, boolean system) {
        try {
            return adaptUnchecked(component, senderName, system);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isLegacyCredentialAnnouncement(Text component, String raw) {
        if (raw == null || component == null) return false;
        if (findTranslationKey(component, "msg.module_shop_redpacket.btn_password") != null) return true;
        String visible = raw.replaceAll("§.", "").strip();
        String folded = visible.toLowerCase(Locale.ROOT);
        boolean envelope = visible.startsWith("[红包]") || folded.startsWith("[red packet]") || findTranslationKey(component,
                "msg.module_shop_redpacket.sent_lucky", "msg.module_shop_redpacket.sent_even") != null;
        return envelope && (visible.contains("发送口令领取") || visible.contains("口令：") || visible.contains("口令:")
                || visible.contains("发了一个") && visible.contains("口令") || folded.contains("password:"));
    }

    private static ShareCard adaptUnchecked(Text component, String senderName, boolean system) {
        if (component == null) {
            return null;
        }
        String raw = component.getString();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        List<ClickEvent> rawClicks = collectClickEvents(component);
        if (isLegacyCredentialAnnouncement(component, raw)) {
            var protectedCard = adaptRedPacketAnnouncement(component, raw, rawClicks, Set.of());
            if (protectedCard != null) return protectedCard;
        }
        var cfg = AtomChatConfig.get();
        if (!cfg.shareCardsEnabled) return null;

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

        for (ComponentCardAdapter adapter : COMPONENT_ADAPTERS) {
            try {
                ShareCard custom = adapter.adapt(component, signals);
                if (custom != null) {
                    return attachVerifiedNativeCommands(custom, nativeCmds);
                }
            } catch (Throwable ignored) {
            }
        }

        ShareCard signalCard = CardRegistry.adaptSignals(signals);
        if (signalCard != null) {
            return attachVerifiedNativeCommands(signalCard, nativeCmds);
        }

        if (cfg.customCardRulesEnabled) {
            ShareCard ruleCard = CardRuleEngine.match(signals);
            if (ruleCard != null) {
                return attachVerifiedNativeCommands(ruleCard, nativeCmds);
            }
        }

        if (cfg.autoConvertRequestCards) {
            ShareCard tpa = adaptTpaRequest(component, raw, nativeCmds);
            if (tpa != null) {
                return tpa;
            }
        }

        if (system) {
            ShareCard redPacket = adaptRedPacketAnnouncement(component, raw, rawClicks, nativeCmds);
            if (redPacket != null) {
                return redPacket;
            }
        }

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

        if (cfg.autoConvertRequestCards) {
            ShareCard pair = UniversalCardMatcher.matchPairRequest(signals);
            if (pair != null) {
                return pair;
            }
        }

        if (cfg.autoConvertItemLinksToCards && !itemHovers.isEmpty()) {
            ShareCard itemCard = adaptSingleItemShare(itemHovers, raw, system, clickSpecs, nativeCmds);
            if (itemCard != null) {
                return itemCard;
            }
        }

        if (cfg.autoConvertLocationCards) {
            ShareCard genericLoc = UniversalCardMatcher.matchGenericLocation(signals);
            if (genericLoc != null) {
                return genericLoc;
            }
        }

        if (cfg.autoConvertActionCards) {
            ShareCard singleAct = UniversalCardMatcher.matchSingleAction(signals);
            if (singleAct != null) {
                return singleAct;
            }
        }

        return CardRegistry.adaptText(senderName, raw);
    }

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

    public static ShareCard fromPlayerLocation(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return null;
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        String dim = client.world.getRegistryKey().getValue().toString();
        ShareCard card = ComposerLocationCard.create(client.player.getGameProfile().getName(), x, y, z, dim);
        String preview = MinimapPreviewSampler.sampleFromLevel(
                client.world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), dim);
        if (preview != null) {
            card = card.toBuilder()
                    .putData(com.atom.chat.api.card.MinimapPreviewCodec.DATA_KEY, preview)
                    .build();
        }
        return card;
    }

    public static ShareCard localizeComposerLocation(ShareCard card) {
        return ComposerLocationCard.localize(card, net.minecraft.client.resource.language.I18n::translate);
    }

    public static ShareCard fromItemStack(ItemStack stack, String sourceLabel) {
        if (stack == null || stack.isEmpty()) return null;
        String name = BuiltInCardParsers.sanitizeLabel(stack.getName().getString(), 48);
        int accent = rarityAccent(stack);
        String rarity = rarityLabel(stack);
        String summary = summarizeItem(stack);
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        String snbt = encodeItemSnbt(stack);
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(stack.copy()));
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
                resolved = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(stack));
            }
        }
        HOVER_CACHE.put(card, Optional.ofNullable(resolved));
        return resolved;
    }

    private static ShareCard adaptTpaRequest(Text component, String raw, Set<String> nativeCmds) {
        List<ClickEvent> clicks = collectClickEvents(component);
        String acceptCmd = null;
        String denyCmd = null;
        for (ClickEvent click : clicks) {
            if (click.getAction() != ClickEvent.Action.RUN_COMMAND) continue;
            String val = click.getValue() == null ? "" : click.getValue().trim();
            String lower = val.toLowerCase(Locale.ROOT);
            if (lower.startsWith("/tpyes") || lower.startsWith("tpyes")
                    || lower.startsWith("/tpaccept") || lower.startsWith("tpaccept")) {
                acceptCmd = val.startsWith("/") ? val : "/" + val;
            } else if (lower.startsWith("/tpno") || lower.startsWith("tpno")
                    || lower.startsWith("/tpdeny") || lower.startsWith("tpdeny")) {
                denyCmd = val.startsWith("/") ? val : "/" + val;
            }
        }

        TranslatableTextContent tc = findTranslatable(component,
                "stellarrtp.tpa.request_received_to_player",
                "stellarrtp.tpa.request_received_here");
        if (tc != null) {
            boolean toHere = "stellarrtp.tpa.request_received_here".equals(tc.getKey());
            String requester = tc.getArgs().length > 0 ? argString(tc.getArgs()[0]) : "玩家";
            ShareCard card = BuiltInCardParsers.buildTpaRequestCard(
                    requester, toHere, acceptCmd, denyCmd, "繁星传送");
            return attachVerifiedNativeCommands(card, nativeCmds);
        }

        if (acceptCmd != null && denyCmd != null) {
            boolean toHere = raw.contains("过去") || raw.contains("他那里")
                    || raw.toLowerCase(Locale.ROOT).contains("teleport you to")
                    || raw.toLowerCase(Locale.ROOT).contains("tpahere");
            String requester = extractLeadingWord(raw);
            ShareCard card = BuiltInCardParsers.buildTpaRequestCard(
                    requester, toHere, acceptCmd, denyCmd, "传送");
            return attachVerifiedNativeCommands(card, nativeCmds);
        }
        return null;
    }

    private static void observeRedPacketFollowUp(Text component, String raw, List<ClickEvent> rawClicks) {
        if (rawClicks == null || rawClicks.isEmpty()) return;
        String infoId = null;
        for (ClickEvent click : rawClicks) {
            String val = click.getValue() == null ? "" : click.getValue().trim();
            if (click.getAction() == ClickEvent.Action.RUN_COMMAND) {
                if (RP_CLAIM_CMD.matcher(val).matches()) {
                    return;
                }
                var mInfo = RP_INFO_CMD.matcher(val);
                if (mInfo.matches()) {
                    infoId = mInfo.group(1);
                }
            } else if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                return;
            }
        }
        if (infoId == null || infoId.isBlank()) return;

        String cardId = "rp:" + infoId;
        String detailCmd = "/redpacket info " + infoId;
        String cleanRaw = raw.replaceAll("§.", "");

        TranslatableTextContent claimedTc = findTranslatable(component, "msg.module_shop_redpacket.claimed");
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
            return;
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

    private static ShareCard adaptRedPacketAnnouncement(Text component, String raw,
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
        TranslatableTextContent sentTc = findTranslatable(component,
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
        TranslatableTextContent summaryTc = findTranslatable(component, "msg.module_shop_redpacket.summary");
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

    private static ShareCard enrichXaeroMetadata(Text component, String raw, ShareCard card) {
        if (card == null) return null;
        String sharer = "";
        String localizedName = "";
        TranslatableTextContent tc = findTranslatable(component,
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
        HoverEvent.ItemStackContent info = hover.getValue(HoverEvent.Action.SHOW_ITEM);
        if (info == null) return null;
        ItemStack stack = info.asStack();
        if (stack == null || stack.isEmpty()) return null;

        UniversalCardMatcher.ClickActionSpec attachedBtn = null;
        if (system) {
            for (UniversalCardMatcher.ClickActionSpec c : clicks) {
                if (c.isExecutableCommand() && !UniversalCardMatcher.isDangerousCommand(c.value())) {
                    if (attachedBtn != null) {
                        attachedBtn = null;
                        break;
                    }
                    attachedBtn = c;
                }
            }
        }

        String itemName = stack.getName().getString();
        int extraChars = body.length() - itemName.length();
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

    private static String encodeItemSnbt(ItemStack stack) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                var nbt = stack.encode(client.world.getRegistryManager());
                return nbt == null ? "" : nbt.toString();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static ItemStack decodeItemSnbt(String snbt, String itemId, int count) {
        try {
            if (snbt != null && !snbt.isBlank()) {
                NbtCompound tag = StringNbtReader.parse(snbt);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.world != null) {
                    ItemStack parsed = ItemStack.fromNbtOrEmpty(client.world.getRegistryManager(), tag);
                    if (!parsed.isEmpty()) return parsed;
                }
            }
        } catch (Throwable ignored) {
        }
        if (itemId != null && !itemId.isBlank()) {
            try {
                Identifier rl = Identifier.tryParse(itemId);
                if (rl != null && Registries.ITEM.containsId(rl)) {
                    return new ItemStack(Registries.ITEM.get(rl), Math.max(1, count));
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
        if (stack.hasEnchantments()) return 0xFF60A5FA;
        return 0xFF64748B;
    }

    private static String rarityLabel(ItemStack stack) {
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return "史诗";
        if (r == Rarity.RARE) return "稀有";
        if (r == Rarity.UNCOMMON) return "罕见";
        return stack.hasEnchantments() ? "附魔" : "普通";
    }

    private static String summarizeItem(ItemStack stack) {
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

    private static void collectItemHovers(Text c, List<HoverEvent> out) {
        if (c == null) return;
        Style s = c.getStyle();
        if (s != null && s.getHoverEvent() != null && s.getHoverEvent().getAction() == HoverEvent.Action.SHOW_ITEM) {
            out.add(s.getHoverEvent());
        }
        for (Text child : c.getSiblings()) {
            collectItemHovers(child, out);
        }
    }

    private static List<ClickEvent> collectClickEvents(Text c) {
        List<ClickEvent> out = new ArrayList<>();
        collectClickEventsRecursive(c, out);
        return out;
    }

    private static void collectClickEventsRecursive(Text c, List<ClickEvent> out) {
        if (c == null) return;
        Style s = c.getStyle();
        if (s != null && s.getClickEvent() != null) {
            out.add(s.getClickEvent());
        }
        for (Text child : c.getSiblings()) {
            collectClickEventsRecursive(child, out);
        }
    }

    private static void collectClickActionSpecs(Text c, ClickEvent inherited,
                                                List<UniversalCardMatcher.ClickActionSpec> out) {
        if (c == null) return;
        Style s = c.getStyle();
        ClickEvent own = (s != null && s.getClickEvent() != null) ? s.getClickEvent() : inherited;

        String ownText = ownContentOnly(c);
        if (own != null && !ownText.isBlank()) {
            ShareCard.ActionKind kind = mapClickKind(own.getAction());
            if (kind != null && own.getValue() != null && !own.getValue().isBlank()) {
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
        for (Text child : c.getSiblings()) {
            collectClickActionSpecs(child, own, out);
        }
    }

    private static String ownContentOnly(Text c) {
        StringBuilder sb = new StringBuilder();
        c.getContent().visit(part -> {
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

    private static void collectTranslationKeys(Text c, List<String> out) {
        if (c == null) return;
        if (c.getContent() instanceof TranslatableTextContent tc && tc.getKey() != null) {
            out.add(tc.getKey());
        }
        for (Text child : c.getSiblings()) {
            collectTranslationKeys(child, out);
        }
    }

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

    private static TranslatableTextContent findTranslatable(Text c, String... keys) {
        if (c == null) return null;
        if (c.getContent() instanceof TranslatableTextContent tc) {
            for (String k : keys) {
                if (k.equals(tc.getKey())) return tc;
            }
        }
        for (Text child : c.getSiblings()) {
            TranslatableTextContent found = findTranslatable(child, keys);
            if (found != null) return found;
        }
        return null;
    }

    private static String findTranslationKey(Text c, String... keys) {
        TranslatableTextContent tc = findTranslatable(c, keys);
        return tc == null ? null : tc.getKey();
    }

    private static String argString(Object arg) {
        if (arg instanceof Text c) return c.getString();
        return arg == null ? "" : String.valueOf(arg);
    }

    private static String extractLeadingWord(String raw) {
        String s = raw.replaceAll("§.", "").trim();
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end >= 0 && end + 1 < s.length()) s = s.substring(end + 1).trim();
        }
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : s;
    }

    private static String detectBracketMapSource() {
        if (isModLoaded("journeymap")) return "JourneyMap";
        if (isModLoaded("voxelmap")) return "VoxelMap";
        return "地图分享";
    }

    private static boolean isModLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String currentDimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            return client.world.getRegistryKey().getValue().toString();
        }
        return "minecraft:overworld";
    }
}
