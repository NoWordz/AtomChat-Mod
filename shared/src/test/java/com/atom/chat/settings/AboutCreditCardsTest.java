package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AboutCreditCardsTest {
    @Test
    void qualityArtworkUsesTheSettingsTileContourAtBothEndsOfTheCornerSlider(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        var config = AtomChatConfig.get();
        float previous = config.cornerRadius;
        float tileX = 10F;
        float rowY = 10F;
        var box = AboutCreditCards.logoRect(tileX, rowY);
        int size = (int) Math.ceil(box.bottom() + AboutCreditCards.ROW_HEIGHT);
        int selection = 0xFF00CFFF;
        try (var reference = Surface.makeRasterN32Premul(size, size);
             var result = Surface.makeRasterN32Premul(size, size);
             var expected = new Bitmap(); var actual = new Bitmap()) {
            expected.allocN32Pixels(size, size);
            actual.allocN32Pixels(size, size);
            for (float radius : new float[]{0F, AtomChatConfig.MAX_CORNER_RADIUS}) {
                config.cornerRadius = radius;
                float contour = UiTokens.settingsTileRadius(AtomChatAppearance.theme());
                for (int edge : new int[]{0, selection}) {
                    reference.getCanvas().clear(0);
                    CornerGeometry.drawRoundedRect(reference.getCanvas(), box.x(), box.y(), box.w(), box.h(),
                            contour, 0xFFFFFFFF);
                    if (edge != 0) {
                        CornerGeometry.drawEdgeHighlight(reference.getCanvas(), box.x(), box.y(), box.w(), box.h(),
                                contour, UiTokens.s(2F), edge);
                    }
                    assertTrue(reference.readPixels(expected, 0, 0));
                    for (GraphicsQuality quality : GraphicsQuality.values()) {
                        result.getCanvas().clear(0);
                        AboutCreditCards.drawQuality(result.getCanvas(), quality, quality.name(), tileX, rowY,
                                0xFFFFFFFF, null, edge);
                        assertTrue(result.readPixels(actual, 0, 0));
                        for (int y = (int) Math.ceil(box.y()); y < box.bottom() - 1F; y++) {
                            for (int x = (int) Math.ceil(box.x()); x < box.right() - 1F; x++) {
                                int alpha = expected.getColor(x, y) >>> 24;
                                if (alpha == 0 || alpha == 255) {
                                    assertEquals(alpha, actual.getColor(x, y) >>> 24,
                                            quality + ", slider " + radius + ", selected " + (edge != 0)
                                                    + ", pixel (" + x + ", " + y + ")");
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            config.cornerRadius = previous;
        }
    }

    @Test
    void ordinaryWheelScrollsLinksAndOnlyLogoBoundsAreClickable(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        var config = AtomChatConfig.get();
        boolean previous = config.performanceModeEnabled();
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
        try {
            var page = new SettingsSectionPage();
            var layout = UiLayout.ofDetail(0, 0, 240, 1600);
            var about = SettingsSection.ABOUT;
            var rows = page.rows(about);
            int index = 0;
            while (rows.get(index).kind() != SettingsSectionPage.RowKind.CREDITS) index++;
            assertEquals("atomchat.settings.group.about.maintenance", rows.get(index + 1).labelKey());
            assertEquals(AboutCreditCards.ROW_HEIGHT, page.rowHeight(rows.get(index), layout));
            var row = page.rowRect(about, rows, index, 0F, layout);
            float firstX = row.x() + UiTokens.SETTINGS_ROW_PAD + AboutCreditCards.WIDTH / 2;
            float y = row.y() + AboutCreditCards.TOP + AboutCreditCards.LOGO / 2;
            assertEquals("https://github.com/HumbleUI/Skija", page.hit(firstX, y, layout, about, 0F).row().info().uri());
            assertNull(page.hit(firstX, y + AboutCreditCards.LOGO, layout, about, 0F));
            assertNull(page.hit(row.x() + UiTokens.SETTINGS_ROW_PAD, y, layout, about, 0F));
            assertTrue(page.scrollCardStrip(firstX, y, layout, about, 0F, 0F, -20F));
            float lastX = row.right() - UiTokens.SETTINGS_ROW_PAD - AboutCreditCards.WIDTH / 2;
            assertEquals("https://github.com/btzy/nativefiledialog-extended",
                    page.hit(lastX, y, layout, about, 0F).row().info().uri());
            assertNull(page.hit(row.right() + 1, y, layout, about, 0F));
            assertTrue(page.scrollCardStrip(lastX, y, layout, about, 0F, 20F, 0F));
            assertEquals("Skija", page.hit(firstX, y, layout, about, 0F).row().info().value());

            var header = page.rowRect(about, rows, index - 1, 0F, layout);
            assertTrue(page.perform(page.hit(firstX, header.y() + header.h() / 2, layout, about, 0F)));
            assertFalse(page.themesUnderPointer(firstX, y, layout, about, 0F));
        } finally {
            config.graphicsQuality = (previous ? com.atom.chat.config.GraphicsQuality.PERFORMANCE : com.atom.chat.config.GraphicsQuality.ULTRA);
        }
    }
}
