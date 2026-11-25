package com.atom.chat.card;

import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.ShareCard;
import net.minecraft.network.chat.Style;

/**
 * Interactive hit region registered while painting a {@link ShareCard}.
 *
 * <p>{@code action} may be {@code null} for hover-only regions (e.g. an item card
 * body carrying a native {@code HoverEvent.SHOW_ITEM} tooltip).
 */
public record CardHitRegion(float x, float y, float w, float h,
                            ShareCard card, CardState state,
                            ShareCard.CardAction action, Style hoverStyle, long messageId) {
    public CardHitRegion(float x, float y, float w, float h, ShareCard card, CardState state,
                         ShareCard.CardAction action, Style hoverStyle) {
        this(x, y, w, h, card, state, action, hoverStyle, 0L);
    }

    public boolean contains(float mx, float my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
