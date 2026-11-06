package com.atom.chat.settings;

import com.atom.chat.platform.Platform;
import com.atom.chat.ui.UiLayout;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsSectionRowFrameReuseTest {
    @Test
    void measureAndDrawBuildTheRowListOncePerFrame(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String modId) { return false; }
        });
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10.0F, 10.0F, 420.0F, 760.0F);
        try (Surface surface = Surface.makeRasterN32Premul(460, 820)) {
            page.measureContent(layout, SettingsSection.PRIVACY);
            page.render(surface.getCanvas(), layout, SettingsSection.PRIVACY,
                    -1.0F, -1.0F, 0.0F, 0xFF3B82F6);
            assertEquals(1L, page.rowBuildsForTest(),
                    "the draw pass must reuse the rows measured for the same frame");

            page.selectChip(SettingsSection.APPEARANCE, 1);
            page.measureContent(layout, SettingsSection.APPEARANCE);
            page.render(surface.getCanvas(), layout, SettingsSection.APPEARANCE,
                    -1.0F, -1.0F, 0.0F, 0xFF3B82F6);
            assertEquals(2L, page.rowBuildsForTest(),
                    "the next frame rebuilds once and its draw still reuses that list");
        }
    }

    @Test
    void rowRectsDoNotRemeasureThePrefixForEveryRow(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String modId) { return false; }
        });
        SettingsSectionPage page = new SettingsSectionPage();
        UiLayout layout = UiLayout.ofDetail(10.0F, 10.0F, 420.0F, 760.0F);
        SettingsSectionPage.Row textRow = page.rows(SettingsSection.CHAT).stream()
                .filter(row -> row.kind() == SettingsSectionPage.RowKind.SWITCH)
                .findFirst()
                .orElseThrow();
        List<SettingsSectionPage.Row> rows = Collections.nCopies(80, textRow);

        long before = page.compactTextBuildsForTest();
        for (int i = 0; i < rows.size(); i++) {
            page.rowRect(SettingsSection.CHAT, rows, i, 0.0F, layout);
        }

        long measured = page.compactTextBuildsForTest() - before;
        // The old prefix walk measured rows 0..i for every rowRect call (about 3,240
        // text measurements for 80 rows). Geometry is O(n), with a small allowance
        // for callers that legitimately measure the row while building it.
        assertEquals(rows.size(), measured,
                "one frame should measure each settings row once, not once per prefix step");
    }
}
