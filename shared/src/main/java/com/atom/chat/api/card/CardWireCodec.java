package com.atom.chat.api.card;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact UTF-8 JSON wire codec for {@link ShareCard} and {@link CardState}.
 *
 * <p>Pure Java so server and client on all loaders share one exact format.
 */
public final class CardWireCodec {
    public static final int MAX_CARD_BYTES = 32_768;
    public static final int MAX_STATE_BYTES = 4_096;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private CardWireCodec() {
    }

    public static byte[] encodeCard(ShareCard card) {
        CardDto dto = CardDto.from(card.hidePrivateRedPacketPassword());
        return GSON.toJson(dto).getBytes(StandardCharsets.UTF_8);
    }

    public static ShareCard decodeCard(byte[] bytes) {
        return decodeCard(bytes, true);
    }

    /** Decodes a server payload, ignoring actions that name locally registered callbacks. */
    public static ShareCard decodeRemoteCard(byte[] bytes) {
        return decodeCard(bytes, false);
    }

    private static ShareCard decodeCard(byte[] bytes, boolean allowLocalCallback) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_CARD_BYTES) {
            return null;
        }
        try {
            CardDto dto = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), CardDto.class);
            return dto == null ? null : dto.toCard(allowLocalCallback).hidePrivateRedPacketPassword();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static byte[] encodeState(CardState state) {
        StateDto dto = StateDto.from(state);
        return GSON.toJson(dto).getBytes(StandardCharsets.UTF_8);
    }

    public static CardState decodeState(byte[] bytes) {
        return decodeState(bytes, true);
    }

    /** Decodes a server payload, ignoring actions that name locally registered callbacks. */
    public static CardState decodeRemoteState(byte[] bytes) {
        return decodeState(bytes, false);
    }

    private static CardState decodeState(byte[] bytes, boolean allowLocalCallback) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_STATE_BYTES) {
            return null;
        }
        try {
            StateDto dto = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), StateDto.class);
            return dto == null ? null : dto.toState(allowLocalCallback);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final class ActionDto {
        String id;
        String label;
        String kind;
        String value;
        boolean primary;

        static ActionDto from(ShareCard.CardAction a) {
            if (a == null) return null;
            ActionDto d = new ActionDto();
            d.id = a.id();
            d.label = a.label();
            d.kind = a.kind().name();
            d.value = a.value();
            d.primary = a.primary();
            return d;
        }

        ShareCard.CardAction toAction(boolean allowLocalCallback) {
            ShareCard.ActionKind k;
            try {
                k = kind == null ? ShareCard.ActionKind.COPY_TEXT : ShareCard.ActionKind.valueOf(kind);
            } catch (IllegalArgumentException e) {
                k = ShareCard.ActionKind.COPY_TEXT;
            }
            if (!allowLocalCallback && k == ShareCard.ActionKind.CLIENT_CALLBACK) return null;
            return new ShareCard.CardAction(id, label, k, value, primary);
        }
    }

    private static final class LocationDto {
        String name;
        double x, y, z;
        String dimension;
        int accentColor;
        String mapSource;
        String nativeAddCommand;
    }

    private static final class RedPacketDto {
        String packetId;
        String greeting;
        String statusText;
        boolean lucky;
        boolean hasPassword;
        String password;
        int claimedCount;
        int totalCount;
        String currency;
        boolean dimmed;
    }

    private static final class ItemDto {
        String itemId;
        String itemSnbt;
        String displayName;
        String summary;
        String rarityLabel;
        int accentColor;
        int count;
    }

    private static final class GeneralDto {
        String title;
        String description;
        String thumbnailUrl;
        String thumbnailText;
        String avatarPlayerName;
        int accentColor;
    }

    private static final class CardDto {
        String cardId;
        String template;
        String customTypeId;
        String title;
        String subtitle;
        String sourceLabel;
        String badgeText;
        int accentColor;
        boolean serverAuthoritative;
        LocationDto location;
        RedPacketDto redPacket;
        ItemDto item;
        GeneralDto general;
        Map<String, String> customData;
        ActionDto bodyAction;
        List<ActionDto> actions;
        String hudTag;
        String hudSummary;
        String fallbackPlainText;

        static CardDto from(ShareCard c) {
            CardDto d = new CardDto();
            d.cardId = c.cardId();
            d.template = c.template().name();
            d.customTypeId = c.customTypeId();
            d.title = c.title();
            d.subtitle = c.subtitle();
            d.sourceLabel = c.sourceLabel();
            d.badgeText = c.badgeText();
            d.accentColor = c.accentColor();
            d.serverAuthoritative = c.serverAuthoritative();
            if (c.location() != null) {
                d.location = new LocationDto();
                d.location.name = c.location().name();
                d.location.x = c.location().x();
                d.location.y = c.location().y();
                d.location.z = c.location().z();
                d.location.dimension = c.location().dimension();
                d.location.accentColor = c.location().accentColor();
                d.location.mapSource = c.location().mapSource();
                d.location.nativeAddCommand = c.location().nativeAddCommand();
            }
            if (c.redPacket() != null) {
                d.redPacket = new RedPacketDto();
                d.redPacket.packetId = c.redPacket().packetId();
                d.redPacket.greeting = c.redPacket().greeting();
                d.redPacket.statusText = c.redPacket().statusText();
                d.redPacket.lucky = c.redPacket().lucky();
                d.redPacket.hasPassword = c.redPacket().hasPassword();
                d.redPacket.password = c.redPacket().password();
                d.redPacket.claimedCount = c.redPacket().claimedCount();
                d.redPacket.totalCount = c.redPacket().totalCount();
                d.redPacket.currency = c.redPacket().currency();
                d.redPacket.dimmed = c.redPacket().dimmed();
            }
            if (c.item() != null) {
                d.item = new ItemDto();
                d.item.itemId = c.item().itemId();
                d.item.itemSnbt = c.item().itemSnbt();
                d.item.displayName = c.item().displayName();
                d.item.summary = c.item().summary();
                d.item.rarityLabel = c.item().rarityLabel();
                d.item.accentColor = c.item().accentColor();
                d.item.count = c.item().count();
            }
            if (c.general() != null) {
                d.general = new GeneralDto();
                d.general.title = c.general().title();
                d.general.description = c.general().description();
                d.general.thumbnailUrl = c.general().thumbnailUrl();
                d.general.thumbnailText = c.general().thumbnailText();
                d.general.avatarPlayerName = c.general().avatarPlayerName();
                d.general.accentColor = c.general().accentColor();
            }
            if (!c.customData().isEmpty()) {
                d.customData = new LinkedHashMap<>(c.customData());
            }
            d.bodyAction = ActionDto.from(c.bodyAction());
            if (!c.actions().isEmpty()) {
                d.actions = new ArrayList<>(c.actions().size());
                for (ShareCard.CardAction a : c.actions()) {
                    d.actions.add(ActionDto.from(a));
                }
            }
            d.hudTag = c.hudTag();
            d.hudSummary = c.hudSummary();
            d.fallbackPlainText = c.fallbackPlainText();
            return d;
        }

        ShareCard toCard(boolean allowLocalCallback) {
            ShareCard.Template tpl;
            try {
                tpl = template == null ? ShareCard.Template.GENERAL : ShareCard.Template.valueOf(template);
            } catch (IllegalArgumentException e) {
                tpl = ShareCard.Template.GENERAL;
            }
            ShareCard.Builder b = new ShareCard.Builder(tpl)
                    .cardId(cardId)
                    .customTypeId(customTypeId)
                    .title(title)
                    .subtitle(subtitle)
                    .source(sourceLabel)
                    .badge(badgeText)
                    .accentColor(accentColor)
                    .serverAuthoritative(serverAuthoritative)
                    .hudTag(hudTag)
                    .hudSummary(hudSummary)
                    .fallbackPlainText(fallbackPlainText);
            if (location != null) {
                b.locationPayload(new ShareCard.LocationPayload(
                        location.name, location.x, location.y, location.z,
                        location.dimension, location.accentColor, location.mapSource,
                        location.nativeAddCommand));
            }
            if (redPacket != null) {
                b.redPacketPayload(new ShareCard.RedPacketPayload(
                        redPacket.packetId, redPacket.greeting, redPacket.statusText,
                        redPacket.lucky, redPacket.hasPassword, redPacket.password,
                        redPacket.claimedCount, redPacket.totalCount, redPacket.currency,
                        redPacket.dimmed));
            }
            if (item != null) {
                b.itemPayload(new ShareCard.ItemPayload(
                        item.itemId, item.itemSnbt, item.displayName,
                        item.summary, item.rarityLabel, item.accentColor, item.count));
            }
            if (general != null) {
                b.generalPayload(new ShareCard.GeneralPayload(
                        general.title, general.description, general.thumbnailUrl,
                        general.thumbnailText, general.avatarPlayerName, general.accentColor));
            }
            if (customData != null) {
                customData.forEach(b::putData);
            }
            if (bodyAction != null) {
                b.bodyAction(bodyAction.toAction(allowLocalCallback));
            }
            if (actions != null) {
                for (ActionDto a : actions) {
                    if (a != null) b.addAction(a.toAction(allowLocalCallback));
                }
            }
            return b.build();
        }
    }

    private static final class StateDto {
        String cardId;
        boolean dimmed;
        String statusText;
        String badgeText;
        ActionDto overrideBodyAction;
        boolean disableActions;

        static StateDto from(CardState s) {
            StateDto d = new StateDto();
            d.cardId = s.cardId();
            d.dimmed = s.dimmed();
            d.statusText = s.statusText();
            d.badgeText = s.badgeText();
            d.overrideBodyAction = ActionDto.from(s.overrideBodyAction());
            d.disableActions = s.disableActions();
            return d;
        }

        CardState toState(boolean allowLocalCallback) {
            if (cardId == null || cardId.isBlank()) return null;
            CardState.Builder b = new CardState.Builder(cardId, dimmed)
                    .statusText(statusText)
                    .badgeText(badgeText)
                    .disableActions(disableActions);
            if (overrideBodyAction != null) {
                b.overrideBodyAction(overrideBodyAction.toAction(allowLocalCallback));
            }
            return b.build();
        }
    }
}
