package com.atom.chat.render;

import net.minecraft.text.Style;

/**
 * A clickable/hoverable text run rectangle in the coordinate space where the
 * text was drawn, together with the run's effective Minecraft style.
 *
 * @param x left edge of the run rectangle
 * @param y top edge of the line box containing the run
 * @param w width of the run
 * @param h height of the line box containing the run
 * @param style style carrying the {@code ClickEvent} / {@code HoverEvent}
 */
public record ClickableSpan(float x, float y, float w, float h, Style style, long messageId, int run) {
    public ClickableSpan(float x, float y, float w, float h, Style style) {
        this(x, y, w, h, style, 0, 0);
    }

    public boolean sameClick(ClickableSpan other) {
        if (other == null) return false;
        if (messageId == 0 || other.messageId == 0) return equals(other);
        return messageId == other.messageId && run == other.run
                && java.util.Objects.equals(style.getClickEvent(), other.style.getClickEvent())
                && java.util.Objects.equals(style.getInsertion(), other.style.getInsertion());
    }
}
