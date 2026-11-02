package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.wallpaper.WallpaperStore;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real config IO and preset boundaries; reflection lets the missing API fail before implementation. */
class HalfScreenConfigTest {
    @TempDir Path tmp;
    private final Map<Field, Object> previousState = new LinkedHashMap<>();

    @BeforeEach
    void installTemporaryPlatform() throws Exception {
        rememberStaticState(Platform.class, "provider");
        Field configInstance = rememberStaticState(AtomChatConfig.class, "instance");
        rememberStaticState(WallpaperStore.class, "dir");
        rememberStaticState(WallpaperStore.class, "current");
        configInstance.set(null, null);
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp; }
            public Path gameDir() { return tmp; }
            public boolean isModLoaded(String id) { return false; }
        });
        WallpaperStore.init(tmp.resolve("wallpaper"));
    }

    @AfterEach
    void restoreState() throws Exception {
        for (var entry : previousState.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    private Field rememberStaticState(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        previousState.put(field, field.get(null));
        return field;
    }

    private Path configFile() {
        return tmp.resolve("atomchat/atomchat-client.json");
    }

    private static Field field(String name) {
        return assertDoesNotThrow(() -> AtomChatConfig.class.getField(name),
                "missing public layout preference: " + name);
    }

    private static void mode(AtomChatConfig config, boolean enabled) throws Exception {
        field("halfScreenMode").setBoolean(config, enabled);
    }

    private static float activeWidth(AtomChatConfig config) {
        return assertDoesNotThrow(() -> (Float) AtomChatConfig.class
                .getMethod("activePanelWidth").invoke(config), "missing active width reader");
    }

    private static void setWidth(AtomChatConfig config, float width) {
        assertDoesNotThrow(() -> AtomChatConfig.class.getMethod("setActivePanelWidth", float.class)
                .invoke(config, width), "missing active width writer");
    }

    @Test
    void switchingKeepsEachWidthThroughRealSaveAndReload() throws Exception {
        AtomChatConfig config = new AtomChatConfig();
        setWidth(config, 430F);
        mode(config, true);
        assertEquals(600F, activeWidth(config));
        setWidth(config, 570F);
        assertEquals(430F, config.panelWidth);
        assertEquals(570F, field("halfScreenWidth").getFloat(config));
        assertTrue(AtomChatConfig.saveChecked(config));

        AtomChatConfig.reload();
        config = AtomChatConfig.get();
        assertTrue(field("halfScreenMode").getBoolean(config));
        assertEquals(570F, activeWidth(config));
        mode(config, false);
        assertEquals(430F, activeWidth(config));
        assertTrue(AtomChatConfig.saveChecked(config));

        AtomChatConfig.reload();
        config = AtomChatConfig.get();
        assertFalse(field("halfScreenMode").getBoolean(config));
        assertEquals(430F, activeWidth(config));
        mode(config, true);
        assertEquals(570F, activeWidth(config));
    }

    @Test
    void legacyConfigKeepsPhoneWidthAndSeedsHalfScreenDefaults() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(), "{\"panelWidth\": 510, \"pixelRadiusMigrated\": true}");
        AtomChatConfig.reload();
        AtomChatConfig config = AtomChatConfig.get();
        assertFalse(field("halfScreenMode").getBoolean(config));
        assertEquals(510F, activeWidth(config));
        mode(config, true);
        assertEquals(600F, activeWidth(config));
        var saved = JsonParser.parseString(Files.readString(configFile())).getAsJsonObject();
        assertFalse(saved.get("halfScreenMode").getAsBoolean());
        assertEquals(600F, saved.get("halfScreenWidth").getAsFloat());
    }

    /**
     * Live files carried the phone width in {@code halfScreenWidth} even when
     * half-screen was never customised, so switching modes drew the identical
     * panel. A legacy file (no marker key) whose half width equals the phone
     * width must therefore fall back to the documented 600 default.
     */
    @Test
    void legacyFileWithHalfWidthEqualToPhoneWidthReadsSixHundred() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(),
                "{\"panelWidth\": 480, \"halfScreenMode\": true,"
                        + " \"halfScreenWidth\": 480, \"pixelRadiusMigrated\": true}");
        AtomChatConfig.reload();
        AtomChatConfig config = AtomChatConfig.get();
        assertTrue(field("halfScreenMode").getBoolean(config));
        assertFalse(field("halfScreenWidthCustomized").getBoolean(config),
                "an unmarked equal width is the old defect, not a choice");
        assertEquals(600F, activeWidth(config),
                "half-screen must be the documented 600 until it is customised");
    }

    /**
     * A genuine legacy customisation survives the migration: the stored half
     * width differs from the phone width, so the marker is set and the value is
     * kept.
     */
    @Test
    void legacyFileWithADifferentHalfWidthKeepsIt() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(),
                "{\"panelWidth\": 480, \"halfScreenMode\": true,"
                        + " \"halfScreenWidth\": 560, \"pixelRadiusMigrated\": true}");
        AtomChatConfig.reload();
        AtomChatConfig config = AtomChatConfig.get();
        assertTrue(field("halfScreenWidthCustomized").getBoolean(config));
        assertEquals(560F, activeWidth(config));
    }

    /** A slider drag in half mode is the evidence that makes the width a choice. */
    @Test
    void writingTheWidthInHalfModeMarksItCustomised() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(),
                "{\"panelWidth\": 480, \"halfScreenMode\": true,"
                        + " \"halfScreenWidth\": 480, \"pixelRadiusMigrated\": true}");
        AtomChatConfig.reload();
        AtomChatConfig config = AtomChatConfig.get();
        assertEquals(600F, activeWidth(config));
        setWidth(config, 520F);
        assertTrue(field("halfScreenWidthCustomized").getBoolean(config));
        assertEquals(520F, activeWidth(config));
        // Phone mode is untouched by the half-screen write.
        mode(config, false);
        assertEquals(480F, activeWidth(config));
        mode(config, true);
        assertEquals(520F, activeWidth(config));
        assertTrue(AtomChatConfig.saveChecked(config));
        AtomChatConfig.reload();
        assertEquals(520F, activeWidth(AtomChatConfig.get()),
                "the marker persists with the width");
    }

    /** Writing the phone width must not leak into the half-screen choice. */
    @Test
    void phoneWidthWritesLeaveTheHalfScreenWidthAlone() throws Exception {
        Files.createDirectories(configFile().getParent());
        Files.writeString(configFile(),
                "{\"panelWidth\": 480, \"halfScreenMode\": false,"
                        + " \"halfScreenWidth\": 480, \"pixelRadiusMigrated\": true}");
        AtomChatConfig.reload();
        AtomChatConfig config = AtomChatConfig.get();
        assertEquals(480F, activeWidth(config));
        setWidth(config, 430F);
        assertFalse(field("halfScreenWidthCustomized").getBoolean(config),
                "a phone-mode drag says nothing about the half-screen layout");
        assertEquals(430F, activeWidth(config));
        mode(config, true);
        assertEquals(600F, activeWidth(config),
                "half mode still answers its own default");
    }

    @Test
    void missingConfigStartsInPhoneModeWithIndependentDefaultWidths() throws Exception {
        AtomChatConfig.reload();
        AtomChatConfig config = AtomChatConfig.get();
        assertFalse(field("halfScreenMode").getBoolean(config));
        assertEquals(480F, activeWidth(config));
        mode(config, true);
        assertEquals(600F, activeWidth(config));
    }

    @Test
    void invalidWidthsFallBackOnlyForTheCurrentMode() throws Exception {
        AtomChatConfig config = new AtomChatConfig();
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, 0F, -1F}) {
            config.panelWidth = invalid;
            field("halfScreenWidth").setFloat(config, 560F);
            // A hand-written half width counts as a choice (the marker is what
            // "customised" means; the load migration sets it for real files).
            field("halfScreenWidthCustomized").setBoolean(config, true);
            mode(config, false);
            assertEquals(480F, activeWidth(config));
            mode(config, true);
            assertEquals(560F, activeWidth(config));

            config.panelWidth = 420F;
            field("halfScreenWidth").setFloat(config, invalid);
            assertEquals(600F, activeWidth(config));
            mode(config, false);
            assertEquals(420F, activeWidth(config));
        }
        // The config reader validates, but does not impose the UI slider's range.
        setWidth(config, 350F);
        assertEquals(350F, activeWidth(config));
        mode(config, true);
        setWidth(config, 720F);
        assertEquals(720F, activeWidth(config));
        assertEquals(350F, config.panelWidth);
    }

    @Test
    void customPresetPreservesBothWidthsInHalfScreenAndKeepsPhoneModeBehavior() throws Exception {
        var store = new AppearancePresetStore(tmp.resolve("presets"));
        AtomChatConfig source = new AtomChatConfig();
        source.accentColor = 0xFF123456;
        source.panelWidth = 430F;
        mode(source, false);
        field("halfScreenWidth").setFloat(source, 600F);
        var saved = store.saveCurrent(source, null);
        assertTrue(saved.ok(), saved.error());
        var appearance = JsonParser.parseString(Files.readString(tmp.resolve("presets")
                .resolve(saved.preset().id()).resolve("preset.json")))
                .getAsJsonObject().getAsJsonObject("appearance");
        assertFalse(appearance.has("halfScreenMode"));
        assertFalse(appearance.has("halfScreenWidth"));

        AtomChatConfig target = new AtomChatConfig();
        target.panelWidth = 440F;
        mode(target, true);
        field("halfScreenWidth").setFloat(target, 550F);
        field("halfScreenWidthCustomized").setBoolean(target, true);
        var reloaded = new AppearancePresetStore(tmp.resolve("presets"));
        assertTrue(reloaded.apply(saved.preset().id(), target).ok());
        assertEquals(0xFF123456, target.accentColor);
        assertTrue(field("halfScreenMode").getBoolean(target));
        assertEquals(440F, target.panelWidth, "half-screen presets must preserve the remembered phone width");
        assertEquals(550F, field("halfScreenWidth").getFloat(target));
        assertEquals(550F, activeWidth(target));
        AtomChatConfig.reload();
        target = AtomChatConfig.get();
        assertTrue(field("halfScreenMode").getBoolean(target));
        assertEquals(440F, target.panelWidth);
        assertEquals(550F, field("halfScreenWidth").getFloat(target));
        assertEquals(550F, activeWidth(target));

        mode(target, false);
        assertEquals(440F, activeWidth(target));
        assertTrue(reloaded.apply(saved.preset().id(), target).ok());
        assertEquals(430F, activeWidth(target), "phone mode must still apply the preset's phone width");
        assertEquals(550F, field("halfScreenWidth").getFloat(target));
        AtomChatConfig.reload();
        target = AtomChatConfig.get();
        assertFalse(field("halfScreenMode").getBoolean(target));
        assertEquals(430F, target.panelWidth);
        assertEquals(550F, field("halfScreenWidth").getFloat(target));
        assertEquals(430F, activeWidth(target));
        mode(target, true);
        assertEquals(550F, activeWidth(target));
    }

    @Test
    void builtInThemesPreserveModeAndBothWidths() throws Exception {
        AtomChatConfig config = new AtomChatConfig();
        config.panelWidth = 440F;
        field("halfScreenWidth").setFloat(config, 580F);
        for (boolean halfScreen : new boolean[]{false, true}) {
            mode(config, halfScreen);
            for (var preset : ThemeService.presets()) {
                ThemeService.apply(config, preset.id());
                assertEquals(halfScreen, field("halfScreenMode").getBoolean(config));
                assertEquals(440F, config.panelWidth);
                assertEquals(580F, field("halfScreenWidth").getFloat(config));
            }
            ThemeService.apply(config, ThemeService.MODERN);
            ThemeService.apply(config, ThemeService.FROSTED);
            assertEquals(halfScreen, field("halfScreenMode").getBoolean(config));
            assertEquals(440F, config.panelWidth);
            assertEquals(580F, field("halfScreenWidth").getFloat(config));
        }
    }
}
