package com.atom.chat.api.card;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java universal semantic card matcher that automatically upgrades common mod/plugin
 * interactive chat messages into unified {@link ShareCard}s without per-mod hardcoding.
 *
 * <p>Covers four universal patterns with strict anti-false-positive and security guardrails:
 * <ol>
 *   <li>Pair-button requests/invitations (TPA, Party, Guild, Trade, Duel, Friend, Claim)</li>
 *   <li>Single-button system action notifications (Mail, Auction, Quest, Reward, Warp)</li>
 *   <li>Generic coordinate shares paired with teleport/waypoint click commands</li>
 *   <li>Dangerous-command denylist shared with {@code CardActionExecutor}</li>
 * </ol>
 */
public final class UniversalCardMatcher {

    /** Lightweight descriptor of a ClickEvent extracted from an incoming chat Component. */
    public record ClickActionSpec(String label, ShareCard.ActionKind kind, String value) {
        public ClickActionSpec {
            label = label == null ? "" : label.trim();
            kind = kind == null ? ShareCard.ActionKind.COPY_TEXT : kind;
            value = value == null ? "" : value.trim();
        }

        public boolean isExecutableCommand() {
            return (kind == ShareCard.ActionKind.RUN_COMMAND || kind == ShareCard.ActionKind.SUGGEST_COMMAND)
                    && !value.isEmpty();
        }
    }

    /** Normalized platform-independent signals extracted from an incoming chat message. */
    public record MessageSignals(
            String rawText,
            String senderName,
            boolean system,
            List<String> translationKeys,
            List<ClickActionSpec> clicks,
            boolean hasItemHover,
            String currentDimension
    ) {
        public MessageSignals {
            rawText = rawText == null ? "" : rawText;
            senderName = senderName == null ? "" : senderName;
            translationKeys = translationKeys == null ? List.of() : List.copyOf(translationKeys);
            clicks = clicks == null ? List.of() : List.copyOf(clicks);
            currentDimension = (currentDimension == null || currentDimension.isBlank())
                    ? "minecraft:overworld" : currentDimension;
        }
    }

    private static final AtomicLong REQUEST_SEQ = new AtomicLong();

    private static final Pattern BRACKET_PREFIX = Pattern.compile("^\\s*[\\[【(（]([^\\]】)）]{1,16})[\\]】)）]\\s*");
    private static final Pattern PLAYER_TOKEN = Pattern.compile("([A-Za-z0-9_]{3,16})");
    private static final Pattern GENERIC_XYZ_LABELED = Pattern.compile(
            "(?i)x\\s*[:=]?\\s*(-?\\d{1,7})[\\s,;/]+y\\s*[:=]?\\s*(-?\\d{1,4})[\\s,;/]+z\\s*[:=]?\\s*(-?\\d{1,7})");
    private static final Pattern GENERIC_XYZ_TUPLE = Pattern.compile(
            "[\\[（(]\\s*(-?\\d{1,7})\\s*[,，\\s]+\\s*(-?\\d{1,4})\\s*[,，\\s]+\\s*(-?\\d{1,7})\\s*[\\]）)]");
    private static final Pattern TP_CMD_XYZ = Pattern.compile(
            "(?i)^/?(?:tp|stp|teleport|waypoint|wp|xaero_waypoint_add|jm)\\b.*?(-?\\d{1,7})\\s+(-?\\d{1,4})\\s+(-?\\d{1,7})");

    private static final Set<String> POSITIVE_LABELS = Set.of(
            "接受", "同意", "确认", "允许", "加入", "应战", "接收", "确定", "是", "好",
            "accept", "agree", "confirm", "yes", "allow", "join", "ok", "approve");

    private static final Set<String> NEGATIVE_LABELS = Set.of(
            "拒绝", "婉拒", "取消", "忽略", "不同意", "谢绝", "否", "不",
            "deny", "decline", "reject", "cancel", "no", "ignore", "refuse");

