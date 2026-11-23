package com.atom.chat.api.card;

/**
 * Dynamic live-state overlay for a {@link ShareCard} identified by {@code cardId}.
 *
 * <p>Allows server-side mods (e.g. Red Packet claim/finish/expire) or client-side
 * adapters (e.g. StellarRTP TPA accepted/denied/expired) to update a card's visual
 * state in-place without sending another chat message.
 */
public final class CardState {
    private final String cardId;
    private final boolean dimmed;
    private final String statusText;
    private final String badgeText;
    private final ShareCard.CardAction overrideBodyAction;
    private final boolean disableActions;

    private CardState(Builder b) {
        this.cardId = b.cardId == null ? "" : b.cardId;
        this.dimmed = b.dimmed;
        this.statusText = b.statusText;
        this.badgeText = b.badgeText;
        this.overrideBodyAction = b.overrideBodyAction;
        this.disableActions = b.disableActions;
    }

    public String cardId() { return cardId; }
    public boolean dimmed() { return dimmed; }
    public String statusText() { return statusText; }
    public String badgeText() { return badgeText; }
    public ShareCard.CardAction overrideBodyAction() { return overrideBodyAction; }
    public boolean disableActions() { return disableActions; }

    public static Builder active(String cardId) {
        return new Builder(cardId, false);
    }

    public static Builder dimmed(String cardId, String statusText) {
        return new Builder(cardId, true).statusText(statusText);
    }

    public static final class Builder {
        private final String cardId;
        private boolean dimmed;
        private String statusText;
        private String badgeText;
        private ShareCard.CardAction overrideBodyAction;
        private boolean disableActions;

        public Builder(String cardId, boolean dimmed) {
            this.cardId = cardId;
            this.dimmed = dimmed;
        }

        public Builder dimmed(boolean dimmed) { this.dimmed = dimmed; return this; }
        public Builder statusText(String statusText) { this.statusText = statusText; return this; }
        public Builder badgeText(String badgeText) { this.badgeText = badgeText; return this; }
        public Builder overrideBodyAction(ShareCard.CardAction action) { this.overrideBodyAction = action; return this; }
        public Builder onClickCommand(String command) {
            this.overrideBodyAction = new ShareCard.CardAction("body", "", ShareCard.ActionKind.RUN_COMMAND, command, false);
            return this;
        }
        public Builder disableActions(boolean disableActions) { this.disableActions = disableActions; return this; }

        public CardState build() {
            return new CardState(this);
        }
    }
}
