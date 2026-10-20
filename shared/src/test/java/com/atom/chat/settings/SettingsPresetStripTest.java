package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPresetStripTest {
    @TempDir
    Path tmp;

    private SettingsSectionPage page;
    private UiLayout layout;
    private final List<String> actions = new ArrayList<>();
    private java.lang.reflect.Field provider, configInstance;
    private Object previousProvider, previousConfig;

    @BeforeEach
    void setUp() throws Exception {
        provider = Platform.class.getDeclaredField("provider");
        configInstance = AtomChatConfig.class.getDeclaredField("instance");
        provider.setAccessible(true);
        configInstance.setAccessible(true);
        previousProvider = provider.get(null);
        previousConfig = configInstance.get(null);
        Platform.install(new Platform.Provider() {
            @Override public Path configDir() { return tmp.resolve("config"); }
            @Override public Path gameDir() { return tmp.resolve("game"); }
            @Override public boolean isModLoaded(String modId) { return false; }
        });
        AtomChatConfig.reload();
        AppearancePresetStore.init(tmp.resolve("appearance-presets"));
        page = new SettingsSectionPage();
        page.setActionHandler(actions::add);
        layout = UiLayout.ofDetail(10, 10, 520, 900);
    }

    @AfterEach void restoreState() throws Exception {
        provider.set(null, previousProvider);
        configInstance.set(null, previousConfig);
    }

    @Test
    void aSavedAppearanceExcludesTheDefaultThemeSelectionBorder() throws Exception {
        AtomChatConfig.reload();
        AtomChatConfig cfg = AtomChatConfig.get();
        cfg.animationEnabled = false;
        cfg.themeName = "";
        try (Surface surface = Surface.makeRasterN32Premul(560, 940);
             Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(560, 940);
            Canvas canvas = surface.getCanvas();
            page.render(canvas, layout, SettingsSection.APPEARANCE, -1, -1, 0, cfg.accentColor);
            UiLayout.Rect row = page.themesRowRect(layout, SettingsSection.APPEARANCE, 0);
            assertNotNull(row);
            byte[] selectedDefault = readPixels(surface, bitmap);
            var saved = AppearancePresetStore.get().saveCurrent(cfg, null);
            assertTrue(saved.ok(), saved.error());
            assertTrue(AppearancePresetStore.get().apply(saved.preset().id(), cfg).ok());
            canvas.clear(0);
            page.render(canvas, layout, SettingsSection.APPEARANCE, -1, -1, 0, cfg.accentColor);
            byte[] savedSelected = readPixels(surface, bitmap);
            cfg.themeName = "custom"; // No matching built-in or saved snapshot: borderless reference.
            canvas.clear(0);
            page.render(canvas, layout, SettingsSection.APPEARANCE, -1, -1, 0, cfg.accentColor);
            byte[] borderless = readPixels(surface, bitmap);
            int x = Math.round(row.x() + UiTokens.SETTINGS_ROW_PAD);
            int y = Math.round(row.y() + UiTokens.s(28));
            int w = Math.round(UiTokens.THEME_CARD_W);
            int h = Math.round(UiTokens.THEME_CARD_H);
            assertTrue(differences(selectedDefault, borderless, x, y, w, h) > 20,
                    "the fixture must observe the default card selection edge");
            assertEquals(0, differences(savedSelected, borderless, x, y, w, h),
                    "applying a matching saved appearance removes the default selection edge");
        }
    }

    private static byte[] readPixels(Surface surface, Bitmap bitmap) {
        assertTrue(surface.readPixels(bitmap, 0, 0));
        return bitmap.readPixels();
    }

    private static int differences(byte[] a, byte[] b, int x, int y, int w, int h) {
        int count = 0;
        for (int py = y; py < y + h; py++) for (int px = x; px < x + w; px++) {
            int i = (py * 560 + px) * 4;
            for (int c = 0; c < 4; c++) if (a[i+c] != b[i+c]) { count++; break; }
        }
        return count;
    }

    @Test
    void customCardsApplyDeleteAndTheTrailingPlusSaves() throws Exception {
        AtomChatConfig config = new AtomChatConfig();
        config.accentColor = 0xFF123456;
        AppearancePresetStore.Result saved = AppearancePresetStore.get().saveCurrent(config, null);
        assertTrue(saved.ok(), saved.error());
        String id = saved.preset().id();

        try (Surface surface = Surface.makeRasterN32Premul(560, 940)) {
            Canvas canvas = surface.getCanvas();
            page.render(canvas, layout, SettingsSection.APPEARANCE, -1, -1, 0, 0xFF4A90E2);
            page.requestRevealLatestPreset();
            page.render(canvas, layout, SettingsSection.APPEARANCE, -1, -1, 0, 0xFF4A90E2);
            UiLayout.Rect rect = page.themesRowRect(layout, SettingsSection.APPEARANCE, 0);
            assertNotNull(rect);
            float cardY = rect.y() + UiTokens.s(28) + UiTokens.THEME_CARD_H / 2.0F;
            float customX = findAction(rect, cardY, "theme_preset_apply:");
            assertEquals(List.of("theme_preset_apply:" + id), actions);

            actions.clear();
            findActionFrom(rect, rect.y() + UiTokens.s(38), customX, "theme_preset_delete:");
            assertEquals(List.of("theme_preset_delete:" + id), actions,
                    "the remove key wins over applying the card beneath it");

            actions.clear();
            findAction(rect, cardY, "theme_preset_save");
            assertEquals(List.of("theme_preset_save"), actions,
                    "the trailing + saves immediately and is always last");

            writePng(surface, "settings-presets-strip.png");
        }
    }

    @Test
    void everySettingsTabUsesTheConfirmedGroupHeadings() {
        assertGroupHeadings(SettingsSection.APPEARANCE, "theme",
                "atomchat.settings.group.appearance.presets",
                "atomchat.settings.group.appearance.corners",
                "atomchat.settings.group.appearance.message_colors",
                "atomchat.settings.group.appearance.ui_colors");
        assertGroupHeadings(SettingsSection.CHAT, "messages",
                "atomchat.settings.group.chat.input",
                "atomchat.settings.group.chat.messages_display",
                "atomchat.settings.group.chat.interaction");
        assertTrue(page.rows(SettingsSection.PRIVACY).stream().anyMatch(r ->
                r.kind() == SettingsSectionPage.RowKind.LABEL
                        && "atomchat.settings.group.privacy.blocked".equals(r.labelKey())));
        assertTrue(page.rows(SettingsSection.ABOUT).stream().anyMatch(r ->
                r.kind() == SettingsSectionPage.RowKind.LABEL
                        && "atomchat.settings.about.modinfo".equals(r.labelKey())));
    }

    @Test
    void mergedColourCardsKeepPerRowAndPerSwatchHoverIdentity() throws Exception {
        page.selectChip(SettingsSection.APPEARANCE, 0);
        List<SettingsSectionPage.Row> rows = page.rows(SettingsSection.APPEARANCE);
        List<Integer> colourRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind() == SettingsSectionPage.RowKind.COLOR) colourRows.add(i);
        }
        assertTrue(colourRows.size() >= 2, "the merged card must contain several colour rows");

        UiLayout tall = UiLayout.ofDetail(10, 10, 520, 1480);
        try (Surface surface = Surface.makeRasterN32Premul(560, 1500);
             Bitmap bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(560, 1500);
            page.render(surface.getCanvas(), tall, SettingsSection.APPEARANCE,
                    -1, -1, 0, 0xFF4A90E2);
            for (int row : List.of(colourRows.get(0), colourRows.get(1))) {
                ColourProbe probe = findColourHit(tall, rows.get(row));
                assertNotNull(probe, "a visible swatch is hittable in row " + row);
                page.render(surface.getCanvas(), tall, SettingsSection.APPEARANCE,
                        -1, -1, 0, 0xFF4A90E2);
                assertTrue(surface.readPixels(bitmap, 0, 0));
                byte[] idle = bitmap.readPixels();
                for (int frame = 0; frame < 8; frame++) {
                    Thread.sleep(20L);
                    page.render(surface.getCanvas(), tall, SettingsSection.APPEARANCE,
                            probe.pointerX(), probe.pointerY(), 0, 0xFF4A90E2);
                }
                assertTrue(surface.readPixels(bitmap, 0, 0));
                byte[] hovered = bitmap.readPixels();
                assertTrue(regionChanged(idle, hovered, 560,
                                (int) probe.pointerX(), (int) probe.pointerY(), 24),
                        "hovering a swatch in merged colour row " + row + " changes its local pixels");
            }
        }
    }

    private static boolean regionChanged(byte[] before, byte[] after, int width,
                                         int cx, int cy, int radius) {
        int changed = 0;
        for (int y = Math.max(0, cy - radius); y <= Math.min(after.length / 4 / width - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(width - 1, cx + radius); x++) {
                int i = (y * width + x) * 4;
                if (before[i] != after[i] || before[i + 1] != after[i + 1]
                        || before[i + 2] != after[i + 2] || before[i + 3] != after[i + 3]) {
                    changed++;
                }
            }
        }
        return changed > 40;
    }


    private record ColourProbe(SettingsSectionPage.ColorHit hit, float pointerX, float pointerY) {
    }

    private ColourProbe findColourHit(UiLayout layout, SettingsSectionPage.Row target) {
        for (float y = layout.list.y(); y < layout.list.bottom(); y += UiTokens.s(5)) {
            for (float x = layout.list.x(); x < layout.list.right(); x += UiTokens.s(5)) {
                SettingsSectionPage.ColorHit hit = page.colorHit(x, y, layout,
                        SettingsSection.APPEARANCE, 0);
                if (hit != null && hit.color() == target.color()) {
                    return new ColourProbe(hit, x, y);
                }
            }
        }
        return null;
    }

    private void assertGroupHeadings(SettingsSection section, String chip, String... keys) {
        int index = java.util.stream.IntStream.range(0, page.chips(section).size())
                .filter(i -> page.chips(section).get(i).id().equals(chip)).findFirst().orElseThrow();
        page.selectChip(section, index);
        List<String> labels = page.rows(section).stream()
                .filter(r -> r.kind() == SettingsSectionPage.RowKind.LABEL)
                .map(SettingsSectionPage.Row::labelKey).toList();
        for (String key : keys) {
            assertTrue(labels.contains(key), section + " is missing " + key + ": " + labels);
        }
    }

    private void press(float x, float y) {
        assertTrue(page.themesPress(x, y, layout, SettingsSection.APPEARANCE, 0),
                "press outside strip at " + x + "," + y);
        page.endThemeStrip();
    }

    private float findAction(UiLayout.Rect rect, float y, String prefix) {
        return findActionFrom(rect, y, rect.x(), prefix);
    }

    private float findActionFrom(UiLayout.Rect rect, float y, float fromX, String prefix) {
        for (float x = Math.max(rect.x(), fromX); x <= rect.right(); x += UiTokens.s(2)) {
            actions.clear();
            press(x, y);
            if (!actions.isEmpty() && actions.get(0).startsWith(prefix)) {
                return x;
            }
        }
        throw new AssertionError("no " + prefix + " action in the rendered strip");
    }

    private static void writePng(Surface surface, String name) throws Exception {
        Path dir = Path.of(System.getProperty("atomchat.repoRoot", "."),
                ".work/2026-10-19-settings/evidence/png");
        Files.createDirectories(dir);
        try (io.github.humbleui.skija.Image image = surface.makeImageSnapshot();
             Data data = image.encodeToData(EncodedImageFormat.PNG)) {
            Files.write(dir.resolve(name), data.getBytes());
        }
    }
}