    private static final List<String> SINGLE_ACTION_VERBS = List.of(
            "领取", "立即领取", "点击领取", "打开", "点击打开", "立即查看",
            "前往", "点击前往", "传送", "点击传送", "购买", "立即购买", "参与", "进入",
            "签到", "兑换", "回复", "确认", "接受", "加入",
            "claim", "collect", "open", "teleport", "buy", "purchase",
            "join", "redeem", "confirm", "accept");

    private static final List<String> PASSIVE_INFO_LABELS = List.of(
            "详情", "查看详情", "明细", "记录", "details", "detail", "info");

    private static final List<String> PAGINATION_OR_MENU_MARKERS = List.of(
            "上一页", "下一页", "首页", "尾页", "返回", "关闭", "帮助", "目录", "菜单",
            "prev", "previous", "next", "page", "back", "close", "help", "menu");

    /** Administrative / destructive / financial / session-hijack command roots blocked from unsigned cards. */
    private static final Set<String> DANGEROUS_COMMAND_ROOTS = Set.of(
            "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick", "stop", "restart",
            "shutdown", "reload", "whitelist", "execute", "give", "gamemode", "gm", "gmc", "gms", "gmsp",
            "gamerule", "setblock", "fill", "clone", "summon", "kill", "clear", "effect", "enchant",
            "data", "function", "schedule", "forceload", "worldborder", "save-off", "save-all",
            "tp", "teleport", "transfer", "tellraw", "damage", "item", "loot", "place", "ride",
            "attribute", "datapack", "defaultgamemode", "experience", "xp", "return",
            "lp", "lpb", "lpv", "luckperms", "pex", "manuadd", "uperms", "permission", "permissions", "perms",
            "sudo", "esudo", "console", "server", "eval", "script", "sk", "skript", "plugman",
            "spark", "timings", "neoforge", "forge", "fabric", "carpet", "tick", "debug", "publish",
            "difficulty", "time", "weather", "spreadplayers", "scoreboard", "tag", "team", "trigger",
            "pay", "epay", "eco", "money", "points", "sell", "res", "residence", "trust", "trustall",
            "abandonclaim", "coop", "cmi", "mv", "multiverse", "worldedit", "we", "fawe");

    private UniversalCardMatcher() {
    }

