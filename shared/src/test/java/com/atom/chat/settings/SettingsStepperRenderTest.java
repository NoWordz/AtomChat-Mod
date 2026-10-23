package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class SettingsStepperRenderTest {
    @BeforeAll static void setup(@TempDir Path dir) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir; }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void authorsHaveIndependentSurfacesWithoutAnOpaqueBridge() throws Exception {
        var configField = AtomChatConfig.class.getDeclaredField("instance");
        configField.setAccessible(true);
        Object old = configField.get(null);
        var cfg = new AtomChatConfig(); cfg.animationEnabled = false; cfg.cardTint = 1;
        cfg.cardColor = 0xFFFFFFFF; configField.set(null, cfg);
        try (var surface = Surface.makeRasterN32Premul(1000, 1200); var bitmap = new Bitmap()) {
            var page = new SettingsSectionPage();
            var layout = UiLayout.ofDetail(0, 0, 1000, 1200);
            var rows = page.rows(SettingsSection.ABOUT);
            int index = -1;
            for (int i = 0; i < rows.size(); i++)
                if (rows.get(i).kind() == SettingsSectionPage.RowKind.AUTHORS) index = i;
            assertTrue(index >= 0);
            var rect = page.rowRect(SettingsSection.ABOUT, rows, index, 0, layout);
            int save = surface.getCanvas().getSaveCount();
            for (int frame = 0; frame < 3; frame++) {
                surface.getCanvas().clear(0);
                page.render(surface.getCanvas(), layout, SettingsSection.ABOUT, -1, -1, 0, 0xFF4488CC);
                assertEquals(save, surface.getCanvas().getSaveCount());
                bitmap.allocN32Pixels(1000, 1200); assertTrue(surface.readPixels(bitmap, 0, 0));
                byte[] pixels = bitmap.readPixels();
                int x = Math.round(rect.x() + rect.w() / 2);
                int y = Math.round(rect.y() + rect.h() / 2);
                int seam = (y * 1000 + x) * 4;
                assertTrue(Byte.toUnsignedInt(pixels[seam + 3]) < 255,
                        "the seam may contain soft shadows, but never an opaque outer card");
                assertEquals(0, Byte.toUnsignedInt(pixels[seam]), "no white card fill between authors");
                int face = (y * 1000 + Math.round(rect.x() + UiTokens.SETTINGS_ROW_PAD)) * 4;
                assertEquals(255, Byte.toUnsignedInt(pixels[face + 3]), "real card faces still draw");
            }
            Path output = Path.of("build/test-artifacts/authors-preview.png");
            Files.createDirectories(output.getParent());
            try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
                Files.write(output, data.getBytes());
            }
        } finally { configField.set(null, old); }
    }

    @Test void selectorsOnlyReturnArrowActionsAndHudLayoutOpensAnEditor() throws Exception {
        var configField = AtomChatConfig.class.getDeclaredField("instance"); configField.setAccessible(true);
        Object old = configField.get(null);
        var cfg = new AtomChatConfig(); cfg.animationEnabled = false; configField.set(null, cfg);
        try {
            for (float width : new float[]{240, 1000}) {
                checkSelector(width, SettingsSection.CHAT, 0, "avatar_style");
                checkSelector(width, SettingsSection.CHAT, 3, "teleport_mode");
                checkSelector(width, SettingsSection.APPEARANCE, 1, "page_nav_style");
            }
            var page = new SettingsSectionPage(); page.selectChip(SettingsSection.CHAT, 2);
            var rows = page.rows(SettingsSection.CHAT);
            assertEquals(1, rows.stream().filter(r -> "hud_edit".equals(r.actionId())).count());
            assertFalse(rows.stream().anyMatch(r -> "hud_position".equals(r.actionId())
                    || r.slider() != null && r.slider().id().startsWith("hud_offset_")));
        } finally { configField.set(null, old); }
    }

    @Test void arrowHoverWeightsAnimateIndependentlyAndSettleExactly() {
        var hover = new SettingsStepper.HoverState();
        var avatarLeft = new SettingsStepper.ArrowHover("avatar_style", -1);
        var avatarRight = new SettingsStepper.ArrowHover("avatar_style", 1);
        var teleportLeft = new SettingsStepper.ArrowHover("teleport_mode", -1);

        hover.advance(avatarLeft, 10.0F, true);
        float leftRising = hover.weight("avatar_style", -1);
        assertTrue(leftRising > 0.0F && leftRising < 1.0F,
                "the first painted frame must not snap the arrow wash to full opacity");

        hover.advance(avatarRight, 10.0F, true);
        float rightRising = hover.weight("avatar_style", 1);
        assertTrue(rightRising > 0.0F && rightRising < 1.0F);
        assertTrue(hover.weight("avatar_style", -1) > 0.0F,
                "the opposite arrow fades out independently instead of sharing one float");

        hover.advance(teleportLeft, 10.0F, true);
        assertTrue(hover.weight("teleport_mode", -1) > 0.0F);

        for (int frame = 0; frame < 200; frame++) hover.advance(avatarRight, 10.0F, true);
        assertEquals(1.0F, hover.weight("avatar_style", 1), 0.0F);
        assertEquals(0.0F, hover.weight("avatar_style", -1), 0.0F);
        assertEquals(0.0F, hover.weight("teleport_mode", -1), 0.0F);

        hover.advance(null, 10.0F, true);
        assertTrue(hover.weight("avatar_style", 1) > 0.0F
                && hover.weight("avatar_style", 1) < 1.0F,
                "leaving the row starts a visible fade instead of clearing the wash");
        for (int frame = 0; frame < 200; frame++) hover.advance(null, 10.0F, true);
        assertEquals(0.0F, hover.weight("avatar_style", 1), 0.0F);
    }

    @Test void disabledMotionSnapsOnlyTheActiveArrow() {
        var hover = new SettingsStepper.HoverState();
        var left = new SettingsStepper.ArrowHover("page_nav_style", -1);
        var right = new SettingsStepper.ArrowHover("page_nav_style", 1);
        hover.advance(left, 1.0F, true);

        hover.advance(right, 1.0F, false);
        assertEquals(0.0F, hover.weight("page_nav_style", -1), 0.0F);
        assertEquals(1.0F, hover.weight("page_nav_style", 1), 0.0F);
        hover.advance(null, 1.0F, false);
        assertEquals(0.0F, hover.weight("page_nav_style", 1), 0.0F);
    }

    private static void checkSelector(float width, SettingsSection section, int chip, String action) throws Exception {
        var page = new SettingsSectionPage(); page.selectChip(section, chip);
        var layout = UiLayout.ofDetail(0, 0, width, 1800);
        var rows = page.rows(section);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); if (!action.equals(row.actionId())) continue;
            var rect = page.rowRect(section, rows, i, 0, layout);
            var geometry = page.stepperGeometry(row, rect);
            var left = page.hit(geometry.previous().x() + geometry.previous().w() / 2,
                    geometry.previous().y() + geometry.previous().h() / 2, layout, section, 0);
            var right = page.hit(geometry.next().x() + geometry.next().w() / 2,
                    geometry.next().y() + geometry.next().h() / 2, layout, section, 0);
            assertNotNull(left); assertNotNull(right);
            assertEquals(action + ":prev", left.row().actionId());
            assertEquals(action, right.row().actionId());
            assertNull(page.hit(rect.x() + UiTokens.SETTINGS_ROW_PAD, rect.y() + 1, layout, section, 0));
            if (geometry.value().w() > 0) assertNull(page.hit(
                    geometry.value().x() + geometry.value().w() / 2,
                    geometry.value().y() + geometry.value().h() / 2, layout, section, 0));
            try (var surface = Surface.makeRasterN32Premul(1100, 1800)) {
                for (int frame = 0; frame < 3; frame++) {
                    int save = surface.getCanvas().getSaveCount();
                    page.render(surface.getCanvas(), layout, section, -1, -1, 0, 0xFF4488CC);
                    assertEquals(save, surface.getCanvas().getSaveCount());
                }
                Path output = Path.of("build/test-artifacts/stepper-" + action + ".png");
                Files.createDirectories(output.getParent());
                try (var image = surface.makeImageSnapshot(); var data = image.encodeToData()) {
                    Files.write(output, data.getBytes());
                }
            }
            return;
        }
        fail("fixture must expose " + action);
    }
}
