package com.atom.chat.api.card;

/** Display semantics derived from the existing card payload and its live state. */
public final class SpecialCardPresentation {
    public enum Kind { LOCATION, ITEM, TELEPORT_REQUEST, RED_PACKET, GENERAL, CUSTOM }

    private SpecialCardPresentation() {}

    public static Kind kind(ShareCard card) {
        return switch (card.template()) {
            case LOCATION -> Kind.LOCATION;
            case ITEM -> Kind.ITEM;
            case RED_PACKET -> Kind.RED_PACKET;
            case CUSTOM -> Kind.CUSTOM;
            case GENERAL -> card.cardId() != null
                    && (card.cardId().startsWith("tpa:") || card.cardId().startsWith("req:tpa:"))
                    ? Kind.TELEPORT_REQUEST : Kind.GENERAL;
        };
    }

    public static CardState state(ShareCard card) {
        return CardStateStore.get(card.cardId());
    }
}
