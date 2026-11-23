package com.atom.chat.api.card;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java parsers for the most common map waypoint &amp; TPA chat formats:
 * <ul>
 *   <li>Xaero's Minimap / World Map ({@code xaero-waypoint:...}, {@code xaero_waypoint:...}, {@code xaero_waypoint_add:...})</li>
 *   <li>JourneyMap &amp; VoxelMap ({@code [name:..., x:..., y:..., z:..., dim:...]})</li>
 *   <li>FTB Chunks coordinate shares</li>
 *   <li>StellarRTP &amp; common TPA accept/deny chat requests</li>
 * </ul>
 */
public final class BuiltInCardParsers {

    /** Matches JourneyMap / VoxelMap bracketed waypoint syntax: [name:"Base", x:10, y:64, z:-20, dim:minecraft:overworld] */
    private static final Pattern BRACKET_WAYPOINT = Pattern.compile("\\[([^\\]]{6,300})\\]");
    private static final Pattern KV_PAIR = Pattern.compile("(?i)(name|x|y|z|dim|dimension|color)\\s*[:=]\\s*(?:\"([^\"]*)\"|'([^']*)'|([^,\\]]+))");
    private static final Pattern VALID_DIMENSION = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final java.util.concurrent.atomic.AtomicLong TPA_SEQUENCE = new java.util.concurrent.atomic.AtomicLong();

    /** Xaero standard 16 waypoint colors mapped to vibrant ARGB accents. */
    private static final int[] XAERO_COLORS = {
            0xFF1F2937, 0xFF1D4ED8, 0xFF15803D, 0xFF0E7490,
            0xFFB91C1C, 0xFF7E22CE, 0xFFD97706, 0xFF64748B,
            0xFF475569, 0xFF3B82F6, 0xFF10B981, 0xFF06B6D4,
            0xFFEF4444, 0xFFEC4899, 0xFFEAB308, 0xFF10B981
    };

    private BuiltInCardParsers() {
    }

    /**
     * Parses a Xaero waypoint chat payload into a {@link ShareCard.Template#LOCATION} card,
     * or {@code null} when the line is not a valid Xaero waypoint.
     *
     * <p>Accepts both raw {@code xaero-waypoint:...} chat text (when the viewer has no Xaero
     * client mod rewriting chat) and Xaero's localized rewritten chat messages where the
     * structured payload lives inside {@code nativeAddCommand} ({@code /xaero_waypoint_add:...}).
     */
    public static ShareCard parseXaeroWaypoint(String rawLine, String nativeAddCommand) {
        return parseXaeroWaypoint(rawLine, nativeAddCommand, "minecraft:overworld");
    }

    public static ShareCard parseXaeroWaypoint(String rawLine, String nativeAddCommand, String fallbackDimension) {
        String body = extractXaeroBody(rawLine);
        if (body == null) {
            body = extractXaeroBody(nativeAddCommand);
        }
        if (body == null) {
            return null;
        }
        String[] parts = body.split(":", -1);
        // Format: Name:Initials:X:Y:Z:Color:Rotation:Yaw:Type:Dimension (some variants omit Rotation/Yaw)
        if (parts.length < 5) return null;
        String name = sanitizeLabel(parts[0].replace("^col^", ":"), 32);
        if (name.isEmpty()) name = "Xaero 路径点";
        Double x = parseCoord(parts[2]);
        Double y = parseCoordOrDefault(parts[3], 64.0);
        Double z = parseCoord(parts[4]);
        if (x == null || z == null) return null;

        int colorIdx = parts.length > 5 ? parseIntOrDefault(parts[5], 10) : 10;
        String dimRaw = parts.length > 9 ? parts[9]
                : (parts.length > 8 ? parts[8] : (parts.length > 6 ? parts[parts.length - 1] : ""));
        String dimension = normalizeXaeroDimension(dimRaw, fallbackDimension);
        int accent = colorIdx >= 0 && colorIdx < XAERO_COLORS.length
                ? XAERO_COLORS[colorIdx] : ShareCard.dimensionAccent(dimension);

        String cmd;
        if (nativeAddCommand != null && !nativeAddCommand.isBlank()) {
            String trimmed = nativeAddCommand.trim();
            cmd = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        } else {
            cmd = "/xaero_waypoint_add:" + body;
        }

        return buildLocationCard(name, x, y, z, dimension, accent, "Xaero 地图", cmd);
    }

