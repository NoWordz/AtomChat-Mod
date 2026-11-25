package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import com.atom.chat.render.Animator;
import com.atom.chat.render.BlurMask;
import com.atom.chat.render.CardBackdrop;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.settings.SettingsCatalog;
import com.atom.chat.settings.SettingsChipBar;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsTabDefaults;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.UiLayout;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceModeTest {
    @Test void tabSelectionAndAnActiveTweenLandImmediatelyInPerformanceMode(@TempDir Path root) throws Exception {
        installPlatform(root);
        AtomChatConfig.reload();
        var config = AtomChatConfig.get();
        config.animationEnabled = true;
        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.ULTRA;
        var tabs = new SettingsChipBar();
        var layout = UiLayout.ofDetail(0, 0, 420, 550);
        var indicatorField = SettingsChipBar.class.getDeclaredField("indicator");
        indicatorField.setAccessible(true);
        var indicator = (Animator) indicatorField.get(tabs);
        tabs.update(layout, 3, 0, -1, -1, -1, 0);
        tabs.update(layout, 3, 1, -1, -1, -1, 10);
        assertTrue(indicator.getValue() > 0 && indicator.getValue() < 1);
        assertFalse(Animations.caretVisible(500L));

        config.graphicsQuality = com.atom.chat.config.GraphicsQuality.PERFORMANCE;
        assertTrue(Animations.caretVisible(500L));
        tabs.update(layout, 3, 1, -1, -1, -1, 0);
        assertEquals(1F, indicator.getValue());
        assertTrue(indicator.isDone());
        tabs.update(layout, 3, 2, -1, -1, -1, 0);
        assertEquals(2F, indicator.getValue());
        assertTrue(indicator.isDone());
        var tween = new Animator(t -> t).animateTo(180F, 42F);
        assertEquals(42F, tween.getValue());
        assertTrue(tween.isDone());
    }

    @Test void runtimeOverrideSurvivesRestartAndProtectsStoredSettings(@TempDir Path root) throws Exception {
        installPlatform(root);
        Path file = root.resolve("atomchat/atomchat-client.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"blurEnabled":true,"blurStrength":1.25,"cardBlurStrength":2.75,
                 "animationEnabled":true,"messageEntryAnimation":true,"avatarPokeEnabled":true,
                 "pageNavStyle":"ZOOM","funEffectsEnabled":true,"experimentalEffectsEnabled":true}
                """);
        AtomChatConfig.reload();
        var config = AtomChatConfig.get();
        var debug = SettingsCatalog.items(SettingsSection.ABOUT).stream()
                .filter(item -> item.id().equals("debug")).findFirst().orElseThrow();
        assertFalse(config.performanceModeEnabled());
        config.graphicsQuality = GraphicsQuality.PERFORMANCE;
        assertTrue(AtomChatConfig.saveChecked(config));
        assertFalse(debug.value());
        AtomChatConfig.reload();
        config = AtomChatConfig.get();
        assertTrue(config.performanceModeEnabled());
        assertFalse(config.effectiveBlurEnabled());
        assertEquals(0F, config.effectiveBlurStrength());
        assertEquals(0F, config.effectiveCardBlurStrength());
        assertFalse(Animations.enabled());
        assertFalse(Animations.messageEntry());
        assertFalse(Animations.avatarPoke());
        assertNull(CardBackdrop.captureWorld(() -> fail("performance mode must skip the framebuffer capture")));
        assertFalse(PanelBlurRenderer.render(new Matrix4f(), 1, 2, 120, 80, 10, 1, 2));
        assertFalse(PanelBlurRenderer.renderReuse(new Matrix4f(), 1, 2, 120, 80, 10, 1, 2));
        assertFalse(PanelBlurRenderer.renderMasked(java.util.List.of(
                new BlurMask(new UiLayout.Rect(1, 2, 120, 80), 10, 1)), 2));
        assertEquals(2.75F, AppearancePresetStore.Snapshot.capture(config).cardBlurStrength());
        byte[] before = Files.readAllBytes(file);
        for (var row : SettingsCatalog.sliders(SettingsSection.APPEARANCE)) {
            if (!row.id().contains("blur")) continue;
            assertFalse(row.available());
            assertEquals("0%", row.displayValue());
            row.set(.5F);
            row.apply(.75F);
            row.persist();
            row.nudge(1);
        }
        for (var section : new SettingsSection[]{SettingsSection.APPEARANCE, SettingsSection.CHAT}) {
            for (var row : SettingsCatalog.items(section)) {
                if (!java.util.Set.of("blur", "motion", "entry", "poke", "fun_effects", "experimental_effects").contains(row.id())) continue;
                assertFalse(row.available(), row.id());
                assertFalse(row.value(), row.id());
                row.set(false);
            }
        }
        assertArrayEquals(before, Files.readAllBytes(file));
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "material", () -> true));
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "interaction", () -> true));
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.CHAT, "effects", () -> true));
        ThemeService.applyFactoryDefaults(config);
        AppearancePresetStore.Snapshot.capture(new AtomChatConfig()).applyTo(config);
        assertTrue(config.imageMessagesEnabled);
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.ABOUT, "", () -> true));
        assertTrue(config.performanceModeEnabled());
        assertFalse(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "theme", () -> false));
        assertTrue(config.performanceModeEnabled());
        assertTrue(SettingsTabDefaults.reset(config, SettingsSection.APPEARANCE, "theme", () -> true));
        assertFalse(config.performanceModeEnabled());
        assertTrue(config.effectiveBlurEnabled());
        assertEquals(1.25F, config.effectiveBlurStrength());
        assertEquals(2.75F, config.effectiveCardBlurStrength());
        assertTrue(Animations.messageEntry());
        assertTrue(Animations.avatarPoke());
        assertEquals(AtomChatConfig.PageNavStyle.ZOOM, config.pageNavStyle);
        assertTrue(config.funEffectsEnabled);
        assertTrue(config.experimentalEffectsEnabled);
    }

    private static void installPlatform(Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root; }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @org.junit.jupiter.api.AfterEach void resetMode() {
        AtomChatConfig.get().graphicsQuality = com.atom.chat.config.GraphicsQuality.ULTRA;
    }
}
