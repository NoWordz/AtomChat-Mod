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