    private static String extractXaeroBody(String input) {
        if (input == null || input.isBlank()) return null;
        String payload = stripLeadingSpeaker(input).trim();
        if (payload.startsWith("/")) {
            payload = payload.substring(1).trim();
        }
        String lower = payload.toLowerCase(Locale.ROOT);
        if (lower.startsWith("xaero-waypoint:")) {
            return payload.substring("xaero-waypoint:".length());
        }
        if (lower.startsWith("xaero_waypoint:")) {
            return payload.substring("xaero_waypoint:".length());
        }
        if (lower.startsWith("xaero_waypoint_add:")) {
            return payload.substring("xaero_waypoint_add:".length());
        }
        return null;
    }

    /**
     * Parses JourneyMap / VoxelMap bracketed coordinate messages:
     * {@code [name:"Home", x:128, y:64, z:-320, dim:minecraft:overworld]}.
     */
    public static ShareCard parseBracketWaypoint(String rawLine, String nativeAddCommand, String preferredSource) {
        if (rawLine == null || !rawLine.contains("[")) return null;
        Matcher m = BRACKET_WAYPOINT.matcher(rawLine);
        while (m.find()) {
            String inner = m.group(1);
            String name = null;
            Double x = null, y = null, z = null;
            String dim = "minecraft:overworld";
            Matcher kv = KV_PAIR.matcher(inner);
            while (kv.find()) {
                String key = kv.group(1).toLowerCase(Locale.ROOT);
                String val = kv.group(2) != null ? kv.group(2)
                        : (kv.group(3) != null ? kv.group(3) : kv.group(4));
                if (val == null) continue;
                val = val.trim();
                switch (key) {
                    case "name" -> name = val;
                    case "x" -> x = parseCoord(val);
                    case "y" -> y = parseCoordOrDefault(val, 64.0);
                    case "z" -> z = parseCoord(val);
                    case "dim", "dimension" -> dim = normalizeDimension(val);
                    default -> { }
                }
            }
            if (x != null && z != null) {
                double finalY = y != null ? y : 64.0;
                String title = (name != null && !name.isBlank()) ? name : "分享坐标";
                String source = (preferredSource != null && !preferredSource.isBlank())
                        ? preferredSource : "JourneyMap";
                int accent = ShareCard.dimensionAccent(dim);
                return buildLocationCard(title, x, finalY, z, dim, accent, source, nativeAddCommand);
            }
        }
        return null;
    }

