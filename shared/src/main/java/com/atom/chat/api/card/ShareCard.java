package com.atom.chat.api.card;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable data model for rich chat share cards rendered inside AtomChat
 * (WeChat/QQ-style location cards, red-packet cards, equipment/item cards,
 * general mini-program cards and custom Skija-rendered cards).
 *
 * <p>This class lives in {@code shared/} with zero Minecraft mapping
 * dependencies so Fabric, NeoForge and Forge share one identical API contract.
 */
public final class ShareCard {

    public enum Template {
        LOCATION,
        RED_PACKET,
        ITEM,
        GENERAL,
        CUSTOM
    }

    public enum ActionKind {
        RUN_COMMAND,
        SUGGEST_COMMAND,
        OPEN_URL,
        COPY_TEXT,
        ADD_WAYPOINT,
        TELEPORT_LOCATION,
        CLIENT_CALLBACK
    }

    public record CardAction(String id, String label, ActionKind kind, String value, boolean primary) {
        public CardAction {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            kind = kind == null ? ActionKind.COPY_TEXT : kind;
            value = value == null ? "" : value;
        }
    }

    public record LocationPayload(String name, double x, double y, double z,
                                  String dimension, int accentColor, String mapSource,
                                  String nativeAddCommand) {
        public LocationPayload {
            name = name == null || name.isBlank() ? "坐标位置" : name;
            dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension;
            mapSource = mapSource == null || mapSource.isBlank() ? "位置分享" : mapSource;
            nativeAddCommand = nativeAddCommand == null ? "" : nativeAddCommand;
        }

        public int blockX() {
            return (int) Math.floor(x);
        }

        public int blockY() {
            return (int) Math.floor(y);
        }

        public int blockZ() {
            return (int) Math.floor(z);
        }
    }

    public record RedPacketPayload(String packetId, String greeting, String statusText,
                                   boolean lucky, boolean hasPassword, String password,
                                   int claimedCount, int totalCount, String currency, boolean dimmed) {
        public RedPacketPayload {
            packetId = packetId == null ? "" : packetId;
            greeting = greeting == null || greeting.isBlank() ? "恭喜发财，大吉大利" : greeting;
            statusText = statusText == null ? "" : statusText;
            password = password == null ? "" : password;
            currency = currency == null ? "" : currency;
            totalCount = Math.max(0, totalCount);
            claimedCount = Math.max(0, claimedCount);
        }
    }

    public record ItemPayload(String itemId, String itemSnbt, String displayName,
                              String summary, String rarityLabel, int accentColor, int count) {
        public ItemPayload {
            itemId = itemId == null ? "" : itemId;
            itemSnbt = itemSnbt == null ? "" : itemSnbt;
            displayName = displayName == null || displayName.isBlank() ? "物品" : displayName;
            summary = summary == null ? "" : summary;
            rarityLabel = rarityLabel == null ? "" : rarityLabel;
            count = Math.max(1, count);
        }
    }

    public record GeneralPayload(String title, String description, String thumbnailUrl,
                                 String thumbnailText, String avatarPlayerName, int accentColor) {
        public GeneralPayload {
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
            thumbnailText = thumbnailText == null ? "" : thumbnailText;
            avatarPlayerName = avatarPlayerName == null ? "" : avatarPlayerName;
        }
    }

    private final String cardId;
    private final Template template;
    private final String customTypeId;
    private final String title;
    private final String subtitle;
    private final String sourceLabel;
    private final String badgeText;
    private final int accentColor;
    private final boolean serverAuthoritative;
    private final LocationPayload location;
    private final RedPacketPayload redPacket;
    private final ItemPayload item;
    private final GeneralPayload general;
    private final Map<String, String> customData;
    private final CardAction bodyAction;
    private final List<CardAction> actions;
    private final String hudTag;
    private final String hudSummary;
    private final String fallbackPlainText;
    /** Optional transient platform-native hover object (e.g. MC HoverEvent for full item tooltips). */
    private final transient Object nativeHoverObject;
    /**
     * Non-serializable set of slash commands verified to have originated verbatim from a
     * server-delivered Minecraft {@code ClickEvent}. Never populated by {@link CardWireCodec}.
     */
    private final transient Set<String> nativeClickCommands;

