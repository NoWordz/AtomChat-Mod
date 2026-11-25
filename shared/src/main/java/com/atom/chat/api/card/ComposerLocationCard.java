package com.atom.chat.api.card;

import java.util.function.BiFunction;

/** Composer location data stays language-neutral until it reaches an AtomChat client. */
public final class ComposerLocationCard {
    public static final String PREFIX = "atomchat.card.location.";
    private static final String AUTHOR = "atomchat:location_author";
    private static final String WAYPOINT_SUFFIX = " (AtomChat)";

    private ComposerLocationCard() {}

    public static ShareCard create(String playerName, double x, double y, double z, String dimension) {
        String author = sanitizeAuthor(playerName);
        ShareCard card = BuiltInCardParsers.buildLocationCard(author + WAYPOINT_SUFFIX, x, y, z, dimension,
                ShareCard.dimensionAccent(dimension), "AtomChat", "");
        return card.toBuilder().putData(AUTHOR, author).build();
    }

    public static boolean isComposerLocation(ShareCard card) {
        return card != null && card.location() != null && card.customData().containsKey(AUTHOR);
    }

    public static boolean isLegacyComposerLocation(ShareCard card, String playerName) {
        return card.location() != null && "位置分享".equals(card.location().mapSource())
                && card.location().name().equals(playerName + " 的位置");
    }

    public static String nativeName(ShareCard card) {
        return isComposerLocation(card) ? card.customData().get(AUTHOR) + WAYPOINT_SUFFIX : card.location().name();
    }

    /** The waypoint's name preserves the source even when the server only relays original chat. */
    public static ShareCard fromWaypoint(ShareCard card, String playerName) {
        if (card == null || card.location() == null) return card;
        String name = card.location().name();
        if (!name.endsWith(WAYPOINT_SUFFIX)) return card;
        return card.toBuilder().putData(AUTHOR, sanitizeAuthor(playerName)).build();
    }

    public static ShareCard fromPairedWaypoint(ShareCard card, String playerName) {
        if (isComposerLocation(card) || isLegacyComposerLocation(card, playerName)) {
            return card.toBuilder().putData(AUTHOR, sanitizeAuthor(playerName)).build();
        }
        return card;
    }

    private static String sanitizeAuthor(String playerName) {
        return BuiltInCardParsers.sanitizeLabel(playerName, 32 - WAYPOINT_SUFFIX.length());
    }

    public static ShareCard localize(ShareCard card, BiFunction<String, Object[], String> translate) {
        if (!isComposerLocation(card)) return card;
        var loc = card.location();
        String source = translate.apply(PREFIX + "source", new Object[0]);
        String author = card.customData().get(AUTHOR);
        String title = author.isEmpty() ? source : translate.apply(PREFIX + "title", new Object[]{author});
        String tag = translate.apply(PREFIX + "tag", new Object[0]);
        String position = title + " (" + loc.blockX() + ", " + loc.blockY() + ", " + loc.blockZ();
        return card.toBuilder().title(title).source(source).hudTag(tag).hudSummary(position + ")")
                .fallbackPlainText(tag + " " + position + ", " + dimensionName(loc.dimension(), translate) + ")")
                .locationPayload(new ShareCard.LocationPayload(title, loc.x(), loc.y(), loc.z(), loc.dimension(),
                        loc.accentColor(), source, loc.nativeAddCommand()))
                .mapActions(action -> localizeAction(action, translate))
                .build();
    }

    public static String dimensionName(String dimension, BiFunction<String, Object[], String> translate) {
        String suffix = switch (dimension) {
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end" -> "end";
            default -> null;
        };
        if (suffix != null) return translate.apply(PREFIX + suffix, new Object[0]);
        return dimension.substring(dimension.indexOf(':') + 1);
    }

    private static ShareCard.CardAction localizeAction(ShareCard.CardAction action,
                                                        BiFunction<String, Object[], String> translate) {
        String suffix = switch (action.id()) {
            case "save_wp" -> "waypoint";
            case "tp_loc" -> "teleport";
            case "copy_loc" -> "copy";
            default -> null;
        };
        if (suffix == null) return action;
        return new ShareCard.CardAction(action.id(), translate.apply(PREFIX + suffix, new Object[0]),
                action.kind(), action.value(), action.primary());
    }
}
