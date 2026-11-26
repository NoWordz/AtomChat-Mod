package com.atom.chat.api.card;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.atom.chat.api.card.ShareCard.ActionKind.RUN_COMMAND;
import static com.atom.chat.api.card.UniversalCardMatcher.ClickActionSpec;
import static com.atom.chat.api.card.UniversalCardMatcher.MessageSignals;

final class TeleportRequestMatcher {
    static final String STELLAR_TO = "stellarrtp.tpa.request_received_to_player";
    static final String STELLAR_HERE = "stellarrtp.tpa.request_received_here";
    static final String STELLAR_PREFIX = "[StellarRTP] ";
    private static final String PLAYER = "[A-Za-z0-9_]{1,16}";
    private static final Pattern NAME = Pattern.compile(PLAYER);
    private static final Pattern TEXT = Pattern.compile(
            "^(?:\\[[^\\]\\r\\n]{1,24}\\] *)?(" + PLAYER + ") *(has requested to teleport to you\\.|"
                    + "has requested that you teleport to them\\.|请求传送到你这里。|请求你传送到其位置。"
                    + "|请求你传送到他那里。|has requested for you to teleport to them\\."
                    + "|wants to teleport to you\\.|wants you to teleport to them\\."
                    + "|想要传送到你这里。|希望你传送到他那里。)$");
    private static final Pattern ACTION = Pattern.compile(
            "^/?(?:(essentials|huskhomes|stellarrtp):)?(tpyes|tpaccept|tpno|tpdeny|tpdecline)(?: (" + PLAYER + "))?$",
            Pattern.CASE_INSENSITIVE);

    private TeleportRequestMatcher() {}

    static ShareCard match(MessageSignals signals, String translatedRequester, boolean toHere,
                           Predicate<String> offersCommand) {
        if (signals == null || !signals.system() || !signals.senderName().isBlank() || signals.hasItemHover() || signals.rawText().length() > 300) return null;
        String text = stripClickSuffix(signals);
        boolean stellarPrefix = text.startsWith(STELLAR_PREFIX);
        if (stellarPrefix) text = text.substring(STELLAR_PREFIX.length()).stripLeading();
        boolean stellarKey = signals.translationKeys().contains(STELLAR_TO) || signals.translationKeys().contains(STELLAR_HERE);
        boolean stellar = stellarKey || stellarPrefix;
        boolean stellarName = stellarKey && NAME.matcher(translatedRequester).matches();
        String player = stellarName ? translatedRequester : "";
        String source = stellar ? "StellarRTP" : "";
        var plain = TEXT.matcher(text);
        boolean knownText = plain.matches();
        if (knownText) {
            if (player.isEmpty()) player = plain.group(1);
            String direction = plain.group(2);
            toHere = direction.contains("them") || direction.startsWith("请求你") || direction.startsWith("希望你");
        }

        var actions = new LinkedHashMap<Boolean, ClickActionSpec>();
        String boundPlayer = "";
        boolean ambiguous = false;
        boolean hasTeleportClicks = false;
        for (var click : signals.clicks()) {
            if (!click.isExecutableCommand()) continue;
            var action = ACTION.matcher(click.value());
            if (!action.matches()) continue;
            hasTeleportClicks = true;
            String namespace = action.group(1);
            String target = action.group(3);
            if (target == null) continue; // "most recent" can change while the card is on screen.
            if ((!player.isEmpty() && !player.equalsIgnoreCase(target))
                    || (!boundPlayer.isEmpty() && !boundPlayer.equalsIgnoreCase(target))) {
                ambiguous = true;
                continue;
            }
            boundPlayer = target;
            if (namespace != null) {
                String plugin = switch (namespace.toLowerCase(Locale.ROOT)) {
                    case "essentials" -> "EssentialsX";
                    case "huskhomes" -> "HuskHomes";
                    default -> "StellarRTP";
                };
                if (!source.isEmpty() && !source.equals(plugin)) ambiguous = true;
                else source = plugin;
            }
            boolean accept = action.group(2).equalsIgnoreCase("tpaccept") || action.group(2).equalsIgnoreCase("tpyes");
            var previous = actions.putIfAbsent(accept, click);
            if (previous != null && !previous.equals(click)) ambiguous = true;
        }
        if (!knownText && !stellarKey && actions.size() != 2) return null;
        if (player.isEmpty()) player = boundPlayer;
        if (ambiguous) actions.clear();

        if (!hasTeleportClicks && signals.clicks().stream().noneMatch(ClickActionSpec::isExecutableCommand)
                && !player.isEmpty() && (stellar || !offersCommand.test("huskhomes:tpaccept"))) {
            String prefix = stellar ? "" : "essentials:";
            String accept = prefix + "tpaccept", deny = prefix + "tpdeny";
            if (offersCommand.test(accept) && offersCommand.test(deny)) {
                if (!stellar) source = "EssentialsX";
                actions.put(true, new ClickActionSpec("接受", RUN_COMMAND, "/" + accept + " " + player));
                actions.put(false, new ClickActionSpec("拒绝", RUN_COMMAND, "/" + deny + " " + player));
            }
        }
        String title;
        if (knownText || player.isEmpty()) {
            title = text;
        } else if (text.contains("stellarrtp.")) {
            title = toHere ? player + " wants you to teleport to them." : player + " wants to teleport to you.";
        } else if (stellar) {
            title = text;
        } else {
            title = toHere ? player + " 邀请你传送过去" : player + " 请求传送到你这里";
        }
        if (title.isBlank()) return null;
        var builder = ShareCard.general(title).cardId(BuiltInCardParsers.tpaCardId(player))
                .source(source).accentColor(0xFF0EA5E9)
                .generalPayload(new ShareCard.GeneralPayload(title, "", "", "", player, 0xFF0EA5E9))
                .hudTag("[传送]").hudSummary(title).fallbackPlainText(title);
        var nativeRuns = new java.util.ArrayList<String>(2);
        for (boolean accept : List.of(true, false)) {
            var action = actions.get(accept);
            if (action == null) continue;
            String label = action.label();
            if (label.startsWith("stellarrtp.tpa.button_")) label = accept ? "[Accept]" : "[Deny]";
            if (label.isEmpty()) label = accept ? "接受" : "拒绝";
            builder.addAction(accept ? "respond_tpa_accept" : "respond_tpa_deny", label, action.kind(), action.value(), accept);
            if (hasTeleportClicks && action.kind() == RUN_COMMAND) nativeRuns.add(action.value());
        }
        return builder.build().withNativeClickCommands(nativeRuns);
    }

    private static String stripClickSuffix(MessageSignals signals) {
        String text = signals.rawText().replaceAll("§.", "").strip();
        for (int i = signals.clicks().size() - 1; i >= 0; i--) {
            var click = signals.clicks().get(i);
            if (isTeleportClick(click) && !click.label().isBlank() && text.endsWith(click.label())) {
                text = text.substring(0, text.length() - click.label().length()).stripTrailing();
            }
        }
        return text;
    }

    static boolean isTargetedCommand(String command) {
        var match = ACTION.matcher(command);
        return match.matches() && match.group(3) != null
                && (match.group(1) == null || match.group(1).equalsIgnoreCase("essentials"));
    }

    static boolean isTeleportClick(ClickActionSpec click) {
        return click.isExecutableCommand() && ACTION.matcher(click.value()).matches();
    }
}