    private ShareCard(Builder b) {
        this.cardId = b.cardId;
        this.template = Objects.requireNonNull(b.template, "template");
        this.customTypeId = b.customTypeId == null ? "" : b.customTypeId;
        this.title = b.title == null ? "" : b.title;
        this.subtitle = b.subtitle == null ? "" : b.subtitle;
        this.sourceLabel = b.sourceLabel == null ? "" : b.sourceLabel;
        this.badgeText = b.badgeText == null ? "" : b.badgeText;
        this.accentColor = b.accentColor;
        this.serverAuthoritative = b.serverAuthoritative;
        this.location = b.location;
        this.redPacket = b.redPacket;
        this.item = b.item;
        this.general = b.general;
        this.customData = Collections.unmodifiableMap(new LinkedHashMap<>(b.customData));
        this.bodyAction = b.bodyAction;
        this.actions = List.copyOf(b.actions);
        this.hudTag = b.hudTag == null || b.hudTag.isBlank() ? defaultHudTag(this.template) : b.hudTag;
        this.hudSummary = b.hudSummary == null || b.hudSummary.isBlank() ? defaultHudSummary(this) : b.hudSummary;
        this.fallbackPlainText = b.fallbackPlainText == null || b.fallbackPlainText.isBlank()
                ? this.hudTag + " " + this.hudSummary : b.fallbackPlainText;
        this.nativeHoverObject = b.nativeHoverObject;
        this.nativeClickCommands = b.nativeClickCommands.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(b.nativeClickCommands));
    }

    private static String defaultHudTag(Template template) {
        return switch (template) {
            case LOCATION -> "[坐标]";
            case RED_PACKET -> "[红包]";
            case ITEM -> "[装备]";
            case GENERAL, CUSTOM -> "[分享]";
        };
    }

    private static String defaultHudSummary(ShareCard c) {
        if (c.location != null) {
            return c.location.name() + " (" + c.location.blockX() + ", " + c.location.blockY() + ", " + c.location.blockZ() + ")";
        }
        if (c.redPacket != null) {
            return c.redPacket.greeting();
        }
        if (c.item != null) {
            return c.item.displayName() + (c.item.count() > 1 ? " ×" + c.item.count() : "");
        }
        return !c.title.isBlank() ? c.title : c.subtitle;
    }

    public String cardId() { return cardId; }
    public Template template() { return template; }
    public String customTypeId() { return customTypeId; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public String sourceLabel() { return sourceLabel; }
    public String badgeText() { return badgeText; }
    public int accentColor() { return accentColor; }
    public boolean serverAuthoritative() { return serverAuthoritative; }
    public LocationPayload location() { return location; }
    public RedPacketPayload redPacket() { return redPacket; }
    public ItemPayload item() { return item; }
    public GeneralPayload general() { return general; }
    public Map<String, String> customData() { return customData; }
    public CardAction bodyAction() { return bodyAction; }
    public List<CardAction> actions() { return actions; }
    public String hudTag() { return hudTag; }
    public String hudSummary() { return hudSummary; }
    public String fallbackPlainText() { return fallbackPlainText; }
    public Object nativeHoverObject() { return nativeHoverObject; }

    /** Returns true iff {@code rawCommand} came verbatim from an incoming native MC ClickEvent. */
    public boolean isNativeClickCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank() || nativeClickCommands.isEmpty()) return false;
        String trimmed = rawCommand.trim();
        if (nativeClickCommands.contains(trimmed)) return true;
        String alt = trimmed.startsWith("/") ? trimmed.substring(1) : "/" + trimmed;
        return nativeClickCommands.contains(alt);
    }

    public ShareCard withServerAuthoritative(boolean authoritative) {
        if (this.serverAuthoritative == authoritative) return this;
        return toBuilder().serverAuthoritative(authoritative).build();
    }

    public ShareCard withNativeHoverObject(Object hoverObj) {
        return toBuilder().nativeHoverObject(hoverObj).build();
    }

    public ShareCard withNativeClickCommands(Collection<String> commands) {
        if (commands == null || commands.isEmpty()) return this;
        Builder b = toBuilder();
        for (String c : commands) {
            if (c != null && !c.isBlank()) b.nativeClickCommands.add(c.trim());
        }
        return b.build();
    }

    public Builder toBuilder() {
        Builder b = new Builder(template);
        b.cardId = cardId;
        b.customTypeId = customTypeId;
        b.title = title;
        b.subtitle = subtitle;
        b.sourceLabel = sourceLabel;
        b.badgeText = badgeText;
        b.accentColor = accentColor;
        b.serverAuthoritative = serverAuthoritative;
        b.location = location;
        b.redPacket = redPacket;
        b.item = item;
        b.general = general;
        b.customData.putAll(customData);
        b.bodyAction = bodyAction;
        b.actions.addAll(actions);
        b.hudTag = hudTag;
        b.hudSummary = hudSummary;
        b.fallbackPlainText = fallbackPlainText;
        b.nativeHoverObject = nativeHoverObject;
        b.nativeClickCommands.addAll(nativeClickCommands);
        return b;
    }

    public static Builder location(String name, double x, double y, double z, String dimension) {
        Builder b = new Builder(Template.LOCATION);
        b.title = name;
        b.accentColor = dimensionAccent(dimension);
        b.location = new LocationPayload(name, x, y, z, dimension, b.accentColor, "位置分享", "");
        b.sourceLabel = "位置分享";
        b.hudTag = "[坐标]";
        return b;
    }

    public static Builder redPacket(String cardId) {
        Builder b = new Builder(Template.RED_PACKET);
        b.cardId = cardId;
        b.accentColor = 0xFFFA9D3B;
        b.sourceLabel = "模块商店 · 红包";
        b.hudTag = "[红包]";
        return b;
    }

    public static Builder item(String displayName) {
        Builder b = new Builder(Template.ITEM);
        b.title = displayName;
        b.accentColor = 0xFFA855F7;
        b.sourceLabel = "装备分享";
        b.hudTag = "[装备]";
        return b;
    }

    public static Builder general(String title) {
        Builder b = new Builder(Template.GENERAL);
        b.title = title;
        b.accentColor = 0xFF3B82F6;
        b.sourceLabel = "分享卡片";
        b.hudTag = "[分享]";
        return b;
    }

    public static Builder custom(String customTypeId, String title) {
        Builder b = new Builder(Template.CUSTOM);
        b.customTypeId = customTypeId;
        b.title = title;
        b.accentColor = 0xFF3B82F6;
        return b;
    }

    public static int dimensionAccent(String dim) {
        if (dim == null) return 0xFF10B981;
        String lower = dim.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("nether")) return 0xFFEF4444;
        if (lower.contains("the_end") || lower.contains("end")) return 0xFFA855F7;
        return 0xFF10B981;
    }

    public static final class Builder {
        private String cardId;
        private final Template template;
        private String customTypeId = "";
        private String title = "";
        private String subtitle = "";
        private String sourceLabel = "";
        private String badgeText = "";
        private int accentColor = 0xFF3B82F6;
        private boolean serverAuthoritative;
        private LocationPayload location;
        private RedPacketPayload redPacket;
        private ItemPayload item;
        private GeneralPayload general;
        private final Map<String, String> customData = new LinkedHashMap<>();
        private CardAction bodyAction;
        private final List<CardAction> actions = new ArrayList<>();
        private String hudTag = "";
        private String hudSummary = "";
        private String fallbackPlainText = "";
        private Object nativeHoverObject;
        private final Set<String> nativeClickCommands = new LinkedHashSet<>();

        public Builder(Template template) {
            this.template = template;
        }

        public Builder cardId(String cardId) { this.cardId = cardId; return this; }
        public Builder customTypeId(String customTypeId) { this.customTypeId = customTypeId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder subtitle(String subtitle) { this.subtitle = subtitle; return this; }
        public Builder source(String sourceLabel) { this.sourceLabel = sourceLabel; return this; }
        public Builder badge(String badgeText) { this.badgeText = badgeText; return this; }
        public Builder accentColor(int accentColor) { this.accentColor = accentColor; return this; }
        public Builder serverAuthoritative(boolean serverAuthoritative) { this.serverAuthoritative = serverAuthoritative; return this; }
        public Builder locationPayload(LocationPayload location) { this.location = location; return this; }
        public Builder redPacketPayload(RedPacketPayload redPacket) { this.redPacket = redPacket; return this; }
        public Builder itemPayload(ItemPayload item) { this.item = item; return this; }
        public Builder generalPayload(GeneralPayload general) { this.general = general; return this; }
        public Builder putData(String key, String value) {
            if (key != null && value != null) this.customData.put(key, value);
            return this;
        }
        public Builder bodyAction(CardAction action) { this.bodyAction = action; return this; }
        public Builder onClickCommand(String command) {
            this.bodyAction = new CardAction("body", "", ActionKind.RUN_COMMAND, command, true);
            return this;
        }
        public Builder onClickSuggest(String text) {
            this.bodyAction = new CardAction("body", "", ActionKind.SUGGEST_COMMAND, text, true);
            return this;
        }
        public Builder onClickCopy(String text) {
            this.bodyAction = new CardAction("body", "", ActionKind.COPY_TEXT, text, false);
            return this;
        }
        public Builder onClickUrl(String url) {
            this.bodyAction = new CardAction("body", "", ActionKind.OPEN_URL, url, false);
            return this;
        }
        public Builder addAction(CardAction action) {
            if (action != null && this.actions.size() < 3) this.actions.add(action);
            return this;
        }
        public Builder addAction(String id, String label, ActionKind kind, String value, boolean primary) {
            return addAction(new CardAction(id, label, kind, value, primary));
        }
        public Builder hudTag(String hudTag) { this.hudTag = hudTag; return this; }
        public Builder hudSummary(String hudSummary) { this.hudSummary = hudSummary; return this; }
        public Builder fallbackPlainText(String fallbackPlainText) { this.fallbackPlainText = fallbackPlainText; return this; }
        public Builder nativeHoverObject(Object nativeHoverObject) { this.nativeHoverObject = nativeHoverObject; return this; }

        public ShareCard build() {
            if (template == Template.RED_PACKET && redPacket == null) {
                redPacket = new RedPacketPayload(cardId == null ? "" : cardId, title, subtitle,
                        true, false, "", 0, 0, "", false);
            } else if (template == Template.ITEM && item == null) {
                item = new ItemPayload("", "", title, subtitle, "", accentColor, 1);
            } else if (template == Template.GENERAL && general == null) {
                general = new GeneralPayload(title, subtitle, "", "", "", accentColor);
            }
            return new ShareCard(this);
        }
    }
}
