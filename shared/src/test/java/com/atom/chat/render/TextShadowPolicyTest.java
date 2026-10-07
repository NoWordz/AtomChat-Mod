package com.atom.chat.render;

import io.github.humbleui.skija.ImageFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the soft-shadow policy the call sites consult: capsule-family text
 * takes a backing shadow, bubble body and panel chrome do not. It pins the
 * policy values, not the call sites — a new capsule method can still pass the
 * wrong surface, but it must at least go through {@link TextSurface} to pass,
 * so the decision stays in one place.
 */
class TextShadowPolicyTest {

    @Test
    void capsuleFamilyAndNameBandAreShadowed() {
        assertTrue(TextSurface.CAPSULE.shadowed(), "system/quote/time/image capsules");
        assertTrue(TextSurface.NAME_BAND.shadowed(), "sender name band on the panel");
    }

    @Test
    void bubbleAndPanelChromeAreNotShadowed() {
        assertFalse(TextSurface.BUBBLE.shadowed(), "bubble body has its own fill contrast");
        assertFalse(TextSurface.PANEL.shadowed(), "panel chrome already reads");
    }

    /**
     * The shadow's polarity follows the surface passed in, not the panel: a
     * light capsule picks the light-surface filter and a dark one the dark
     * filter, and each is the cached instance.
     */
    @Test
    void shadowPolarityFollowsTheSurfaceArgument() {
        ImageFilter onLight = SkiaFontRenderer.backingShadowFor(0xFFF5F0E8); // elegant capsule
        ImageFilter onDark = SkiaFontRenderer.backingShadowFor(0xFF2A313C);  // raven capsule
        assertNotSame(onLight, onDark, "light and dark capsules must not share a filter");
        assertSame(onLight, SkiaFontRenderer.backingShadowFor(0xFFFFFFFF),
                "two light surfaces resolve to the same cached filter");
        assertSame(onDark, SkiaFontRenderer.backingShadowFor(0xFF000000),
                "two dark surfaces resolve to the same cached filter");
    }
}
