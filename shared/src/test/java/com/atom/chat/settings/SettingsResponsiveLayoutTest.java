package com.atom.chat.settings;

import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SettingsResponsiveLayoutTest {
    @BeforeAll static void setup(@TempDir Path dir) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return dir; }
            public Path gameDir() { return dir; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test void longTitleGetsItsOwnSpaceWithoutReducingTheFont() {
        SettingsSectionPage page = new SettingsSectionPage();
        SettingsItem item = new SettingsItem("long", "A long setting title that must remain readable", ".", () -> true, value -> {});
        SettingsSectionPage.Row row = SettingsSectionPage.Row.ofSwitch(item);
        float narrow = page.rowHeight(row, UiLayout.ofDetail(0, 0, 240, 600));
        float wide = page.rowHeight(row, UiLayout.ofDetail(0, 0, 1000, 600));
        assertTrue(narrow > wide, "the title must wrap and move the switch below it");
    }

    @Test void narrowSettingsRenderMultipleFramesAndStayScrollable() {
        SettingsSectionPage page = new SettingsSectionPage();
        try (Surface surface = Surface.makeRasterN32Premul(400, 600)) {
            for (float width : new float[]{210, 240, 320, 600}) {
                UiLayout layout = UiLayout.ofDetail(0, 0, width, 300);
                for (SettingsSection section : SettingsSection.values()) {
                    for (int frame = 0; frame < 3; frame++) {
                        int save = surface.getCanvas().getSaveCount();
                        page.render(surface.getCanvas(), layout, section, -1, -1, 0, 0xFF4488CC);
                        assertEquals(save, surface.getCanvas().getSaveCount());
                        assertTrue(Float.isFinite(page.measureContent(layout, section)));
                    }
                }
            }
        }
    }

    @Test void scrollEndLeavesTheSameClearanceAsTheCardSides() {
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(0, 0, 420, 180);
        var rows = page.rows(SettingsSection.CHAT);
        float content = page.measureContent(layout, SettingsSection.CHAT);
        float scrollY = Math.max(0.0F, content - layout.list.h());
        assertTrue(scrollY > 0.0F, "this fixture must actually overflow and scroll");
        var last = page.rowRect(SettingsSection.CHAT, rows, rows.size() - 1, scrollY, layout);
        assertEquals(UiTokens.ROW_CLIP_INSET, layout.list.bottom() - last.bottom(), 0.01F,
                "the footer clearance must survive the hover spring instead of parking on the clip");
        assertEquals(UiTokens.LIST_PAD_X + UiTokens.ROW_CLIP_INSET,
                last.x() - layout.panelX, 0.01F,
                "the bottom and side breathing rooms share the same 23-unit outer margin");
    }
}
