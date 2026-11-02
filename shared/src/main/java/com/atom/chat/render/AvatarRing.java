package com.atom.chat.render;

import com.atom.lib.design.atomchat.UiTokens;

/**
 * The circular avatar's two radii, in one place.
 *
 * <p><b>Why this exists.</b> Five avatar sites (profile hero, message rows,
 * conversation rows, the notification banner, the settings blocked-player row)
 * each derived their separator pad and their rim radius by hand. The 0.3.0
 * language painted the rim <em>outside</em> the panel-coloured separator, which
 * left ~{@code s(2)} of panel colour plus a visible gap between the bitmap and
 * its outline; once the rims were snapped to the device grid the gap became
 * crisp and obvious ("avatars and their rim must touch"). One helper keeps the
 * five sites from drifting apart again: the rim stroke straddles the avatar's
 * own edge (half over the bitmap, half past it) and the separator's outer edge
 * lands exactly on the stroke's outer edge, so nothing of the panel colour can
 * ever show through between bitmap and rim.</p>
 *
 * <p><b>The geometric invariant.</b> {@link #separatorRadius} must equal the
 * rim's <em>outer</em> radius, {@code rimRadius(R) + 0.5 * RIM_STROKE_PX}.
 * Both radii are drawn about the same centre, so if the separator reaches
 * further than the stroke, the excess is a ring of panel colour; if the stroke
 * reaches further, it floats on the card fill. That is exactly what a
 * half-device-pixel centre offset produced: the separator stayed concentric
 * with the bitmap while the stroke's centre was nudged, so the gap opened on
 * one side only ("the rim has a seam"). {@link #SEPARATOR_PX} is therefore
 * derived from the stroke width, not written down a second time.</p>
 *
 * <p><b>Why the stroke is not pixel-snapped.</b> Nudging the stroke's centre by
 * half a device pixel sharpens a 1px horizontal or vertical edge, but a circle
 * has no single edge direction: the nudge just moves the whole ring off the
 * bitmap's centre, and the resulting one-side seam is far more visible than the
 * extra AA softness it buys. The rim is deliberately concentric with the
 * bitmap; a 1.25-logical-px stroke already carries enough AA that the
 * half-pixel phase changes nothing the eye can resolve.</p>
 *
 * <p><b>Bucket.</b> Shared single source, no Minecraft names — only
 * {@link UiTokens} and pure arithmetic.</p>
 */
public final class AvatarRing {

    /** The rim stroke width; the stroke centre sits half of this outside R. */
    public static final float RIM_STROKE_PX = UiTokens.s(1.0F); // bare-value: hairline ring, owns its own scale

    /**
     * The panel-coloured separator's pad outside the bitmap. Derived from the
     * stroke so the separator's outer edge IS the stroke's outer edge: a
     * standalone literal here is what let the two drift apart before. A
     * hairline ring is the avatar's own edge weight, not a scaled family token.
     */
    public static final float SEPARATOR_PX = RIM_STROKE_PX; // bare-value: derived, not an independent literal

    private AvatarRing() {
    }

    /**
     * Radius of the panel-coloured separator circle drawn under the avatar:
     * {@code R + SEPARATOR_PX}, which is the rim stroke's outer radius. Only the
     * profile hero draws one — the other sites sit on a card whose fill already
     * separates the bitmap.
     */
    public static float separatorRadius(float avatarRadius) {
        return avatarRadius + SEPARATOR_PX;
    }

    /**
     * Radius of the rim stroke's centre line: {@code R + 0.5 * RIM_STROKE_PX}.
     * The stroke therefore straddles the bitmap's edge — half over the avatar,
     * half over whatever is behind it — and its outer edge lands exactly on
     * {@link #separatorRadius} of the same radius.
     */
    public static float rimRadius(float avatarRadius) {
        return avatarRadius + 0.5F * RIM_STROKE_PX;
    }
}