    /**
     * Checks whether a slash command (with or without leading '/') is a dangerous admin/destructive
     * command that must never be executed by an unsigned auto-adapted card.
     */
    public static boolean isDangerousCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) return true;
        String trimmed = rawCommand.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '§') return true;
        }
        // Reject WorldEdit/FAWE double-slash commands (e.g. //eval, //set, //sphere) outright.
        if (trimmed.startsWith("//")) return true;
        int slashEnd = 0;
        while (slashEnd < trimmed.length() && trimmed.charAt(slashEnd) == '/') {
            slashEnd++;
        }
        if (slashEnd > 1) return true;
        String cmd = trimmed.substring(slashEnd).trim();
        if (cmd.isEmpty() || cmd.length() > 256 || cmd.startsWith("/")) return true;
        int space = cmd.indexOf(' ');
        String root = (space >= 0 ? cmd.substring(0, space) : cmd).toLowerCase(Locale.ROOT);
        // Strip namespace prefix like minecraft:op or bukkit:ban
        int colon = root.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < root.length()) {
            root = root.substring(colon + 1);
        }
        return DANGEROUS_COMMAND_ROOTS.contains(root);
    }

    /**
     * Pattern A: Universal pair-button request / invitation card (TPA, Party, Guild, Trade, Duel, Friend, Claim).
     * Strictly requires {@code signals.system()} to prevent player-to-player command phishing via chat tags.
     */
    public static ShareCard matchPairRequest(MessageSignals signals) {
        if (signals == null || !signals.system() || !passesBaseGuardrails(signals, 200)) return null;
        List<ClickActionSpec> execs = executableClicks(signals.clicks());
        if (execs.size() != 2) return null;

        ClickActionSpec a = execs.get(0);
        ClickActionSpec b = execs.get(1);
        if (isPaginationOrMenu(a) || isPaginationOrMenu(b)) return null;
        if (isDangerousCommand(a.value()) || isDangerousCommand(b.value())) return null;

        ClickActionSpec accept = null;
        ClickActionSpec deny = null;
        if (isPositiveAction(a) && isNegativeAction(b)) {
            accept = a;
            deny = b;
        } else if (isPositiveAction(b) && isNegativeAction(a)) {
            accept = b;
            deny = a;
        }
        if (accept == null || deny == null) return null;

        String cleanRaw = stripFormatting(signals.rawText()).trim();
        String leadingTag = extractLeadingTag(cleanRaw);
        String body = stripClickLabelsAndPrefix(cleanRaw, List.of(accept.label(), deny.label()));
        if (body.length() < 2) return null;

        DomainMeta domain = inferDomain(cleanRaw, accept.value(), signals.translationKeys(), leadingTag);
        String avatarPlayer = extractRequesterName(body, signals.senderName());
        String cardId = "req:" + domain.key + ":" + REQUEST_SEQ.incrementAndGet();

        String acceptLabel = cleanButtonLabel(accept.label(), "接受");
        String denyLabel = cleanButtonLabel(deny.label(), "拒绝");

        ShareCard card = ShareCard.general(body)
                .cardId(cardId)
                .title(body)
                .subtitle("")
                .source(domain.sourceLabel)
                .badge("")
                .accentColor(domain.accentColor)
                .hudTag(domain.hudTag)
                .hudSummary(body)
                .fallbackPlainText(domain.hudTag + " " + body)
                .generalPayload(new ShareCard.GeneralPayload(body, "", "", "", avatarPlayer, domain.accentColor))
                .addAction("req_accept", acceptLabel, accept.kind(), accept.value(), true)
                .addAction("req_deny", denyLabel, deny.kind(), deny.value(), false)
                .build();
        List<String> runOnly = new ArrayList<>(2);
        if (accept.kind() == ShareCard.ActionKind.RUN_COMMAND) runOnly.add(accept.value());
        if (deny.kind() == ShareCard.ActionKind.RUN_COMMAND) runOnly.add(deny.value());
        return runOnly.isEmpty() ? card : card.withNativeClickCommands(runOnly);
    }

    /**
     * Pattern B: Universal single-button system action notification card (Mail, Auction, Quest, Reward, etc.).
     */
    public static ShareCard matchSingleAction(MessageSignals signals) {
        if (signals == null || !signals.system() || !passesBaseGuardrails(signals, 150)) return null;
        List<ClickActionSpec> execs = executableClicks(signals.clicks());
        if (execs.size() != 1) return null;

        ClickActionSpec act = execs.get(0);
        if (isPaginationOrMenu(act) || isPassiveReceiptAction(act, signals) || isDangerousCommand(act.value())) {
            return null;
        }
        if (!matchesSingleActionVerb(act.label())) return null;

        String cleanRaw = stripFormatting(signals.rawText()).trim();
        String leadingTag = extractLeadingTag(cleanRaw);
        String body = stripClickLabelsAndPrefix(cleanRaw, List.of(act.label()));
        if (body.length() < 3) return null;

        DomainMeta domain = inferDomain(cleanRaw, act.value(), signals.translationKeys(), leadingTag);
        String btnLabel = cleanButtonLabel(act.label(), "查看");
        String cardId = "act:" + domain.key + ":" + REQUEST_SEQ.incrementAndGet();

        ShareCard card = ShareCard.general(body)
                .cardId(cardId)
                .title(body)
                .subtitle("")
                .source(domain.sourceLabel)
                .badge("")
                .accentColor(domain.accentColor)
                .hudTag(domain.hudTag)
                .hudSummary(body)
                .fallbackPlainText(domain.hudTag + " " + body)
                .generalPayload(new ShareCard.GeneralPayload(body, "", "", "", "", domain.accentColor))
                .addAction("act_primary", btnLabel, act.kind(), act.value(), true)
                .build();
        return act.kind() == ShareCard.ActionKind.RUN_COMMAND
                ? card.withNativeClickCommands(List.of(act.value()))
                : card;
    }

    /**
     * Pattern C: Generic coordinate share paired with a teleport/waypoint command or explicit coordinate syntax.
     */
    public static ShareCard matchGenericLocation(MessageSignals signals) {
        if (signals == null || !passesBaseGuardrails(signals, 220)) return null;
        String cleanRaw = stripFormatting(signals.rawText()).trim();
        if (cleanRaw.isEmpty()) return null;

        List<ClickActionSpec> execs = executableClicks(signals.clicks());
        String firstCoordCmd = "";
        for (ClickActionSpec c : execs) {
            String low = c.value().toLowerCase(Locale.ROOT);
            if (low.startsWith("/tp ") || low.startsWith("tp ")
                    || low.startsWith("/stp ") || low.startsWith("stp ")
                    || low.startsWith("/xaero_waypoint") || low.startsWith("xaero_waypoint")
                    || low.contains("waypoint") || low.contains("teleport")) {
                firstCoordCmd = c.value();
                break;
            }
        }

        // Require either a labeled x/y/z coordinate OR a tuple backed by an explicit teleport/waypoint command.
        Double x = null, y = null, z = null;
        Matcher mLabeled = GENERIC_XYZ_LABELED.matcher(cleanRaw);
        if (mLabeled.find()) {
            x = parseSafeDouble(mLabeled.group(1));
            y = parseSafeDouble(mLabeled.group(2));
            z = parseSafeDouble(mLabeled.group(3));
        } else if (!firstCoordCmd.isEmpty()) {
            Matcher mTuple = GENERIC_XYZ_TUPLE.matcher(cleanRaw);
            if (mTuple.find()) {
                x = parseSafeDouble(mTuple.group(1));
                y = parseSafeDouble(mTuple.group(2));
                z = parseSafeDouble(mTuple.group(3));
            } else {
                Matcher mCmd = TP_CMD_XYZ.matcher(firstCoordCmd);
                if (mCmd.find()) {
                    x = parseSafeDouble(mCmd.group(1));
                    y = parseSafeDouble(mCmd.group(2));
                    z = parseSafeDouble(mCmd.group(3));
                }
            }
        }
        if (x == null || y == null || z == null) return null;
        if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -2048 || y > 4096) return null;
        // For system messages without a click command, avoid hijacking death messages or vanilla /locate output unless explicit coord share.
        if (firstCoordCmd.isEmpty() && signals.system()) {
            String low = cleanRaw.toLowerCase(Locale.ROOT);
            boolean hasCoordHint = low.contains("waypoint") || low.contains("路径点")
                    || low.contains("坐标") || low.contains("位置");
            if (!hasCoordHint) return null;
        }

        String dim = inferDimensionFromText(cleanRaw, signals.currentDimension());
        String title = inferLocationTitle(cleanRaw, signals.senderName());
        ShareCard card = BuiltInCardParsers.buildLocationCard(
                title, x, y, z, dim, ShareCard.dimensionAccent(dim), "位置分享", firstCoordCmd);
        if (!firstCoordCmd.isEmpty() && !isDangerousCommand(firstCoordCmd)) {
            card = card.withNativeClickCommands(List.of(firstCoordCmd));
        }
        return card;
    }

    /** Checks if a click command corresponds to an affirmative or negative pair-request action. */
    public static boolean isAcceptCommandOrLabel(String label, String cmd) {
        if (isNegativeLabel(label)) return false;
        if (isPositiveLabel(label)) return true;
        String low = cmd == null ? "" : cmd.toLowerCase(Locale.ROOT);
        if (low.contains("deny") || low.contains("decline") || low.contains("reject")
                || low.contains("cancel") || low.contains("tpno") || low.contains("refuse")) {
            return false;
        }
        return low.contains("accept") || low.contains("yes") || low.contains("confirm")
                || low.contains("agree") || low.contains("join");
    }

    public static boolean isDenyCommandOrLabel(String label, String cmd) {
        if (isNegativeLabel(label)) return true;
        String low = cmd == null ? "" : cmd.toLowerCase(Locale.ROOT);
        return low.contains("deny") || low.contains("decline") || low.contains("reject")
                || low.contains("cancel") || low.contains("tpno") || low.contains("refuse");
    }

    // ------------------------------------------------------------------ internal helpers

    private static boolean passesBaseGuardrails(MessageSignals signals, int maxChars) {
        String clean = stripFormatting(signals.rawText()).trim();
        if (clean.isEmpty() || clean.length() > maxChars) return false;
        int lines = 1;
        for (int i = 0; i < clean.length(); i++) {
            if (clean.charAt(i) == '\n') {
                lines++;
                if (lines > 2) return false;
            }
        }
        if (signals.clicks().size() > 3) return false;
        return true;
    }

    private static List<ClickActionSpec> executableClicks(List<ClickActionSpec> clicks) {
        List<ClickActionSpec> out = new ArrayList<>();
        Set<String> seenCmds = new LinkedHashSet<>();
        for (ClickActionSpec c : clicks) {
            if (c != null && c.isExecutableCommand() && seenCmds.add(c.value())) {
                out.add(c);
            }
        }
        return out;
    }

    private static boolean isPositiveAction(ClickActionSpec c) {
        if (isNegativeLabel(c.label())) return false;
        if (isPositiveLabel(c.label())) return true;
        String low = c.value().toLowerCase(Locale.ROOT);
        return low.startsWith("/tpyes") || low.startsWith("/tpaccept")
                || low.contains(" accept") || low.contains(" yes") || low.contains(" confirm");
    }

    private static boolean isNegativeAction(ClickActionSpec c) {
        if (isNegativeLabel(c.label())) return true;
        String low = c.value().toLowerCase(Locale.ROOT);
        return low.startsWith("/tpno") || low.startsWith("/tpdeny")
                || low.contains(" deny") || low.contains(" decline")
                || low.contains(" reject") || low.contains(" cancel");
    }

    private static boolean isNegativeLabel(String rawLabel) {
        String norm = normalizeButtonToken(rawLabel);
        if (norm.isEmpty()) return false;
        if (NEGATIVE_LABELS.contains(norm)) return true;
        if (isAsciiWord(norm)) return false; // Exact match only for ASCII words (prevents Notch/Noah/Normal false positives)
        for (String token : NEGATIVE_LABELS) {
            if (!isAsciiWord(token) && token.length() >= 2 && (norm.startsWith(token) || norm.endsWith(token))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPositiveLabel(String rawLabel) {
        String norm = normalizeButtonToken(rawLabel);
        if (norm.isEmpty()) return false;
        // Reject negated phrases like "不同意", "不接受", "不允许", "未同意"
        if (norm.startsWith("不") || norm.startsWith("非") || norm.startsWith("未")
                || norm.startsWith("no ") || norm.startsWith("not ") || norm.startsWith("dis") || norm.startsWith("un")) {
            return false;
        }
        if (POSITIVE_LABELS.contains(norm)) return true;
        if (isAsciiWord(norm)) return false; // Exact match only for ASCII words (prevents Book/Look/Okay false positives)
        for (String token : POSITIVE_LABELS) {
            if (!isAsciiWord(token) && token.length() >= 2 && (norm.startsWith(token) || norm.endsWith(token))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsciiWord(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) return false;
        }
        return true;
    }

    private static boolean matchesSingleActionVerb(String rawLabel) {
        String norm = normalizeButtonToken(rawLabel);
        if (norm.isEmpty() || norm.length() > 14) return false;
        for (String v : SINGLE_ACTION_VERBS) {
            if (norm.equals(v) || norm.contains(v)) return true;
        }
        return false;
    }

    private static boolean isPassiveReceiptAction(ClickActionSpec c, MessageSignals signals) {
        String normLabel = normalizeButtonToken(c.label());
        for (String info : PASSIVE_INFO_LABELS) {
            if (normLabel.equals(info) || normLabel.contains(info)) {
                return true;
            }
        }
        String lowCmd = c.value().toLowerCase(Locale.ROOT);
        if (lowCmd.startsWith("/redpacket info") || lowCmd.startsWith("redpacket info")) {
            return true;
        }
        for (String key : signals.translationKeys()) {
            if (key.startsWith("msg.module_shop_redpacket.")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPaginationOrMenu(ClickActionSpec c) {
        String normLabel = normalizeButtonToken(c.label());
        String lowCmd = c.value().toLowerCase(Locale.ROOT);
        if (lowCmd.startsWith("/help") || lowCmd.startsWith("help ")) return true;
        for (String m : PAGINATION_OR_MENU_MARKERS) {
            if (normLabel.contains(m)) return true;
        }
        return false;
    }

    private static String normalizeButtonToken(String raw) {
        if (raw == null) return "";
        String s = stripFormatting(raw).trim();
        while (s.length() >= 2 && ((s.startsWith("[") && s.endsWith("]"))
                || (s.startsWith("【") && s.endsWith("】"))
                || (s.startsWith("(") && s.endsWith(")"))
                || (s.startsWith("《") && s.endsWith("》")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        // Strip leading check/cross icons like ✔ ✘ ✓ ✗ +
        s = s.replaceAll("^[✔✓✘✗×+·\\-\\s]+", "").trim();
        return s.toLowerCase(Locale.ROOT);
    }

    private static String cleanButtonLabel(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = stripFormatting(raw).trim();
        while (s.length() >= 2 && ((s.startsWith("[") && s.endsWith("]"))
                || (s.startsWith("【") && s.endsWith("】"))
                || (s.startsWith("(") && s.endsWith(")")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        s = BuiltInCardParsers.sanitizeLabel(s, 10);
        return s.isEmpty() ? fallback : s;
    }

    private static String extractLeadingTag(String cleanRaw) {
        Matcher m = BRACKET_PREFIX.matcher(cleanRaw);
        if (m.find()) {
            String tag = BuiltInCardParsers.sanitizeLabel(m.group(1), 12);
            if (!tag.equalsIgnoreCase("system") && !tag.equalsIgnoreCase("chat") && !tag.equals("系统")) {
                return tag;
            }
        }
        return "";
    }

    private static String stripClickLabelsAndPrefix(String cleanRaw, List<String> labels) {
        String s = cleanRaw;
        Matcher m = BRACKET_PREFIX.matcher(s);
        if (m.find()) {
            s = s.substring(m.end());
        }
        for (String lbl : labels) {
            if (lbl == null || lbl.isBlank()) continue;
            String plainLbl = stripFormatting(lbl).trim();
            if (!plainLbl.isEmpty()) {
                s = s.replace(plainLbl, " ");
                String inner = cleanButtonLabel(plainLbl, "");
                if (!inner.isEmpty()) {
                    s = s.replace("[" + inner + "]", " ");
                    s = s.replace("【" + inner + "】", " ");
                }
            }
        }
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[|·\\-—:,，：]+$", "").trim();
        return BuiltInCardParsers.sanitizeLabel(s, 64);
    }

    private static String extractRequesterName(String body, String senderName) {
        if (senderName != null && !senderName.isBlank()) {
            return BuiltInCardParsers.sanitizeLabel(senderName, 20);
        }
        Matcher m = PLAYER_TOKEN.matcher(body);
        if (m.find()) {
            String token = m.group(1);
            // Only adopt leading player-like token near the beginning of the sentence
            if (m.start() <= 6) {
                return token;
            }
        }
        return "";
    }

    private record DomainMeta(String key, String sourceLabel, String hudTag, int accentColor) {
    }

    private static DomainMeta inferDomain(String raw, String cmd, List<String> keys, String leadingTag) {
        String low = (raw + " " + cmd + " " + String.join(" ", keys)).toLowerCase(Locale.ROOT);
        if (low.contains("stellarrtp")) {
            return new DomainMeta("tpa", "繁星传送", "[传送]", 0xFF0EA5E9);
        }
        if (low.contains("tpa") || low.contains("tpyes") || low.contains("tpaccept") || raw.contains("传送")) {
            return new DomainMeta("tpa", leadingTag.isEmpty() ? "传送" : leadingTag, "[传送]", 0xFF0EA5E9);
        }
        if (low.contains("party") || low.contains("team") || raw.contains("组队") || raw.contains("队伍")) {
            return new DomainMeta("party", leadingTag.isEmpty() ? "组队" : leadingTag, "[组队]", 0xFF6366F1);
        }
        if (low.contains("guild") || low.contains("clan") || low.contains("faction") || raw.contains("公会") || raw.contains("帮派")) {
            return new DomainMeta("guild", leadingTag.isEmpty() ? "公会" : leadingTag, "[公会]", 0xFFF59E0B);
        }
        if (low.contains("trade") || low.contains("exchange") || raw.contains("交易")) {
            return new DomainMeta("trade", leadingTag.isEmpty() ? "交易" : leadingTag, "[交易]", 0xFF10B981);
        }
        if (low.contains("duel") || low.contains("pvp") || raw.contains("决斗") || raw.contains("切磋")) {
            return new DomainMeta("duel", leadingTag.isEmpty() ? "决斗" : leadingTag, "[决斗]", 0xFFF43F5E);
        }
        if (low.contains("friend") || raw.contains("好友")) {
            return new DomainMeta("friend", leadingTag.isEmpty() ? "好友" : leadingTag, "[好友]", 0xFFEC4899);
        }
        if (low.contains("mail") || raw.contains("邮件") || raw.contains("邮箱")) {
            return new DomainMeta("mail", leadingTag.isEmpty() ? "邮件" : leadingTag, "[邮件]", 0xFF3B82F6);
        }
        if (low.contains("auction") || low.contains("/ah") || raw.contains("拍卖")) {
            return new DomainMeta("auction", leadingTag.isEmpty() ? "拍卖行" : leadingTag, "[拍卖]", 0xFFD97706);
        }
        if (low.contains("quest") || raw.contains("任务")) {
            return new DomainMeta("quest", leadingTag.isEmpty() ? "任务" : leadingTag, "[任务]", 0xFF8B5CF6);
        }
        if (low.contains("claim") || low.contains("residence") || low.contains("plot") || raw.contains("领地")) {
            return new DomainMeta("claim", leadingTag.isEmpty() ? "领地" : leadingTag, "[领地]", 0xFF14B8A6);
        }
        return new DomainMeta("action", leadingTag, "[互动]", 0xFF3B82F6);
    }

    private static String inferDimensionFromText(String raw, String fallbackDim) {
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.contains("the_nether") || low.contains("nether") || raw.contains("下界") || raw.contains("地狱")) {
            return "minecraft:the_nether";
        }
        if (low.contains("the_end") || raw.contains("末地")) {
            return "minecraft:the_end";
        }
        if (low.contains("overworld") || raw.contains("主世界")) {
            return "minecraft:overworld";
        }
        return BuiltInCardParsers.normalizeDimension(fallbackDim);
    }

    private static String inferLocationTitle(String raw, String senderName) {
        String s = raw;
        if (s.startsWith("<")) {
            int end = s.indexOf("> ");
            if (end > 0) s = s.substring(end + 2);
        }
        Matcher mPrefix = BRACKET_PREFIX.matcher(s);
        if (mPrefix.find()) {
            s = s.substring(mPrefix.end());
        }
        s = GENERIC_XYZ_LABELED.matcher(s).replaceAll(" ");
        s = GENERIC_XYZ_TUPLE.matcher(s).replaceAll(" ");
        s = s.replaceAll("\\[[^\\]]{1,12}\\]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[|·\\-—:,，：]+$", "").trim();
        String clean = BuiltInCardParsers.sanitizeLabel(s, 28);
        if (clean.length() >= 2) return clean;
        if (senderName != null && !senderName.isBlank()) {
            return BuiltInCardParsers.sanitizeLabel(senderName + " 的坐标", 28);
        }
        return "坐标位置";
    }

    private static Double parseSafeDouble(String s) {
        if (s == null) return null;
        try {
            double v = Double.parseDouble(s.trim());
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stripFormatting(String raw) {
        return raw == null ? "" : raw.replaceAll("§.", "");
    }
}
