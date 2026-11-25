package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.ChromeMaterial;
import com.atom.chat.settings.SettingsChipBar;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelShellCornersTest {
    @Test void headerComposerNavigationAndSettingsTabsUseCircularArcs() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        var config = new AtomChatConfig();
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
        config.cornerRadius = 25F;
        config.cardTint = 1F;
        field.set(null, config);
        try (var surface = Surface.makeRasterN32Premul(500, 500);
             var pixels = new Bitmap(); var bottom = new BottomChromeTransition()) {
            var canvas = surface.getCanvas();
            canvas.scale(32F, 32F);
            pixels.allocN32Pixels(500, 500);
            var historical = AtomChatAppearance.theme();
            config.cornerRadius = 29F;
            var header = new UiLayout.Rect(0F, 0F, 220F, UiTokens.HEADER_HEIGHT);
            var composer = new UiLayout.Rect(0F, 0F, 220F, UiTokens.INPUT_HEIGHT);
            var tabs = new UiLayout.Rect(0F, 0F, 220F, UiTokens.TAB_BAR_H);
            assertCircularCorner(surface, pixels, 0F, 0F, circularRadius(header, UiTokens.headerRadius(historical)),
                    () -> ShellHeader.render(canvas, header, "", false, null, 0F, 0), "header");
            for (var mode : List.of(BottomChromeTransition.Mode.CHAT, BottomChromeTransition.Mode.NAV)) {
                bottom.beginFrame(mode, composer, tabs, 1000, false);
                var bounds = bottom.paintedRect();
                assertCircularCorner(surface, pixels, bounds.x(), bounds.y(),
                        circularRadius(bounds, UiTokens.shellChromeRadius(historical)),
                        () -> bottom.draw(canvas, ignored -> {}), mode.name());
            }

            var initial = UiLayout.ofDetail(0F, 0F, 395F, 450F).chipBar;
            float inset = UiTokens.TAB_EDGE_PAD;
            var layout = UiLayout.ofDetail(-initial.x() - inset, -initial.y() - inset, 395F, 450F);
            var chips = new SettingsChipBar();
            chips.update(layout, 3, 0, -1, -400F, -400F, 16F);
            float radius = UiTokens.insetRadius(UiTokens.chromeRadius(historical), inset,
                    initial.w() / 3F - 2F * inset, UiTokens.CHIP_PILL_H);
            assertCircularCorner(surface, pixels, 0F, 0F, radius,
                    () -> chips.draw(canvas, layout, List.of("", "", ""), 0, 0), "settings tab");
            float cellWidth = layout.chipBar.w() / 3F;
            chips.update(layout, 3, 0, -1, cellWidth + initial.w() / 6F - inset,
                    UiTokens.CHIP_PILL_H / 2F, 1000F);
            canvas.translate(-cellWidth, 0F);
            assertCircularCorner(surface, pixels, 0F, 0F, radius,
                    () -> chips.draw(canvas, layout, List.of("", "", ""), 0, 0), "settings hover",
                    UiTokens.cardHover(1F, AtomChatAppearance.theme()) >>> 24);
        } finally {
            field.set(null, previous);
        }
    }

    private static float circularRadius(UiLayout.Rect bar, float requested) {
        return Math.min(requested, Math.min(bar.w(), bar.h()) / 2F);
    }

    private static void assertCircularCorner(Surface surface, Bitmap pixels, float x, float y, float radius,
                                             Runnable draw, String control) {
        assertCircularCorner(surface, pixels, x, y, radius, draw, control, 255);
    }

    private static void assertCircularCorner(Surface surface, Bitmap pixels, float x, float y, float radius,
                                             Runnable draw, String control, int alpha) {
        surface.getCanvas().clear(0);
        draw.run();
        assertTrue(surface.readPixels(pixels, 0, 0));
        // At about 28.6% of the nominal radius, the former continuous curve was filled.
        // A circular arc cuts back 29.29% on both axes.
        float outside = radius * .286F;
        assertEquals(0, pixels.getColor((int) ((x + outside) * 32F), (int) ((y + outside) * 32F)) >>> 24, control);
        float inside = radius * .36F;
        assertEquals(alpha, pixels.getColor((int) ((x + inside) * 32F), (int) ((y + inside) * 32F)) >>> 24, control);
    }

    @Test void selectedAndHoveredCapsulesFollowTheSharedSliderAndCircularCap() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        var config = new AtomChatConfig();
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
        config.accentColor = 0xFFDD3377;
        field.set(null, config);
        try (var surface = Surface.makeRasterN32Premul(560, 390); var pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            canvas.scale(1.25F, 1.25F);
            pixels.allocN32Pixels(560, 390);
            var root = new BottomTabBar();
            var bar = new UiLayout.Rect(16F, 20F, 360F, UiTokens.TAB_BAR_H);
            var layout = UiLayout.ofDetail(8F, 130F, 395F, 450F);
            var chips = new SettingsChipBar();
            root.update(50F, bar.x() + .5F * bar.w(), bar.y() + .5F * bar.h(), bar);
            chips.update(layout, 3, 0, -1, layout.chipBar.x() + .5F * layout.chipBar.w(),
                    layout.chipBar.y() + .5F * layout.chipBar.h(), 50F);
            int[] previousCoverage = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
            for (int radius : new int[]{12, 18, 25, 28, 30}) {
                config.cornerRadius = radius;
                canvas.clear(0);
                root.renderContents(canvas, bar, 0, 0, config.accentColor);
                chips.draw(canvas, layout, List.of("", "", ""), 0, 0);
                assertTrue(surface.readPixels(pixels, 0, 0));
                int[] coverage = {
                        capsuleCoverage(pixels, bar, 0), capsuleCoverage(pixels, bar, 1),
                        capsuleCoverage(pixels, layout.chipBar, 0), capsuleCoverage(pixels, layout.chipBar, 1)
                };
                for (int i = 0; i < coverage.length; i++) {
                    String label = "capsule " + i + " at " + radius;
                    assertTrue(coverage[i] > 0, label);
                    if (radius <= 25) {
                        assertTrue(coverage[i] < previousCoverage[i],
                                label + ": coverage " + previousCoverage[i] + " -> " + coverage[i]);
                    } else {
                        assertEquals(previousCoverage[i], coverage[i], label + " keeps the 0.9.3 radius cap");
                    }
                }
                previousCoverage = coverage;
            }
        } finally {
            field.set(null, previous);
        }
    }

    @Test void navigationSelectionAndHoverUseTheSameCircularArc() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        var config = new AtomChatConfig();
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
        config.cornerRadius = 25F;
        field.set(null, config);
        try (var surface = Surface.makeRasterN32Premul(500, 500); var pixels = new Bitmap()) {
            var canvas = surface.getCanvas();
            canvas.scale(32F, 32F);
            pixels.allocN32Pixels(500, 500);
            var theme = AtomChatAppearance.theme();
            float inset = UiTokens.TAB_EDGE_PAD;
            float width = 360F;
            float cellWidth = width / 3F;
            float capsuleWidth = cellWidth - 2F * inset;
            float capsuleHeight = UiTokens.TAB_BAR_H - 2F * inset;
            var bar = new UiLayout.Rect(-cellWidth - inset, -inset, width, UiTokens.TAB_BAR_H);
            var root = new BottomTabBar();
            root.update(1000F, capsuleWidth / 2F, capsuleHeight / 2F, bar);
            float radius = ChromeMaterial.circularInsetCornerRadius(capsuleWidth, capsuleHeight,
                    UiTokens.shellChromeRadius(theme), inset);
            assertCircularCorner(surface, pixels, 0F, 0F, radius,
                    () -> root.renderContents(canvas, bar, 0, 0, 0), "navigation hover",
                    UiTokens.cardHover(1F, theme) >>> 24);

            var selectedBar = new UiLayout.Rect(-inset, -inset, width, UiTokens.TAB_BAR_H);
            assertCircularCorner(surface, pixels, 0F, 0F, radius,
                    () -> root.renderContents(canvas, selectedBar, 0, 0, 0), "navigation selection");
        } finally {
            field.set(null, previous);
        }
    }

    private static int capsuleCoverage(Bitmap pixels, UiLayout.Rect bar, int cell) {
        int x = (int) ((bar.x() + bar.w() / 3F * cell + UiTokens.TAB_EDGE_PAD) * 1.25F);
        int y = (int) ((bar.y() + UiTokens.TAB_EDGE_PAD) * 1.25F);
        int coverage = 0;
        for (int dy = 0; dy < 24; dy++) {
            for (int dx = 0; dx < 24; dx++) coverage += pixels.getColor(x + dx, y + dy) >>> 24;
        }
        return coverage;
    }

    @Test void floatingBarsFollowTheLegacyRadiusLimitAndShortSideCap() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        var config = new AtomChatConfig();
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
        config.cardTint = 1F;
        field.set(null, config);
        try (var surface = Surface.makeRasterN32Premul(340, 320);
             var pixels = new Bitmap(); var bottom = new BottomChromeTransition()) {
            var canvas = surface.getCanvas();
            canvas.scale(1.25F, 1.25F);
            pixels.allocN32Pixels(340, 320);
            var header = new UiLayout.Rect(16F, 16F, 220F, UiTokens.HEADER_HEIGHT);
            var composer = new UiLayout.Rect(16F, 64F, 48F, UiTokens.INPUT_HEIGHT);
            var tabs = new UiLayout.Rect(16F, 184F, 220F, UiTokens.TAB_BAR_H);
            int[] previousCoverage = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
            float[] previousRadii = {-1F, -1F, -1F};
            for (int radius = 20; radius <= 30; radius++) {
                config.cornerRadius = Math.min(radius, 25F);
                var historical = AtomChatAppearance.theme();
                config.cornerRadius = radius;
                float chromeRadius = UiTokens.shellChromeRadius(historical);
                float[] radii = {circularRadius(header, UiTokens.headerRadius(historical)),
                        circularRadius(composer, chromeRadius), circularRadius(tabs, chromeRadius)};
                bottom.beginFrame(BottomChromeTransition.Mode.CHAT, composer, tabs, 1000, false);
                int[] coverage = {
                        cornerCoverage(surface, pixels, header,
                                () -> ShellHeader.render(canvas, header, "", false, null, 0F, 0)),
                        cornerCoverage(surface, pixels, composer, () -> bottom.draw(canvas, ignored -> {})),
                        cornerCoverage(surface, pixels, tabs, () -> {
                            bottom.beginFrame(BottomChromeTransition.Mode.NAV, composer, tabs, 1016, false);
                            bottom.draw(canvas, ignored -> {});
                        })
                };
                for (int bar = 0; bar < coverage.length; bar++) {
                    String label = "bar " + bar + " at " + radius;
                    if (radii[bar] == previousRadii[bar]) {
                        assertEquals(previousCoverage[bar], coverage[bar], label);
                    } else {
                        assertTrue(coverage[bar] < previousCoverage[bar], label + ": corner coverage "
                                + previousCoverage[bar] + " -> " + coverage[bar]);
                    }
                }
                previousCoverage = coverage;
                previousRadii = radii;
            }
        } finally {
            field.set(null, previous);
        }
    }

    private static int cornerCoverage(Surface surface, Bitmap pixels, UiLayout.Rect bar, Runnable draw) {
        surface.getCanvas().clear(0);
        draw.run();
        assertTrue(surface.readPixels(pixels, 0, 0));
        int x = (int) (bar.x() * 1.25F);
        int y = (int) (bar.y() * 1.25F);
        int coverage = 0;
        for (int dy = 0; dy < 22; dy++) {
            for (int dx = 0; dx < 22; dx++) {
                coverage += pixels.getColor(x + dx, y + dy) >>> 24;
            }
        }
        return coverage;
    }

    @Test void widePanelContourMovesBetween25And30() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        var config = new AtomChatConfig();
        config.panelOutlineColor = 0;
        field.set(null, config);
        try (var surface = Surface.makeRasterN32Premul(450, 350); var pixels = new Bitmap()) {
            var box = new UiLayout.Rect(16F, 16F, 400F, 310F);
            pixels.allocN32Pixels(450, 350);
            config.cornerRadius = 25F;
            PanelBackground.drawTint(surface.getCanvas(), box, 0xFFFFFFFF);
            assertTrue(surface.readPixels(pixels, 0, 0));
            int at25 = 0;
            for (int i = 16; i < 50; i++) at25 += pixels.getColor(i, i) >>> 24;
            surface.getCanvas().clear(0);
            config.cornerRadius = 30F;
            PanelBackground.drawTint(surface.getCanvas(), box, 0xFFFFFFFF);
            assertTrue(surface.readPixels(pixels, 0, 0));
            int at30 = 0;
            for (int i = 16; i < 50; i++) at30 += pixels.getColor(i, i) >>> 24;
            assertTrue(at25 > at30,
                    "a higher radius removes more of an unconstrained panel corner");
        } finally {
            field.set(null, previous);
        }
    }

    @Test void panelFillAndBackdropClipUseTheSameCircularArc() throws Exception {
        var field = AtomChatConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        var config = new AtomChatConfig();
        config.cornerRadius = 28F;
        config.panelOutlineColor = 0;
        field.set(null, config);
        try (var painted = Surface.makeRasterN32Premul(480, 360);
             var clipped = Surface.makeRasterN32Premul(480, 360);
             var fill = new Bitmap(); var clip = new Bitmap()) {
            var rect = new UiLayout.Rect(8F, 8F, 128F, 112F);
            painted.getCanvas().scale(3F, 3F);
            clipped.getCanvas().scale(3F, 3F);
            PanelBackground.drawTint(painted.getCanvas(), rect, 0xFFFFFFFF);
            PanelBackground.clipBackdrop(clipped.getCanvas(), rect);
            clipped.getCanvas().drawColor(0xFFFFFFFF);
            fill.allocN32Pixels(480, 360);
            clip.allocN32Pixels(480, 360);
            assertTrue(painted.readPixels(fill, 0, 0));
            assertTrue(clipped.readPixels(clip, 0, 0));
            float radius = UiTokens.panelRadius(AtomChatAppearance.theme());
            int corner = (int) (3F * (rect.x() + .276F * radius));
            assertEquals(0, fill.getColor(corner, corner) >>> 24);
            assertEquals(0, clip.getColor(corner, corner) >>> 24);
            int inside = (int) (3F * (rect.x() + .36F * radius));
            assertEquals(255, fill.getColor(inside, inside) >>> 24);
            assertEquals(255, clip.getColor(inside, inside) >>> 24);
            assertEquals(0, fill.getColor(25, 25) >>> 24);
            assertEquals(0, clip.getColor(25, 25) >>> 24);
        } finally {
            field.set(null, previous);
        }
    }
}