    /**
     * Parses common FTB Chunks waypoint/coordinate share messages such as
     * {@code [X, Y, Z]} when paired with a known FTB marker or dimension hint.
     */
    public static ShareCard parseFtbCoordinate(String rawLine, String dimensionHint, String nativeCommand) {
        if (rawLine == null) return null;
        String lower = rawLine.toLowerCase(Locale.ROOT);
        if (!lower.contains("ftb") && (nativeCommand == null || !nativeCommand.toLowerCase(Locale.ROOT).contains("ftb"))) {
            return null;
        }
        Matcher m = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\]").matcher(rawLine);
        if (!m.find()) return null;
        double x = Double.parseDouble(m.group(1));
        double y = Double.parseDouble(m.group(2));
        double z = Double.parseDouble(m.group(3));
        String dim = normalizeDimension(dimensionHint);
        return buildLocationCard("FTB 区块坐标", x, y, z, dim, ShareCard.dimensionAccent(dim), "FTB 区块", nativeCommand);
    }

    /**
     * Builds a standard interactive {@link ShareCard.Template#LOCATION} card with
     * save-waypoint, teleport and copy actions.
     */
    public static ShareCard buildLocationCard(String name, double x, double y, double z,
                                              String dimension, int accent, String source,
                                              String nativeAddCommand) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        String cleanName = sanitizeLabel(name, 32);
        if (cleanName.isEmpty()) cleanName = "坐标位置";
        String cleanSource = sanitizeLabel(source, 24);
        if (cleanSource.isEmpty()) cleanSource = "位置分享";
        String dim = normalizeDimension(dimension);
        String coordText = bx + " " + by + " " + bz;
        ShareCard.LocationPayload loc = new ShareCard.LocationPayload(
                cleanName, x, y, z, dim, accent, cleanSource, nativeAddCommand);
        return ShareCard.location(cleanName, x, y, z, dim)
                .accentColor(accent)
                .source(cleanSource)
                .locationPayload(loc)
                .subtitle("X: " + bx + ", Y: " + by + ", Z: " + bz)
                .hudTag("[坐标]")
                .hudSummary(cleanName + " (" + bx + ", " + by + ", " + bz + ")")
                .fallbackPlainText("[坐标] " + cleanName + " (" + bx + ", " + by + ", " + bz + ", " + dimensionDisplayName(dim) + ")")
                .bodyAction(new ShareCard.CardAction("save_wp", "+ 路径点", ShareCard.ActionKind.ADD_WAYPOINT, "", true))
                .addAction("save_wp", "+ 路径点", ShareCard.ActionKind.ADD_WAYPOINT, "", true)
                .addAction("tp_loc", "传送", ShareCard.ActionKind.TELEPORT_LOCATION, coordText, false)
                .addAction("copy_loc", "复制", ShareCard.ActionKind.COPY_TEXT, coordText, false)
                .build();
    }

    /**
     * Builds a StellarRTP / generic TPA interactive request card with Accept &amp; Deny buttons
     * and a deterministic {@code cardId} so follow-up status lines can dim it in place.
     */
    public static ShareCard buildTpaRequestCard(String requesterName, boolean toHere,
                                                String acceptCommand, String denyCommand,
                                                String sourceLabel) {
        String sender = (requesterName == null || requesterName.isBlank()) ? "玩家" : requesterName.trim();
        String cardId = tpaCardId(sender);
        String title = toHere ? (sender + " 邀请你传送过去") : (sender + " 请求传送到你这里");
        String desc = toHere
                ? "同意后将传送至对方所在位置"
                : "同意后对方将传送至你的位置";
        String acceptCmd = (acceptCommand == null || acceptCommand.isBlank()) ? "/tpyes" : acceptCommand;
        String denyCmd = (denyCommand == null || denyCommand.isBlank()) ? "/tpno" : denyCommand;
        String src = (sourceLabel == null || sourceLabel.isBlank()) ? "繁星传送" : sourceLabel;
        return ShareCard.general(title)
                .cardId(cardId)
                .subtitle(desc)
                .source(src)
                .badge("")
                .accentColor(0xFF0EA5E9)
                .hudTag("[传送]")
                .hudSummary(title)
                .fallbackPlainText("[传送] " + title)
                .generalPayload(new ShareCard.GeneralPayload(title, desc, "", "传", sender, 0xFF0EA5E9))
                .addAction("tpa_accept", "接受", ShareCard.ActionKind.RUN_COMMAND, acceptCmd, true)
                .addAction("tpa_deny", "拒绝", ShareCard.ActionKind.RUN_COMMAND, denyCmd, false)
                .build();
    }

    /**
     * Builds a fallback {@link ShareCard.Template#RED_PACKET} card from vanilla Module-Shop
     * red-packet chat announcements (for servers running older RedPacket builds or without
     * server-side AtomChat installed).
     */
    public static ShareCard buildRedPacketCard(String packetId, String greeting, boolean lucky,
                                               boolean hasPassword, String password,
                                               int totalCount, String currency,
                                               String clickCommand, boolean suggestPassword) {
        if (packetId == null || !packetId.matches("^[A-Za-z0-9_-]{1,24}$")) {
            return null;
        }
        String cleanGreeting = sanitizeLabel(greeting, 32);
        if (cleanGreeting.isEmpty()) {
            cleanGreeting = "恭喜发财，大吉大利";
        }
        String cleanPassword = hasPassword ? sanitizeLabel(password, 24) : "";
        String typeLabel = lucky ? "拼手气红包" : "普通红包";
        String status = (hasPassword && !cleanPassword.isEmpty())
                ? (typeLabel + " · 口令：" + cleanPassword)
                : (typeLabel + " · 点击领取");
        String badge = totalCount > 0 ? ("共 " + totalCount + " 个") : "";
        String cleanCurrency = sanitizeLabel(currency, 16);
        String effectiveClick = (clickCommand == null || clickCommand.isBlank())
                ? (suggestPassword ? cleanPassword : "/redpacket claim " + packetId)
                : clickCommand.trim();
        ShareCard.Builder b = ShareCard.redPacket("rp:" + packetId)
                .title(cleanGreeting)
                .subtitle(status)
                .source("模块商店 · 红包")
                .badge(badge)
                .redPacketPayload(new ShareCard.RedPacketPayload(
                        packetId, cleanGreeting, status, lucky, hasPassword,
                        cleanPassword, 0, Math.max(0, totalCount), cleanCurrency, false))
                .hudTag("[红包]")
                .hudSummary(cleanGreeting)
                .fallbackPlainText("[红包] " + cleanGreeting + " (" + status + ")");
        if (suggestPassword) {
            b.onClickSuggest(effectiveClick);
        } else {
            b.onClickCommand(effectiveClick);
        }
        return b.build();
    }

    public static String tpaCardId(String requesterName) {
        String norm = requesterName == null ? "latest" : requesterName.trim().toLowerCase(Locale.ROOT);
        return "tpa:" + norm + ":" + TPA_SEQUENCE.incrementAndGet();
    }

    /** Strict ResourceLocation validation preventing command injection via crafted dimension strings. */
    public static boolean isValidDimension(String dim) {
        return dim != null && !dim.isEmpty() && dim.length() <= 96 && VALID_DIMENSION.matcher(dim).matches();
    }

    /** Strips formatting codes, control chars and trims to {@code maxLen}. */
    public static String sanitizeLabel(String raw, int maxLen) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("§.", "");
        StringBuilder sb = new StringBuilder(Math.min(cleaned.length(), maxLen));
        for (int i = 0; i < cleaned.length() && sb.length() < maxLen; i++) {
            char c = cleaned.charAt(i);
            if (c >= 0x20 && c != 0x7F && c != '§') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    public static String dimensionDisplayName(String dim) {
        if (dim == null || dim.isBlank()) return "主世界";
        String lower = dim.toLowerCase(Locale.ROOT);
        if (lower.contains("overworld")) return "主世界";
        if (lower.contains("the_nether") || lower.contains("nether")) return "下界";
        if (lower.contains("the_end") || lower.endsWith(":end")) return "末地";
        int colon = dim.indexOf(':');
        return colon >= 0 && colon + 1 < dim.length() ? dim.substring(colon + 1) : dim;
    }

    public static String normalizeXaeroDimension(String raw) {
        return normalizeXaeroDimension(raw, "minecraft:overworld");
    }

    public static String normalizeXaeroDimension(String raw, String fallbackDimension) {
        String fallback = normalizeDimension(fallbackDimension);
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if ("internal".equals(lower) || "internal-waypoints".equals(lower) || "external".equals(lower)) {
            return fallback;
        }
        if (lower.contains("overworld")) return "minecraft:overworld";
        if (lower.contains("the-nether") || lower.contains("the_nether") || lower.contains("nether")) {
            return "minecraft:the_nether";
        }
        if (lower.contains("the-end") || lower.contains("the_end") || lower.contains("-end-") || "end".equals(lower)) {
            return "minecraft:the_end";
        }
        // Xaero custom dimension format: Internal-dim%namespace$path-waypoints
        int pct = s.indexOf("dim%");
        if (pct >= 0) {
            String rest = s.substring(pct + 4);
            if (rest.endsWith("-waypoints")) {
                rest = rest.substring(0, rest.length() - "-waypoints".length());
            }
            return normalizeDimension(rest.replace('$', ':'));
        }
        return s.contains(":") ? normalizeDimension(s) : fallback;
    }

    public static String normalizeDimension(String raw) {
        if (raw == null || raw.isBlank()) return "minecraft:overworld";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("0".equals(s) || "overworld".equals(s)) return "minecraft:overworld";
        if ("-1".equals(s) || "nether".equals(s) || "the_nether".equals(s)) return "minecraft:the_nether";
        if ("1".equals(s) || "end".equals(s) || "the_end".equals(s)) return "minecraft:the_end";
        String candidate = s.contains(":") ? s : "minecraft:" + s;
        return isValidDimension(candidate) ? candidate : "minecraft:overworld";
    }

    private static String stripLeadingSpeaker(String raw) {
        if (raw.startsWith("<")) {
            int end = raw.indexOf("> ");
            if (end > 0) return raw.substring(end + 2);
        }
        int colon = raw.indexOf("xaero");
        if (colon > 0) {
            String lower = raw.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf("xaero-waypoint:");
            if (idx < 0) idx = lower.indexOf("xaero_waypoint:");
            if (idx < 0) idx = lower.indexOf("xaero_waypoint_add:");
            if (idx >= 0) return raw.substring(idx);
        }
        return raw;
    }

    private static Double parseCoord(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || "~".equals(s)) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseCoordOrDefault(String raw, double fallback) {
        Double parsed = parseCoord(raw);
        return parsed != null ? parsed : fallback;
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
