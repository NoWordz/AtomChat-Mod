package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.BottomChromeTransition;
import com.atom.chat.ui.ShellHeader;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromeShadowTest {
    private static final int WIDTH = 440;
    private static final int HEIGHT = 180;
    private Field instance;
    private Object previous;
    private AtomChatConfig config;

    @BeforeEach void installConfig() throws Exception {
        instance = AtomChatConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previous = instance.get(null);
        config = new AtomChatConfig();
        config.cornerRadius = 24.874624F;
        config.cardColor = 0xFFAE8798;
        instance.set(null, config);
    }

    @AfterEach void restoreConfig() throws Exception {
        instance.set(null, previous);
    }

    @Test void cachedBarShadowMatchesTheDeployed093ProfileIncludingItsInterior() {
        try (var actual = Surface.makeRasterN32Premul(WIDTH, HEIGHT);
             var reference = Surface.makeRasterN32Premul(WIDTH, HEIGHT)) {
            for (float tint : new float[]{0F, .20241691F, 1F}) {
                config.cardTint = tint;
                for (float scale : new float[]{1F, 1.25F}) {
                    for (int pass = 0; pass < 2; pass++) {
                        actual.getCanvas().clear(0);
                        reference.getCanvas().clear(0);
                        actual.getCanvas().save();
                        reference.getCanvas().save();
                        actual.getCanvas().scale(scale, scale);
                        reference.getCanvas().scale(scale, scale);
                        var bar = new UiLayout.Rect(40.25F, 35.5F, 240F, UiTokens.HEADER_HEIGHT);
                        float radius = ChromeMaterial.cornerRadius(bar, UiTokens.headerRadius(AtomChatAppearance.theme()));
                        ChromeMaterial.drawShadow(actual.getCanvas(), bar.x(), bar.y(), bar.w(), bar.h(), radius);
                        draw093Shadow(reference.getCanvas(), bar, radius);
                        actual.getCanvas().restore();
                        reference.getCanvas().restore();
                        assertArrayEquals(pixels(reference), pixels(actual),
                                "bar shadow at tint " + tint + ", scale " + scale + ", cache pass " + pass);
                    }
                }
            }
        }
    }

    @Test void realHeaderAndNavigationComposeTheLegacyShadowUnderTranslucentFaces() {
        config.cardTint = .20241691F;
        try (var actual = Surface.makeRasterN32Premul(WIDTH, HEIGHT);
             var reference = Surface.makeRasterN32Premul(WIDTH, HEIGHT);
             var bottom = new BottomChromeTransition()) {
            for (boolean header : new boolean[]{true, false}) {
                actual.getCanvas().clear(0xFFB5A8B1);
                reference.getCanvas().clear(0xFFB5A8B1);
                var bar = new UiLayout.Rect(40.25F, 35.5F, 300F,
                        header ? UiTokens.HEADER_HEIGHT : UiTokens.TAB_BAR_H);
                var theme = AtomChatAppearance.theme();
                float radius = ChromeMaterial.cornerRadius(bar,
                        header ? UiTokens.headerRadius(theme) : UiTokens.shellChromeRadius(theme));
                if (header) {
                    ShellHeader.render(actual.getCanvas(), bar, "", false, null, 0F, 0);
                } else {
                    bottom.beginFrame(BottomChromeTransition.Mode.NAV, bar, bar, 1000L, false);
                    bottom.draw(actual.getCanvas(), ignored -> {});
                }
                draw093Shadow(reference.getCanvas(), bar, radius);
                CornerGeometry.fill(reference.getCanvas(),
                        CornerGeometry.circularRect(bar.x(), bar.y(), bar.w(), bar.h(), radius), UiTokens.cardFill(theme));
                ChromeMaterial.drawDetails(reference.getCanvas(), bar, radius);
                assertArrayEquals(pixels(reference), pixels(actual), header ? "header" : "navigation");
            }
        }
    }

    private static void draw093Shadow(Canvas canvas, UiLayout.Rect bar, float radius) {
        // The archived 0.9.3 ChromeMaterial scaled these two alphas before drawing.
        float opacity = (UiTokens.cardFill(AtomChatAppearance.theme()) >>> 24) / 255F;
        if (opacity <= 0F) return;
        SkiaDraw.drawChromeShadow(canvas, bar.x(), bar.y(), bar.w(), bar.h(), radius,
                Math.round(70F * opacity) << 24, Math.round(45F * opacity) << 24);
    }

    private static byte[] pixels(Surface surface) {
        try (var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(WIDTH, HEIGHT);
            assertTrue(surface.readPixels(bitmap, 0, 0));
            return bitmap.readPixels();
        }
    }
}
