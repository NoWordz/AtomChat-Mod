package com.atom.chat.card;

import com.atom.chat.api.card.BuiltInCardParsers;
import com.atom.chat.api.card.ComposerLocationCard;
import com.atom.chat.api.card.CardRegistry;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.UniversalCardMatcher;
import com.atom.chat.api.card.TeleportRequestSession;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Client-side click executor for {@link ShareCard} actions, with strict command/dimension
 * validation, cross-map waypoint saving (Xaero / JourneyMap) and StellarRTP non-OP teleport support.
 */
public final class CardActionExecutor {

    private static final Pattern SAFE_XAERO_ADD = Pattern.compile("^xaero_waypoint_add:[^\\r\\n§]{1,200}$");

    public interface FeedbackSink {
        void toast(String message);
        void suggestInComposer(String text);
        void openUrl(String url);
        default void runComponentCommand(String slashCommand) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null && client.player.connection != null && slashCommand != null) {
                String clean = slashCommand.startsWith("/") ? slashCommand.substring(1) : slashCommand;
                client.player.connection.sendCommand(clean);
            }
        }
    }

    private CardActionExecutor() {
    }

    public static boolean execute(ShareCard card, CardState state, ShareCard.CardAction action, FeedbackSink sink) {
        if (card == null || !actionEnabled(action, state)) return false;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.player.connection == null) {
            return false;
        }
        return switch (action.kind()) {
            case RUN_COMMAND -> runCommand(card, action, client, sink);
            case SUGGEST_COMMAND -> {
                if (sink != null && !action.value().isBlank()) {
                    sink.suggestInComposer(action.value());
                }
                yield true;
            }
            case COPY_TEXT -> {
                client.keyboardHandler.setClipboard(action.value());
                if (sink != null) sink.toast(ComposerLocationCard.isComposerLocation(card)
                        ? net.minecraft.client.resources.language.I18n.get("atomchat.feedback.copied") : "已复制到剪贴板");
                yield true;
            }
            case OPEN_URL -> {
                String url = action.value().trim();
                String lowUrl = url.toLowerCase(Locale.ROOT);
                if (sink != null && (lowUrl.startsWith("http://") || lowUrl.startsWith("https://"))
                        && !containsControlOrFormatting(url)) {
                    sink.openUrl(url);
                    yield true;
                }
                yield false;
            }
            case ADD_WAYPOINT -> addWaypoint(card, client, sink);
            case TELEPORT_LOCATION -> teleportLocation(card, client, sink);
            case CLIENT_CALLBACK -> CardRegistry.dispatchCallback(
                    new CardRegistry.ActionCallbackContext(card, state, action));
        };
    }

    public static boolean actionEnabled(ShareCard.CardAction action, CardState state) {
        return action != null && (state == null || !state.disableActions());
    }

    private static boolean runCommand(ShareCard card, ShareCard.CardAction action,
                                      Minecraft client, FeedbackSink sink) {
        String raw = action.value().trim();
        if (raw.isEmpty() || containsControlOrFormatting(raw)) return false;
        String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
        boolean nativeSafe = card.isNativeClickCommand(raw) && !UniversalCardMatcher.isDangerousCommand(cmd);
        boolean teleportResponse = card.cardId() != null && card.cardId().startsWith("tpa:")
                && action.id().startsWith("respond_tpa_");
        boolean inferredResponse = teleportResponse && UniversalCardMatcher.isTargetedTeleportResponse(cmd);
        if (!card.serverAuthoritative() && !nativeSafe && !inferredResponse && !isSafeClientCommand(cmd)) {
            if (sink != null) sink.toast("已拦截非授权卡片指令");
            return false;
        }
        if (teleportResponse && !card.serverAuthoritative() && !TeleportRequestSession.beginAction(card)) return false;
        client.player.connection.sendCommand(cmd);
        return true;
    }

    /** Strict allowlist for commands initiated by client-adapted cards (not server-signed). */
    private static boolean isSafeClientCommand(String cmd) {
        String lower = cmd.toLowerCase(Locale.ROOT);
        return lower.equals("tpyes")
                || lower.equals("tpno")
                || lower.equals("tpaccept")
                || lower.equals("tpdeny")
                || SAFE_XAERO_ADD.matcher(cmd).matches()
                || lower.matches("^redpacket (claim|info) [a-z0-9_-]{1,24}$")
                || lower.matches("^(tp|stp) -?\\d+ -?\\d+ -?\\d+$");
    }

    private static boolean addWaypoint(ShareCard card, Minecraft client, FeedbackSink sink) {
        ShareCard.LocationPayload loc = card.location();
        if (loc == null) return false;
        if (isModLoaded("xaerominimap")) {
            String cmd = loc.nativeAddCommand() == null ? "" : loc.nativeAddCommand().trim();
            String stripped = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            if (SAFE_XAERO_ADD.matcher(stripped).matches()) {
                dispatchComponentCommand("/" + stripped, client, sink);
                if (sink != null) sink.toast("已发送到 Xaero 路径点");
                return true;
            }
            String safeName = BuiltInCardParsers.sanitizeLabel(loc.name(), 32).replace(':', ' ');
            if (safeName.isEmpty()) safeName = "Waypoint";
            String initial = safeName.substring(0, 1).toUpperCase(Locale.ROOT);
            String xaeroDim = toXaeroDimension(BuiltInCardParsers.normalizeDimension(loc.dimension()));
            String xaeroCmd = "/xaero_waypoint_add:" + safeName + ":" + initial + ":"
                    + loc.blockX() + ":" + loc.blockY() + ":" + loc.blockZ()
                    + ":10:false:0:" + xaeroDim;
            dispatchComponentCommand(xaeroCmd, client, sink);
            locationFeedback(card, sink, "waypoint_added", "已添加到 Xaero 路径点");
            return true;
        }
        String coords = BuiltInCardParsers.sanitizeLabel(loc.name(), 32)
                + " (" + loc.blockX() + ", " + loc.blockY() + ", " + loc.blockZ()
                + ", " + locationDimension(card, loc.dimension()) + ")";
        client.keyboardHandler.setClipboard(coords);
        String copiedCoords = loc.blockX() + ", " + loc.blockY() + ", " + loc.blockZ();
        locationFeedback(card, sink, "coords_copied", "已复制坐标：" + copiedCoords, copiedCoords);
        return true;
    }

    private static void dispatchComponentCommand(String slashCmd, Minecraft client, FeedbackSink sink) {
        if (sink != null) {
            sink.runComponentCommand(slashCmd);
        } else {
            String clean = slashCmd.startsWith("/") ? slashCmd.substring(1) : slashCmd;
            client.player.connection.sendCommand(clean);
        }
    }

    private static boolean teleportLocation(ShareCard card, Minecraft client, FeedbackSink sink) {
        ShareCard.LocationPayload loc = card.location();
        if (loc == null || client.level == null) return false;
        String dim = BuiltInCardParsers.normalizeDimension(loc.dimension());
        if (!BuiltInCardParsers.isValidDimension(dim)) {
            locationFeedback(card, sink, "invalid_dimension", "坐标维度格式无效");
            return false;
        }
        String currentDim = client.level.dimension().location().toString();
        boolean sameDim = currentDim.equalsIgnoreCase(dim);
        boolean hasStellarRtp = hasServerCommand(client, "stp");
        boolean isOp = client.player.hasPermissions(2);

        if (sameDim && (hasStellarRtp || isOp)) {
            client.player.connection.sendCommand("tp " + loc.blockX() + " " + loc.blockY() + " " + loc.blockZ());
            if (hasStellarRtp) locationFeedback(card, sink, "stellar_teleporting", "已发起繁星安全传送…");
            else locationFeedback(card, sink, "teleporting", "正在传送至目标坐标…");
            return true;
        }
        if (!sameDim && isOp) {
            if (!serverHasDimension(client, dim)) {
                locationFeedback(card, sink, "missing_dimension", "服务器不存在目标维度：" + dim, dim);
                return false;
            }
            client.player.connection.sendCommand("execute in " + dim
                    + " run tp @s " + loc.blockX() + " " + loc.blockY() + " " + loc.blockZ());
            locationFeedback(card, sink, "teleporting_cross_dimension", "正在跨维度传送…");
            return true;
        }
        if (!sameDim && hasStellarRtp) {
            String dimensionName = locationDimension(card, dim);
            locationFeedback(card, sink, "switch_dimension", "目标在「" + dimensionName + "」，请切换维度后再传送",
                    dimensionName);
            return true;
        }
        String coords = loc.blockX() + " " + loc.blockY() + " " + loc.blockZ();
        client.keyboardHandler.setClipboard(coords);
        locationFeedback(card, sink, "no_teleport", "无传送权限，已复制坐标：" + coords, coords);
        return true;
    }

    private static String locationDimension(ShareCard card, String dimension) {
        return ComposerLocationCard.isComposerLocation(card) ? ComposerLocationCard.dimensionName(dimension, net.minecraft.client.resources.language.I18n::get)
                : BuiltInCardParsers.dimensionDisplayName(dimension);
    }

    private static void locationFeedback(ShareCard card, FeedbackSink sink, String suffix, String legacy, Object... args) {
        if (sink != null) sink.toast(ComposerLocationCard.isComposerLocation(card)
                ? net.minecraft.client.resources.language.I18n.get(ComposerLocationCard.PREFIX + suffix, args) : legacy);
    }

    private static boolean serverHasDimension(Minecraft client, String dimId) {
        try {
            var levels = client.player.connection.levels();
            if (levels == null || levels.isEmpty()) return true;
            for (var key : levels) {
                if (key.location().toString().equals(dimId)) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean hasServerCommand(Minecraft client, String rootName) {
        try {
            var root = client.player.connection.getCommands().getRoot();
            return root.getChild(rootName) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsControlOrFormatting(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '§') return true;
        }
        return false;
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

    private static String toXaeroDimension(String dim) {
        if (dim == null || dim.contains("overworld")) return "Internal-overworld-waypoints";
        if (dim.contains("nether")) return "Internal-the-nether-waypoints";
        if (dim.contains("the_end")) return "Internal-the-end-waypoints";
        return "Internal-dim%" + dim.replace(':', '$') + "-waypoints";
    }
}
