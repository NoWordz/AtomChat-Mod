package com.atom.chat.theme;

import com.atom.chat.api.ui.AppearanceUi;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearancePresetStoreTest {
    @Test void unrelatedWallpaperBackupLinkDoesNotAffectThemeApplication() throws Exception {
        String id = legacyPreset(null);
        assertTrue(WallpaperStore.set(wallpaper("keep.png")));
        Path active = WallpaperStore.current();
        Path target = Files.createDirectories(tmp.resolve("config/private-backups"));
        Path link = tmp.resolve("appearance-presets/.wallpaper-backup");
        try { Files.createSymbolicLink(link,target); }
        catch (java.nio.file.FileSystemException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
            Process process = new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",link.toString(),target.toString())
                    .redirectErrorStream(true).start();
            assertTrue(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,process.exitValue());
        }
        assertTrue(store.apply(id,new AtomChatConfig()).ok());
        assertEquals(active,WallpaperStore.current());
        assertTrue(Files.isRegularFile(active));
        try (var files = Files.list(target)) { assertEquals(0,files.count()); }
    }
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lK3Q6wAAAABJRU5ErkJggg==");

    @Test void outlineWidthRoundTripsAndLegacyPresetsKeepTwoPixels() throws Exception {
        var c = new AtomChatConfig();
        AtomChatConfig.class.getMethod("setPanelOutlineWidth", float.class).invoke(c, 6F);
        var result = store.saveCurrent(c, null);
        assertTrue(result.ok());
        var target = new AtomChatConfig();
        assertTrue(store.apply(result.preset().id(), target).ok());
        assertEquals(6F, ((Number) AtomChatConfig.class.getMethod("effectivePanelOutlineWidth").invoke(target)).floatValue());
        Path manifest = tmp.resolve("appearance-presets").resolve(result.preset().id()).resolve("preset.json");
        var json = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        json.getAsJsonObject("appearance").remove("panelOutlineWidth");
        Files.writeString(manifest, json.toString());
        assertTrue(store.apply(result.preset().id(), target).ok());
        assertEquals(2F, ((Number) AtomChatConfig.class.getMethod("effectivePanelOutlineWidth").invoke(target)).floatValue());
    }

    @TempDir
    Path tmp;

    private AppearancePresetStore store;

    @Test
    void bubbleAppearanceSurvivesDiskReloadAndApply() {
        AtomChatConfig source = appearance();
        source.bubbleStyle = AtomChatConfig.BubbleStyle.CORNER;
        String id = store.saveCurrent(source, null).preset().id();
        AtomChatConfig target = new AtomChatConfig();
        assertTrue(new AppearancePresetStore(tmp.resolve("appearance-presets")).apply(id, target).ok());
        assertEquals(AtomChatConfig.BubbleStyle.CORNER, target.effectiveBubbleStyle());
        try {
            String saved = Files.readString(tmp.resolve("appearance-presets").resolve(id).resolve("preset.json"));
            assertFalse(saved.contains("messageEdgeDotsEnabled"), "decorations are no longer a preset preference");
        } catch (IOException error) { throw new AssertionError(error); }
    }

    @Test
    void oldPresetDefaultsBubbleAppearanceAndKeepsItsOtherValues() throws IOException {
        String id = store.saveCurrent(appearance(), null).preset().id();
        Path manifest = tmp.resolve("appearance-presets").resolve(id).resolve("preset.json");
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        root.getAsJsonObject("appearance").remove("bubbleStyle");
        root.getAsJsonObject("appearance").addProperty("messageEdgeDotsEnabled", false);
        Files.writeString(manifest, root.toString());
        AtomChatConfig target = new AtomChatConfig();
        target.bubbleStyle = AtomChatConfig.BubbleStyle.ROUND;
        assertTrue(store.apply(id, target).ok());
        assertEquals(AtomChatConfig.BubbleStyle.CLASSIC, target.effectiveBubbleStyle());
        assertEquals(0xFF010203, target.accentColor);
    }

    @BeforeEach
    void setUp() {
        Platform.install(new Platform.Provider() {
            @Override public Path configDir() { return tmp.resolve("config"); }
            @Override public Path gameDir() { return tmp.resolve("game"); }
            @Override public boolean isModLoaded(String modId) { return false; }
        });
        store = new AppearancePresetStore(tmp.resolve("appearance-presets"));
        AtomChatConfig.reload();
        WallpaperStore.init(tmp.resolve("wallpaper"));
        WallpaperStore.clear();
    }

    @AfterEach
    void tearDown() {
        WallpaperStore.init(null);
        WallpaperStore.clear();
    }

    private Path wallpaper(String name) throws IOException {
        Path file = tmp.resolve(name);
        Files.write(file, PNG);
        return file;
    }

    private static AtomChatConfig appearance() {
        AtomChatConfig c = new AtomChatConfig();
        c.accentColor = 0xFF010203;
        c.ownBubbleColor = 0xFF040506;
        c.bubbleTextColor = 0xFF070809;
        c.otherBubbleColor = 0xFF0A0B0C;
        c.otherBubbleTextColor = 0xFF0D0E0F;
        c.panelBgColor = 0xFF101112;
        c.textPrimaryColor = 0xFF131415;
        c.textSecondaryColor = 0xFF161718;
        c.cardColor = 0xFF191A1B;
        c.panelOutlineColor = 0xFF1C1D1E;
        c.secondaryCapsuleBg = 0xFF1F2021;
        c.secondaryCapsuleText = 0xFF222324;
        c.panelOpacity = 0.42F;
        c.blurEnabled = false;
        c.panelOutline = false;
        c.cardTint = 0.61F;
        c.cardBlurStrength = 2.35F;
        c.cornerRadius = 7.5F;
        c.panelWidth = 512.0F;
        c.panelHeight = 700.0F;
        c.uiScale = 1.2F;
        c.contentScale = 1.15F;
        c.animationEnabled = false;
        c.pageNavStyle = AtomChatConfig.PageNavStyle.SLIDE;
        c.themeName = "custom";
        return c;
    }

    @Test
    void saveCopiesEveryAppearanceFieldAndWallpaperButNoChatSettings() throws IOException {
        AtomChatConfig saved = appearance();
        saved.hideBlockedMessages = false;
        saved.notificationSoundEnabled = false;
        AppearancePresetStore.Result result = store.saveCurrent(saved, wallpaper("source.png"));
        assertTrue(result.ok(), result.error());
        assertTrue(result.preset().hasWallpaper());
        Files.move(tmp.resolve("source.png"), tmp.resolve("renamed.png"));

        AtomChatConfig target = new AtomChatConfig();
        target.hideBlockedMessages = true;
        target.notificationSoundEnabled = true;
        assertTrue(store.apply(result.preset().id(), target).ok());
        assertEquals(0xFF010203, target.accentColor);
        assertEquals(0xFF222324, target.secondaryCapsuleText);
        assertEquals(0.42F, target.panelOpacity, 0.0001F);
        assertFalse(target.blurEnabled);
        assertFalse(target.panelOutline);
        assertEquals(0.61F, target.cardTint, 0.0001F);
        assertEquals(2.35F, target.cardBlurStrength, 0.0001F);
        assertEquals(7.5F, target.cornerRadius, 0.0001F);
        assertEquals(512.0F, target.panelWidth, 0.0001F);
        assertEquals(700.0F, target.panelHeight, 0.0001F);
        assertEquals(1.2F, target.uiScale, 0.0001F);
        assertEquals(1.15F, target.contentScale, 0.0001F);
        assertFalse(target.animationEnabled);
        assertEquals(AtomChatConfig.PageNavStyle.SLIDE, target.pageNavStyle);
        assertEquals("custom", target.themeName);
        assertEquals(AppearancePresetStore.Snapshot.capture(saved), AppearancePresetStore.Snapshot.capture(target));
        assertTrue(target.hideBlockedMessages, "privacy is outside the appearance whitelist");
        assertTrue(target.notificationSoundEnabled, "sound is outside the appearance whitelist");
        assertArrayEquals(PNG, Files.readAllBytes(WallpaperStore.current()));
    }

    @Test
    void numberingUsesTheSmallestFreePositiveNumberWithoutRenumberingOthers() {
        String one = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        String two = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        String three = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        assertNotEquals(one, two);
        assertEquals(1, store.list().get(0).number());
        assertEquals(2, store.list().get(1).number());
        assertEquals(3, store.list().get(2).number());

        assertTrue(store.delete(two).ok());
        String replacement = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        assertEquals(2, store.list().stream()
                .filter(p -> p.id().equals(replacement)).findFirst().orElseThrow().number());
        assertEquals(replacement, store.list().get(store.list().size() - 1).id(),
                "a reused number is appended at the end, not inserted between its neighbours");
        assertEquals(1, store.list().get(0).number());
        assertEquals(3, store.list().stream()
                .filter(p -> p.id().equals(three)).findFirst().orElseThrow().number());
        assertTrue(store.list().stream().anyMatch(p -> p.id().equals(one)));
        assertEquals(replacement, new AppearancePresetStore(tmp.resolve("appearance-presets"))
                .list().get(2).id(), "the append order survives a fresh directory scan");
    }

    @Test
    void explicitNoWallpaperClearsTheActiveImageAndSurvivesRestart() throws IOException {
        var active = AtomChatConfig.get();
        active.pixelRadiusMigrated = true;
        String id = store.saveCurrent(active, null).preset().id();
        assertTrue(WallpaperStore.set(wallpaper("active.png")));
        assertTrue(store.apply(id, active).ok());
        assertNull(WallpaperStore.current());

        WallpaperStore.init(tmp.resolve("wallpaper"));
        AtomChatConfig.reload();
        var saved = store.refresh();
        assertEquals(id, AppearancePresetStore.selected(saved, AtomChatConfig.get()).id());
        ThemeService.apply(AtomChatConfig.get(), ThemeService.FROSTED);
        assertNull(AppearancePresetStore.selected(saved, AtomChatConfig.get()));
    }

    @Test
    void deleteRemovesOnlyThePresetAndLeavesTheAppliedAppearanceAlone() throws IOException {
        String id = store.saveCurrent(appearance(), wallpaper("preset.png")).preset().id();
        AtomChatConfig active = appearance();
        assertTrue(store.apply(id, active).ok());
        assertTrue(WallpaperStore.isSet());
        assertTrue(store.delete(id).ok());
        assertEquals(0xFF010203, active.accentColor);
        assertArrayEquals(PNG, Files.readAllBytes(WallpaperStore.current()));
        assertTrue(store.list().isEmpty());
    }

    @Test
    void corruptMissingAndPartialFilesAreIgnoredAndNeverApplied() throws IOException {
        String id = store.saveCurrent(appearance(), null).preset().id();
        Path manifest = tmp.resolve("appearance-presets").resolve(id).resolve("preset.json");
        Files.writeString(manifest, "{\"number\":2,\"appearance\":{\"accentColor\":1}}");
        assertTrue(store.refresh().isEmpty(), "out-of-band edits are read at the explicit refresh boundary");
        AtomChatConfig target = new AtomChatConfig();
        target.accentColor = 0xFFABCDEF;
        assertFalse(store.apply(id, target).ok());
        assertEquals(0xFFABCDEF, target.accentColor);

        Path broken = tmp.resolve("appearance-presets").resolve("broken");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("preset.json"), "{not json");
        assertEquals(0, new AppearancePresetStore(tmp.resolve("appearance-presets")).list().size());
    }

    @Test
    void presetRoundTripsTheNewRadiusLimit() {
        AtomChatConfig config = new AtomChatConfig();
        config.cornerRadius = 30F;
        var saved = store.saveCurrent(config, null);
        assertTrue(saved.ok());
        var target = new AtomChatConfig();
        assertTrue(store.apply(saved.preset().id(), target).ok());
        assertEquals(30F, target.cornerRadius);
    }

    @Test
    void savedPresetRestoresBothBlurStrengthsAfterReload() {
        var source = appearance();
        source.blurStrength = 1.25F;
        var target = new AtomChatConfig();
        target.blurStrength = 2.75F;
        var saved = store.saveCurrent(source, null);
        assertTrue(saved.ok(), saved.error());

        var reloaded = new AppearancePresetStore(tmp.resolve("appearance-presets"));
        assertTrue(reloaded.apply(saved.preset().id(), target).ok());
        assertEquals(1.25F, target.blurStrength);
        assertEquals(2.35F, target.cardBlurStrength);
    }

    @Test
    void legacyPresetWithoutPanelBlurStrengthKeepsCurrentStrength() throws IOException {
        var saved = store.saveCurrent(appearance(), null);
        assertTrue(saved.ok(), saved.error());
        Path manifest = tmp.resolve("appearance-presets").resolve(saved.preset().id()).resolve("preset.json");
        var json = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        json.getAsJsonObject("appearance").remove("blurStrength");
        Files.writeString(manifest, json.toString());

        var target = new AtomChatConfig();
        target.blurStrength = 1.75F;
        assertTrue(store.apply(saved.preset().id(), target).ok());
        assertEquals(1.75F, target.blurStrength);
        assertEquals(2.35F, target.cardBlurStrength);
    }

    @Test
    void nonFiniteOrOutOfRangeValuesAndPathTraversalAreRejected() {
        AtomChatConfig bad = new AtomChatConfig();
        bad.cornerRadius = Float.NaN;
        assertFalse(store.saveCurrent(bad, null).ok());
        bad.cornerRadius = 20F;
        bad.uiScale = 99F;
        assertFalse(store.saveCurrent(bad, null).ok());
        assertEquals("invalid_id", store.apply("../../etc", new AtomChatConfig()).error());
        assertEquals("invalid_id", store.delete("../x").error());
        assertTrue(store.list().isEmpty());
    }

    @Test
    void failedConfigWriteRestoresAppearanceAndWallpaper() throws IOException {
        Path replacement = tmp.resolve("replace.png");
        var replacementImage = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        assertTrue(javax.imageio.ImageIO.write(replacementImage, "png", replacement.toFile()));
        String id = store.saveCurrent(new AtomChatConfig(), replacement).preset().id();
        assertTrue(WallpaperStore.set(wallpaper("keep.png")));
        Path active = WallpaperStore.current();
        byte[] previousWallpaper = Files.readAllBytes(active);
        Files.writeString(tmp.resolve("config/atomchat/atomchat-client.json"), "invalid config");
        AtomChatConfig target = appearance();
        target.accentColor = 789;
        target.blurStrength = 1.2F;
        target.bubbleStyle = null;
        target.pageNavStyle = null;
        target.themeName = null;
        target.panelOutline = false;
        target.panelOutlineWidth = 3F;
        target.bubbleAppearanceId = "test:before";
        target.selectedAppearancePresetId = "";
        assertEquals("config_io", store.apply(id, target).error());
        assertEquals(789, target.accentColor);
        assertFalse(target.blurEnabled);
        assertEquals(1.2F, target.blurStrength);
        assertNull(target.bubbleStyle);
        assertNull(target.pageNavStyle);
        assertNull(target.themeName);
        assertFalse(target.panelOutline);
        assertEquals(3F, target.panelOutlineWidth);
        assertEquals("test:before", target.bubbleAppearanceId);
        assertEquals("", target.selectedAppearancePresetId);
        assertEquals("invalid config", Files.readString(tmp.resolve("config/atomchat/atomchat-client.json")));
        assertEquals(active, WallpaperStore.current());
        assertArrayEquals(previousWallpaper, Files.readAllBytes(active));
    }

    @Test
    void failedSaveLeavesNoHalfPreset() throws IOException {
        AtomChatConfig config = new AtomChatConfig();
        Files.write(tmp.resolve("too-large.png"), new byte[16 * 1024 * 1024 + 1]);
        assertFalse(store.saveCurrent(config, tmp.resolve("too-large.png")).ok());
        assertTrue(store.list().isEmpty());
    }

    @Test
    void corruptWallpaperIsRejectedBeforeSaveOrApply() throws IOException {
        Path fake = tmp.resolve("fake.png");
        Files.writeString(fake, "not an image");
        assertFalse(store.saveCurrent(appearance(), fake).ok());
        assertTrue(store.list().isEmpty());

        String id = store.saveCurrent(appearance(), wallpaper("original.png")).preset().id();
        assertTrue(WallpaperStore.set(wallpaper("active.png")));
        Path active = WallpaperStore.current();
        Files.writeString(manifest(id).resolveSibling("wallpaper.png"), "not an image");
        AtomChatConfig config = new AtomChatConfig();
        assertFalse(store.apply(id, config).ok());
        assertEquals(new AtomChatConfig().accentColor, config.accentColor);
        assertEquals(active, WallpaperStore.current());
        assertArrayEquals(PNG, Files.readAllBytes(active));
    }

    @Test
    void legacyManifestWithMissingHistoricalImageStillAppliesIndependently() throws IOException {
        String id = legacyPreset(".png");
        assertTrue(WallpaperStore.set(wallpaper("active.png")));
        AtomChatConfig target = new AtomChatConfig();
        assertTrue(store.apply(id, target).ok());
        assertEquals(appearance().accentColor, target.accentColor);
        assertArrayEquals(PNG, Files.readAllBytes(WallpaperStore.current()));
    }

    @Test
    void staticComparisonUsesBytesAndDoesNotReadTheDiskUntilRefresh() throws IOException {
        AtomChatConfig config = appearance();
        Path source = wallpaper("source.png");
        String id = store.saveCurrent(config, source).preset().id();
        assertNull(AppearancePresetStore.selected(store.list(), config));
        assertTrue(WallpaperStore.set(source));
        assertEquals(id, AppearancePresetStore.selected(store.list(), config).id());

        var replacement = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        assertTrue(javax.imageio.ImageIO.write(replacement, "png", WallpaperStore.current().toFile()));
        assertEquals(id, AppearancePresetStore.selected(store.list(), config).id());
        WallpaperStore.refresh();
        assertNull(AppearancePresetStore.selected(store.list(), config));
    }

    @Test
    void newExplicitSelectionAlsoComparesTheSavedAppearance() {
        AtomChatConfig config = appearance();
        String id = store.saveCurrent(config, null).preset().id();
        assertTrue(store.apply(id, config).ok());
        assertEquals(id, AppearancePresetStore.selected(store.list(), config).id());
        config.cardBlurStrength = 0.5F;
        assertNull(AppearancePresetStore.selected(store.list(), config));
    }

    @Test
    void failedStaticReplacementLeavesThePriorConfigAndFile() throws IOException {
        Path source = tmp.resolve("different.jpg");
        var replacement = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        assertTrue(javax.imageio.ImageIO.write(replacement, "jpeg", source.toFile()));
        String id = store.saveCurrent(appearance(), source).preset().id();
        assertTrue(WallpaperStore.set(wallpaper("active.png")));
        Path active = WallpaperStore.current();
        var target = new AtomChatConfig();
        assertTrue(AtomChatConfig.saveChecked(target));
        Path config = tmp.resolve("config/atomchat/atomchat-client.json");
        byte[] savedConfig = Files.readAllBytes(config);
        Files.createDirectory(active.resolveSibling("wallpaper.jpg"));
        assertFalse(store.apply(id, target).ok());
        assertEquals(new AtomChatConfig().accentColor, target.accentColor);
        assertArrayEquals(savedConfig, Files.readAllBytes(config));
        assertEquals(active, WallpaperStore.current());
        assertArrayEquals(PNG, Files.readAllBytes(active));
    }

    @Test
    void addOnChoicesRoundTripAndResolveAgainWhenTheDynamicAddOnReturns() throws IOException {
        String dynamicId = "preset_test:aurora";
        AppearanceUi.DynamicWallpaper wallpaper = () -> (canvas, frame) -> {};
        var part = AppearanceUi.Part.DYNAMIC_WALLPAPER;
        AppearanceUi.registerDynamicWallpaper(dynamicId, "preset_test.wallpaper", wallpaper);
        try {
            var source = appearance();
            source.bubbleAppearanceId = "preset_test:paper";
            source.panelOutlineAppearanceId = "another_test:cutout";
            source.avatarFrameAppearanceId = "preset_test:halo";
            source.dynamicWallpaperAppearanceId = dynamicId;
            var saved = store.saveCurrent(source, wallpaper("stale.png"));
            assertTrue(saved.ok(), saved.error());
            assertTrue(saved.preset().hasWallpaper());
            assertFalse(Files.exists(manifest(saved.preset().id()).resolveSibling("wallpaper.png")));
            assertTrue(AppearanceUi.unregister(part, dynamicId));

            assertTrue(WallpaperStore.set(wallpaper("active.png")));
            var target = AtomChatConfig.get();
            var reloaded = new AppearancePresetStore(tmp.resolve("appearance-presets"));
            assertTrue(reloaded.apply(saved.preset().id(), target).ok());
            assertNull(WallpaperStore.current());
            WallpaperStore.init(tmp.resolve("wallpaper"));
            AtomChatConfig.reload();
            target = AtomChatConfig.get();
            assertEquals(source.bubbleAppearanceId, target.bubbleAppearanceId);
            assertEquals(source.panelOutlineAppearanceId, target.panelOutlineAppearanceId);
            assertEquals(source.avatarFrameAppearanceId, target.avatarFrameAppearanceId);
            assertEquals(dynamicId, target.dynamicWallpaperAppearanceId);
            assertEquals(saved.preset().id(), AppearancePresetStore.selected(reloaded.list(), target).id());
            var selection = new AppearanceUi.Selection(target.bubbleAppearanceId, target.panelOutlineAppearanceId,
                    target.avatarFrameAppearanceId, target.dynamicWallpaperAppearanceId);
            var resolver = new AppearanceUi.Resolver();
            assertNull(resolver.resolve(selection).dynamicWallpaper());

            AppearanceUi.registerDynamicWallpaper(dynamicId, "preset_test.wallpaper", wallpaper);
            assertEquals(wallpaper, resolver.resolve(selection).dynamicWallpaper());
            target.dynamicWallpaperAppearanceId = "preset_test:other";
            assertNull(AppearancePresetStore.selected(reloaded.list(), target));
        } finally {
            AppearanceUi.unregister(part, dynamicId);
        }
    }

    @Test
    void missingComponentKeysPreserveChoicesWhileExplicitNullRestoresBuiltIns() throws IOException {
        String oldId = legacyPreset(null);
        var target = appearance();
        target.bubbleAppearanceId = "test:bubble";
        target.panelOutlineAppearanceId = "test:outline";
        target.avatarFrameAppearanceId = "test:avatar";
        target.dynamicWallpaperAppearanceId = "test:dynamic";
        assertTrue(store.apply(oldId, target).ok());
        assertEquals("test:bubble", target.bubbleAppearanceId);
        assertEquals("test:outline", target.panelOutlineAppearanceId);
        assertEquals("test:avatar", target.avatarFrameAppearanceId);
        assertEquals("test:dynamic", target.dynamicWallpaperAppearanceId);
        target.accentColor = 42; // Existing legacy selection IDs continue to identify the chosen card.
        assertEquals(oldId, AppearancePresetStore.selected(store.list(), target).id());

        String newId = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        assertTrue(store.apply(newId, target).ok());
        assertNull(target.bubbleAppearanceId);
        assertNull(target.panelOutlineAppearanceId);
        assertNull(target.avatarFrameAppearanceId);
        assertNull(target.dynamicWallpaperAppearanceId);
    }

    @Test
    void anInvalidAddOnIdCannotBeWrittenOrApplied() throws IOException {
        var source = appearance();
        source.bubbleAppearanceId = "../unsafe";
        assertFalse(store.saveCurrent(source, null).ok());
        assertTrue(store.list().isEmpty());

        String id = store.saveCurrent(appearance(), null).preset().id();
        var json = com.google.gson.JsonParser.parseString(Files.readString(manifest(id))).getAsJsonObject();
        json.getAsJsonObject("appearance").addProperty("avatarFrameAppearanceId", "../unsafe");
        Files.writeString(manifest(id), json.toString());
        var target = new AtomChatConfig();
        assertFalse(store.apply(id, target).ok());
        assertNull(target.avatarFrameAppearanceId);
    }

    @Test
    void newPresetRestoresStoredEffectsDuringPerformanceModeAndKeepsHalfScreenWidths() {
        var source = appearance();
        source.blurStrength = 1.15F;
        String id = store.saveCurrent(source, null).preset().id();
        var target = new AtomChatConfig();
        target.halfScreenMode = true;
        target.halfScreenWidth = 700F;
        target.halfScreenWidthCustomized = true;
        target.graphicsQuality = GraphicsQuality.PERFORMANCE;
        target.pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        assertTrue(store.apply(id, target).ok());
        assertEquals(480F, target.panelWidth);
        assertEquals(700F, target.activePanelWidth());
        assertFalse(target.animationEnabled);
        assertFalse(target.blurEnabled);
        assertEquals(2.35F, target.cardBlurStrength);
        assertEquals(1.15F, target.blurStrength);
        assertEquals(AtomChatConfig.PageNavStyle.SLIDE, target.pageNavStyle);
        assertEquals(0F, target.effectiveCardBlurStrength());
        assertEquals(0F, target.effectiveBlurStrength());
        assertEquals(id, AppearancePresetStore.selected(store.list(), target).id());
        target.animationEnabled = true;
        assertFalse(target.effectiveAnimationEnabled());
        assertNull(AppearancePresetStore.selected(store.list(), target));
    }

    @Test
    void legacyPresetKeepsEffectsThatPerformanceModePreviouslyProtected() throws IOException {
        String id = legacyPreset(null);
        var target = new AtomChatConfig();
        target.graphicsQuality = GraphicsQuality.PERFORMANCE;
        target.pageNavStyle = AtomChatConfig.PageNavStyle.ZOOM;
        assertTrue(store.apply(id, target).ok());
        assertTrue(target.animationEnabled);
        assertTrue(target.blurEnabled);
        assertEquals(2F, target.cardBlurStrength);
        assertEquals(AtomChatConfig.PageNavStyle.ZOOM, target.pageNavStyle);
    }

    private Path manifest(String id) {
        return tmp.resolve("appearance-presets").resolve(id).resolve("preset.json");
    }

    private String legacyPreset(String extension) throws IOException {
        String id = store.saveCurrent(appearance(), null).preset().id();
        Path manifest = manifest(id);
        var json = com.google.gson.JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        json.addProperty("schema", 1);
        json.addProperty("wallpaper", extension);
        var a = json.getAsJsonObject("appearance");
        for (String field : new String[]{"bubbleAppearanceId", "panelOutlineAppearanceId",
                "avatarFrameAppearanceId", "dynamicWallpaperAppearanceId"}) a.remove(field);
        Files.writeString(manifest, json.toString());
        store.refresh();
        return id;
    }

    @Test
    void unrelatedBackupFileLeavesTheExistingLegacyWallpaperUntouched() throws IOException {
        String id = legacyPreset(null);
        assertTrue(WallpaperStore.set(wallpaper("keep.png")));
        Path active = WallpaperStore.current();
        Path blocker = tmp.resolve("appearance-presets").resolve(".wallpaper-backup");
        Files.writeString(blocker, "not a directory");

        assertTrue(store.apply(id, new AtomChatConfig()).ok());
        assertTrue(WallpaperStore.isSet(), "legacy themes preserve the independent wallpaper");
        assertEquals(active, WallpaperStore.current());
        assertTrue(Files.exists(active));
    }
}
