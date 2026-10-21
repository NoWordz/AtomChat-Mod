package com.atom.chat.settings;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.wallpaper.WallpaperStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual catalog bindings and the rows exposed by Appearance -> Adjust. */
class HalfScreenSettingsTest {
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
        AtomChatConfig.reload();
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

    private static SettingsItem modeSwitch() {
        return SettingsCatalog.items(SettingsSection.APPEARANCE).stream()
                .filter(item -> item.id().equals("half_screen"))
                .findFirst().orElseThrow(() -> new AssertionError("missing half-screen switch"));
    }

    private static SettingsSlider widthSlider() {
        return SettingsCatalog.sliders(SettingsSection.APPEARANCE).stream()
                .filter(slider -> slider.id().equals("width")).findFirst().orElseThrow();
    }

    @Test
    void widthSliderReadsAndWritesCurrentModeAndPersistsEachWidth() {
        SettingsSlider width = widthSlider();
        assertEquals(480F, width.value());
        width.set(440F);
        SettingsItem mode = modeSwitch();
        assertFalse(mode.value());
        mode.set(true);
        assertTrue(mode.value());
        assertEquals(600F, width.value());
        width.set(550F);
        assertEquals(440F, AtomChatConfig.get().panelWidth);
        AtomChatConfig.reload();
        assertTrue(mode.value());
        assertEquals(550F, width.value());

        mode.set(false);
        assertEquals(440F, width.value());
        width.set(420F);
        AtomChatConfig.reload();
        assertFalse(mode.value());
        assertEquals(420F, width.value());
        mode.set(true);
        assertEquals(550F, width.value());
        width.set(1000F);
        assertEquals(600F, width.value());
        width.set(200F);
        assertEquals(400F, width.value());
        mode.set(false);
        assertEquals(420F, width.value());
    }

    @Test
    void sliderReadsHalfScreenWidthEvenWhenSwitchedOutsideSettings() throws Exception {
        SettingsSlider width = widthSlider();
        AtomChatConfig config = AtomChatConfig.get();
        config.panelWidth = 410F;
        var mode = assertDoesNotThrow(() -> AtomChatConfig.class.getField("halfScreenMode"));
        var halfWidth = assertDoesNotThrow(() -> AtomChatConfig.class.getField("halfScreenWidth"));
        halfWidth.setFloat(config, 580F);
        mode.setBoolean(config, true);
        assertEquals(580F, width.value());
        width.apply(530F);
        assertEquals(530F, halfWidth.getFloat(config));
        assertEquals(410F, config.panelWidth);
        mode.setBoolean(config, false);
        assertEquals(410F, width.value());
    }

    @Test
    void adjustLayoutGroupExposesTheWorkingSwitchBeforeWidthSlider() {
        SettingsSectionPage page = new SettingsSectionPage();
        var chips = page.chips(SettingsSection.APPEARANCE);
        int adjust = -1;
        for (int i = 0; i < chips.size(); i++) {
            if (chips.get(i).id().equals("adjust")) adjust = i;
        }
        assertTrue(adjust >= 0);
        page.selectChip(SettingsSection.APPEARANCE, adjust);
        var rows = page.rows(SettingsSection.APPEARANCE);
        int layout = -1;
        int mode = -1;
        int width = -1;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            if ("atomchat.settings.group.appearance.layout".equals(row.labelKey())) layout = i;
            if (row.kind() == SettingsSectionPage.RowKind.SWITCH
                    && row.item().id().equals("half_screen")) mode = i;
            if (row.kind() == SettingsSectionPage.RowKind.SLIDER
                    && row.slider().id().equals("width")) width = i;
        }
        assertTrue(layout >= 0 && mode > layout && width > mode,
                "layout group must expose half-screen mode before its width slider");
        assertSame(modeSwitch(), rows.get(mode).item());
        rows.get(mode).item().set(true);
        rows.get(width).slider().set(560F);
        AtomChatConfig.reload();
        assertTrue(modeSwitch().value());
        assertEquals(560F, widthSlider().value());
        modeSwitch().set(false);
        assertEquals(480F, widthSlider().value());
    }
}
