package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.wallpaper.WallpaperStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemePresetStoreTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lK3Q6wAAAABJRU5ErkJggg==");

    @TempDir
    Path tmp;

    private ThemePresetStore store;

    @BeforeEach
    void setUp() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return tmp.resolve("config");
            }

            @Override
            public Path gameDir() {
                return tmp.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
        store = new ThemePresetStore(tmp.resolve("presets"));
        WallpaperStore.init(tmp.resolve("wallpaper"));
        WallpaperStore.clear();
    }

    @AfterEach
    void tearDown() {
        WallpaperStore.init(null);
        WallpaperStore.clear();
    }

    private Path wallpaperFile(String name) throws IOException {
        Path file = tmp.resolve(name);
        Files.write(file, PNG);
        return file;
    }

    private static void mutateEverything(AtomChatConfig config) {
        config.accentColor = 0xFF010203;
        config.ownBubbleColor = 0xFF040506;
        config.bubbleTextColor = 0xFF070809;
        config.otherBubbleColor = 0xFF0A0B0C;
        config.otherBubbleTextColor = 0xFF0D0E0F;
        config.panelBgColor = 0xFF101112;
        config.textPrimaryColor = 0xFF131415;
        config.textSecondaryColor = 0xFF161718;
        config.cardColor = 0xFF191A1B;
        config.panelOutlineColor = 0xFF1C1D1E;
        config.secondaryCapsuleBg = 0xFF1F2021;
        config.secondaryCapsuleText = 0xFF222324;
        config.panelOpacity = 0.42F;
        config.blurEnabled = false;
        config.panelOutline = false;
        config.cardTint = 0.61F;
        config.cornerRadius = 7.5F;
        config.panelWidth = 512.0F;
        config.panelHeight = 700.0F;
        config.uiScale = 1.2F;
        config.contentScale = 1.15F;
        config.animationEnabled = false;
        config.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        config.themeName = "custom";
    }

    @Test
    void saveThenApplyRestoresEveryWhitelistedFieldAndWallpaper() throws IOException {
        AtomChatConfig saved = new AtomChatConfig();
        mutateEverything(saved);
        ThemePresetStore.Result savedResult = store.save("夜晚", saved, wallpaperFile("source.png"));
        assertTrue(savedResult.ok(), savedResult.error());
        assertNotNull(savedResult.preset());
        assertTrue(savedResult.preset().hasWallpaper());

        // The source may disappear: the preset owns a real copy.
        Files.delete(tmp.resolve("source.png"));

        AtomChatConfig target = new AtomChatConfig();
        ThemePresetStore.Result applied = store.apply(savedResult.preset().id(), target);
        assertTrue(applied.ok(), applied.error());
        assertEquals(0xFF010203, target.accentColor);
        assertEquals(0xFF222324, target.secondaryCapsuleText);
        assertEquals(0.42F, target.panelOpacity, 0.0001F);
        assertFalse(target.blurEnabled);
        assertFalse(target.panelOutline);
        assertEquals(0.61F, target.cardTint, 0.0001F);
        assertEquals(7.5F, target.cornerRadius, 0.0001F);
        assertEquals(512.0F, target.panelWidth, 0.0001F);
        assertEquals(700.0F, target.panelHeight, 0.0001F);
        assertEquals(1.2F, target.uiScale, 0.0001F);
        assertEquals(1.15F, target.contentScale, 0.0001F);
        assertFalse(target.animationEnabled);
        assertEquals(AtomChatConfig.PageNavStyle.SLIDE, target.pageNavStyle);
        assertEquals("custom", target.themeName);
        assertTrue(WallpaperStore.isSet());
        assertEquals(PNG.length, Files.size(WallpaperStore.current()));
    }

    @Test
    void applyingPresetWithoutWallpaperClearsCurrentWallpaper() throws IOException {
        WallpaperStore.set(wallpaperFile("current.png"));
        assertTrue(WallpaperStore.isSet());
        AtomChatConfig config = new AtomChatConfig();
        ThemePresetStore.Result result = store.save("纯色", config, null);
        assertTrue(result.ok(), result.error());

        AtomChatConfig target = new AtomChatConfig();
        assertTrue(store.apply(result.preset().id(), target).ok());
        assertFalse(WallpaperStore.isSet());
    }

    @Test
    void overwriteRenameDeleteAndReloadRoundTrip() throws IOException {
        AtomChatConfig first = new AtomChatConfig();
        mutateEverything(first);
        String id = store.save("旧名", first, null).preset().id();

        AtomChatConfig second = new AtomChatConfig();
        second.accentColor = 0xFF999999;
        assertTrue(store.overwrite(id, second, wallpaperFile("new.png")).ok());

        assertTrue(store.rename(id, "新名").ok());
        ThemePresetStore reloaded = new ThemePresetStore(tmp.resolve("presets"));
        assertEquals(1, reloaded.list().size());
        assertEquals("新名", reloaded.list().get(0).name());
        assertEquals(0xFF999999, reloaded.list().get(0).snapshot().accentColor());
        assertTrue(reloaded.list().get(0).hasWallpaper());

        assertTrue(reloaded.delete(id).ok());
        assertTrue(reloaded.list().isEmpty());
        assertFalse(new ThemePresetStore(tmp.resolve("presets")).delete(id).ok());
    }

    @Test
    void rejectsBadNamesTooManyPresetsAndBadJsonWithoutTouchingState() throws IOException {
        AtomChatConfig config = new AtomChatConfig();
        assertFalse(store.save("   ", config, null).ok());
        assertFalse(store.save("a".repeat(49), config, null).ok());
        assertTrue(store.save("😀".repeat(48), config, null).ok());

        for (int i = 0; i < ThemePresetStore.MAX_PRESETS - 1; i++) {
            assertTrue(store.save("p" + i, config, null).ok());
        }
        assertEquals(ThemePresetStore.MAX_PRESETS, store.list().size());
        assertFalse(store.save("overflow", config, null).ok());
        assertEquals(ThemePresetStore.MAX_PRESETS, store.list().size());

        Path broken = tmp.resolve("presets").resolve("broken-id").resolve("preset.json");
        Files.createDirectories(broken.getParent());
        Files.writeString(broken, "{not json");
        assertEquals(ThemePresetStore.MAX_PRESETS, new ThemePresetStore(tmp.resolve("presets")).list().size());
    }

    @Test
    void rejectsMissingWallpaperTooLargeAndPathTraversalOnApply() throws IOException {
        AtomChatConfig config = new AtomChatConfig();
        String id = store.save("带壁纸", config, wallpaperFile("ok.png")).preset().id();

        AtomChatConfig target = new AtomChatConfig();
        target.accentColor = 0xFFABCDEF;
        Path wallpaper = tmp.resolve("presets").resolve(id).resolve("wallpaper.png");
        Files.delete(wallpaper);
        assertFalse(store.apply(id, target).ok());
        assertEquals(0xFFABCDEF, target.accentColor);

        assertEquals("invalid_id", store.apply("../../etc", target).error());
        assertEquals("invalid_id", store.delete("../x").error());
    }

    @Test
    void oversizedWallpaperIsRejectedAtSave() throws IOException {
        Path huge = tmp.resolve("huge.png");
        byte[] bytes = new byte[16 * 1024 * 1024 + 1];
        bytes[0] = (byte) 0x89;
        Files.write(huge, bytes);
        AtomChatConfig config = new AtomChatConfig();
        ThemePresetStore.Result result = store.save("太大", config, huge);
        assertFalse(result.ok());
        assertTrue(store.list().isEmpty());
    }

    @Test
    void missingWallpaperAfterRestartNeverClearsTheCurrentWallpaper() throws IOException {
        String id = store.save("snapshot", new AtomChatConfig(), wallpaperFile("saved.png")).preset().id();
        Files.delete(tmp.resolve("presets").resolve(id).resolve("wallpaper.png"));
        assertTrue(WallpaperStore.set(wallpaperFile("active.png")));
        AtomChatConfig target = new AtomChatConfig();
        target.accentColor = 123;
        assertFalse(new ThemePresetStore(tmp.resolve("presets")).apply(id, target).ok());
        assertEquals(123, target.accentColor);
        assertTrue(WallpaperStore.isSet());
    }

    @Test
    void applyRevalidatesChangedManifestEvenAfterListWasCached() throws IOException {
        String id = store.save("valid", new AtomChatConfig(), null).preset().id();
        store.list();
        Files.writeString(tmp.resolve("presets").resolve(id).resolve("preset.json"), "{}");
        AtomChatConfig target = new AtomChatConfig();
        target.accentColor = 456;
        assertFalse(store.apply(id, target).ok());
        assertEquals(456, target.accentColor);
    }

    @Test
    void failedConfigWriteRestoresTheActiveAppearanceAndWallpaper() throws IOException {
        String id = store.save("plain", new AtomChatConfig(), null).preset().id();
        assertTrue(WallpaperStore.set(wallpaperFile("keep.png")));
        Files.createDirectories(tmp.resolve("config/atomchat/atomchat-client.json"));
        AtomChatConfig target = new AtomChatConfig();
        target.accentColor = 789;
        assertFalse(store.apply(id, target).ok());
        assertEquals(789, target.accentColor);
        assertTrue(WallpaperStore.isSet());
    }
}
