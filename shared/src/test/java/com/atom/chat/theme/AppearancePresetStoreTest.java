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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearancePresetStoreTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lK3Q6wAAAABJRU5ErkJggg==");

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
        Files.delete(tmp.resolve("source.png"));

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
        assertEquals(7.5F, target.cornerRadius, 0.0001F);
        assertEquals(512.0F, target.panelWidth, 0.0001F);
        assertEquals(700.0F, target.panelHeight, 0.0001F);
        assertEquals(1.2F, target.uiScale, 0.0001F);
        assertEquals(1.15F, target.contentScale, 0.0001F);
        assertFalse(target.animationEnabled);
        assertEquals(AtomChatConfig.PageNavStyle.SLIDE, target.pageNavStyle);
        assertEquals("custom", target.themeName);
        assertTrue(target.hideBlockedMessages, "privacy is outside the appearance whitelist");
        assertTrue(target.notificationSoundEnabled, "sound is outside the appearance whitelist");
        assertTrue(WallpaperStore.isSet());
        assertEquals(PNG.length, Files.size(WallpaperStore.current()));
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
    void applyingAPresetWithoutWallpaperClearsTheCurrentWallpaper() throws IOException {
        assertTrue(WallpaperStore.set(wallpaper("active.png")));
        String id = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        assertTrue(store.apply(id, new AtomChatConfig()).ok());
        assertFalse(WallpaperStore.isSet());
    }

    @Test
    void deleteRemovesOnlyThePresetAndLeavesTheAppliedAppearanceAlone() throws IOException {
        String id = store.saveCurrent(appearance(), wallpaper("preset.png")).preset().id();
        AtomChatConfig active = appearance();
        assertTrue(store.apply(id, active).ok());
        assertTrue(store.delete(id).ok());
        assertEquals(0xFF010203, active.accentColor);
        assertTrue(WallpaperStore.isSet(), "deleting the definition does not undo its application");
        assertTrue(store.list().isEmpty());
    }

    @Test
    void corruptMissingAndPartialFilesAreIgnoredAndNeverApplied() throws IOException {
        String id = store.saveCurrent(appearance(), null).preset().id();
        Path manifest = tmp.resolve("appearance-presets").resolve(id).resolve("preset.json");
        Files.writeString(manifest, "{\"number\":2,\"appearance\":{\"accentColor\":1}}");
        assertTrue(store.list().isEmpty());
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
        String id = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        assertTrue(WallpaperStore.set(wallpaper("keep.png")));
        Files.writeString(tmp.resolve("config"), "not a directory");
        AtomChatConfig target = appearance();
        target.accentColor = 789;
        assertFalse(store.apply(id, target).ok());
        assertEquals(789, target.accentColor);
        assertFalse(target.blurEnabled);
        assertTrue(WallpaperStore.isSet());
    }

    @Test
    void failedSaveLeavesNoHalfPreset() throws IOException {
        AtomChatConfig config = new AtomChatConfig();
        Files.write(tmp.resolve("too-large.png"), new byte[16 * 1024 * 1024 + 1]);
        assertFalse(store.saveCurrent(config, tmp.resolve("too-large.png")).ok());
        assertTrue(store.list().isEmpty());
    }

    @Test
    void corruptOrOversizedWallpaperHeadersAreRejectedBeforeTheyCanReplaceTheActiveImage()
            throws IOException {
        Path fake = tmp.resolve("fake.png");
        Files.writeString(fake, "not an image");
        assertFalse(store.saveCurrent(new AtomChatConfig(), fake).ok(),
                "an extension is not proof that the bytes are an image");
        assertTrue(store.list().isEmpty());

        String id = store.saveCurrent(new AtomChatConfig(), wallpaper("valid.png")).preset().id();
        Path presetWallpaper = tmp.resolve("appearance-presets").resolve(id).resolve("wallpaper.png");
        Files.writeString(presetWallpaper, "corrupt after save");
        assertFalse(store.apply(id, new AtomChatConfig()).ok(),
                "a damaged preset wallpaper is refused rather than applied as success");
    }

    @Test
    void backupFailureLeavesTheExistingWallpaperUntouched() throws IOException {
        String id = store.saveCurrent(new AtomChatConfig(), null).preset().id();
        assertTrue(WallpaperStore.set(wallpaper("keep.png")));
        Path active = WallpaperStore.current();
        Path blocker = tmp.resolve("appearance-presets").resolve(".wallpaper-backup");
        Files.writeString(blocker, "not a directory");

        assertFalse(store.apply(id, new AtomChatConfig()).ok());
        assertTrue(WallpaperStore.isSet(), "a failed backup must not clear the active wallpaper");
        assertEquals(active, WallpaperStore.current());
        assertTrue(Files.exists(active));
    }
}
