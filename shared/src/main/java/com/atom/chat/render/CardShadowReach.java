package com.atom.chat.render;

import com.atom.lib.design.atomchat.UiTokens;
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
 * <p><b>The number.</b> {@code UiCards.drawCard} draws through
 * {@code SkiaDraw.drawRoundedShadow} with blur
 * {@code UiTokens.CONTENT_SHADOW_BLUR} = {@code s(6)} and offsets the shadow
 * rect by {@code blur * 0.5}. The rendered tail on the settings tile measured
 * 13 logical px above the card fill; {@code s(12)} = 15 covers it with margin
 * for the blur's AA fringe. One shared constant, so no site can re-derive a
 * pad of its own and quietly under-size it again.</p>
 *
 * <p><b>Bucket.</b> Shared single source, no Minecraft names — only
 * {@link UiTokens}.</p>
 */
public final class CardShadowReach {
    /**
     * The four-side pad, in logical px, that contains the whole
     * {@code CARD_SHADOW} tail (blur plus the blur/2 offset).
     */
    public static final float PAD = UiTokens.s(12.0F); // bare-value: sized to the measured shadow tail, not a scaled family token

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
