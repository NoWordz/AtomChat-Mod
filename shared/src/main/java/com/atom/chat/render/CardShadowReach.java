package com.atom.chat.render;

import io.github.humbleui.types.Rect;

/**
 * How far a card's soft shadow reaches past the card's own rectangle — the pad
 * every entrance {@code saveLayer} must add on all four sides.
 *
 * <p><b>Why this exists.</b> An entrance {@code saveLayer} is a hard clip: the
 * layer's alpha is composited, but nothing outside its rectangle is ever
 * drawn. The list entrances padded the layer by the item's rise travel (and
 * the SLIDE drift) but not by the shadow's own spread, so during the entrance
 * the shadow's leading edge was cut into a dead-straight line pinned at the
 * slot's top edge — measured at t=80..280ms the shadow did not move at all,
 * and the frame the layer was skipped it sprang 11px further out. The card
 * and its shadow must travel together, so the layer has to contain both.</p>
 *
 * The layer uses the same reach as the cached card shadow.
 */
public final class CardShadowReach {
    /**
     * The four-side pad, in logical px, that contains the whole
     * contact and ambient shadow tails.
     */
    public static final float PAD = ShadowTokens.CARD.pad();

    private CardShadowReach() {
    }

    /**
     * The saveLayer rectangle for a card at {@code (x, y, w, h)} that is drawn
     * shifted by {@code (drift, lift)} this frame: the union of the card's
     * resting and shifted rectangles, plus {@link #PAD} on all four sides.
     *
     * <p>Both offsets are travel still owed, so they are non-negative — the
     * content is drawn further right and further down than its slot, never
     * left or up. Zero offsets give the plain card plus pad, which is the
     * settled-but-still-fading frame.</p>
     */
    public static Rect layerRect(float x, float y, float w, float h, float lift, float drift) {
        return Rect.makeXYWH(x - PAD, y - PAD,
                w + drift + PAD * 2.0F, h + lift + PAD * 2.0F);
    }
}
