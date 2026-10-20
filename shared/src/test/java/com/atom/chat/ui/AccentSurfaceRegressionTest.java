package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.theme.AppearancePresetStore;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccentSurfaceRegressionTest {
    @Test void hoverRetainsTheConfiguredRgbOnBothSurfacePolarities() {
        for (int base : new int[]{0xFFF8F8F8, 0xFF181818}) {
            assertEquals(0x84CC16, UiTokens.cardHover(0xFF84CC16, base, 1F) & 0xFFFFFF);
            assertEquals(0x4A90E2, UiTokens.cardHover(0xFF4A90E2, base, .5F) & 0xFFFFFF);
        }
    }

    @Test void selectedNavigationUsesExactAccentAndConcentricCorners() {
        var cfg = AtomChatConfig.get();
        var saved = AppearancePresetStore.Snapshot.capture(cfg);
        try (var surface = Surface.makeRasterN32Premul(420,100); var pixels = new Bitmap()) {
            cfg.accentColor = 0xFF3388EE;
            cfg.cardColor = 0xFF181818;
            cfg.cardTint = 1F;
            var bar = new UiLayout.Rect(20,20,360,60);
            var tabs = new BottomTabBar();
            tabs.setSelectedImmediate(0);
            pixels.allocN32Pixels(420,100);
            cfg.cornerRadius = 25;
            tabs.render(surface.getCanvas(),bar,0,0xFFFFFFFF,cfg.accentColor);
            assertTrue(surface.readPixels(pixels,0,0));
            assertEquals(cfg.accentColor,pixels.getColor(110,50),"selected fill must match the swatch exactly");
            assertNotEquals(cfg.accentColor,pixels.getColor(31,31),"inner corner follows the bar rather than a small independent radius");
            surface.getCanvas().clear(0);
            cfg.cornerRadius = 0;
            tabs.render(surface.getCanvas(),bar,0,0xFFFFFFFF,cfg.accentColor);
            assertTrue(surface.readPixels(pixels,0,0));
            assertEquals(cfg.accentColor,pixels.getColor(31,31),"zero radius reaches the selection too");
        } finally { saved.applyTo(cfg); }
    }
}
