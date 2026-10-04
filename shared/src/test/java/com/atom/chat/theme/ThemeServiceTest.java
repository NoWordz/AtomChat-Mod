package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeServiceTest {

    @Test
    void cornerFactorMapsTheThreeStyles() {
        assertEquals(1.0F, ThemeService.cornerFactor("large"), 1e-6F);
        assertEquals(0.5F, ThemeService.cornerFactor("medium"), 1e-6F);
        assertEquals(0.35F, ThemeService.cornerFactor("small"), 1e-6F);
    }

    @Test
    void cornerFactorFallsBackToLargeOnGarbage() {
        assertEquals(1.0F, ThemeService.cornerFactor("tiny"), 1e-6F);
        assertEquals(1.0F, ThemeService.cornerFactor(null), 1e-6F);
        assertEquals(1.0F, ThemeService.cornerFactor(""), 1e-6F);
    }

    @Test
    void modernPresetIsOpaqueFlat() {
        AtomChatConfig config = new AtomChatConfig();
        ThemeService.apply(config, ThemeService.MODERN);
        assertEquals(1.0F, config.panelOpacity, 1e-6F);
        assertFalse(config.blurEnabled);
        assertTrue(config.panelOutline);
        assertEquals(1.0F, config.cardTint, 1e-6F);
        assertEquals(ThemeService.MODERN, config.themeName);
    }

    @Test
    void lookPresetsNeverTouchPersonalColours() {
        AtomChatConfig config = new AtomChatConfig();
        config.ownBubbleColor = 0xFF123456;
        config.otherBubbleColor = 0xFF654321;
        config.textPrimaryColor = 0xFFFF00FF;
        config.panelWidth = 500.0F;
        ThemeService.apply(config, ThemeService.MODERN);
        assertEquals(0xFF123456, config.ownBubbleColor);
        assertEquals(0xFF654321, config.otherBubbleColor);
        assertEquals(0xFFFF00FF, config.textPrimaryColor);
        assertEquals(500.0F, config.panelWidth, 1e-6F);
    }

    /** No preset of any kind moves the corner style — it is an independent
     *  knob (appearance-page segmented control), never a preset side effect. */
    @Test
    void noPresetEverTouchesCornerStyle() {
        for (String id : new String[]{ThemeService.MODERN, ThemeService.FROSTED,
                "minimal", "summer", "elegant", "raven", "hub", "vivid"}) {
            AtomChatConfig config = new AtomChatConfig();
            config.cornerStyle = "medium";
            ThemeService.apply(config, id);
            assertEquals("medium", config.cornerStyle, id);
        }
    }

    @Test
    void frostedPresetRestoresTheShippedDefaults() {
        AtomChatConfig config = new AtomChatConfig();
        // Drift every preset-managed knob away from the defaults first.
        config.panelOpacity = 1.0F;
        config.blurEnabled = false;
        config.panelOutline = false;
        config.themeName = ThemeService.MODERN;
        ThemeService.apply(config, ThemeService.FROSTED);
        assertEquals(0.93F, config.panelOpacity, 1e-6F);
        assertTrue(config.blurEnabled);
        assertTrue(config.panelOutline);
        // 0.235 ≈ white @ alpha 60 — the shipped frosted look on the new
        // "0% means invisible" card-tint axis.
        assertEquals(0.235F, config.cardTint, 1e-6F);
        assertEquals(ThemeService.FROSTED, config.themeName);
    }

    @Test
    void unknownThemeIdIsIgnored() {
        AtomChatConfig config = new AtomChatConfig();
        ThemeService.apply(config, "skypunk");
        assertEquals("", config.themeName);
        assertTrue(config.blurEnabled);
    }

    // ------------------------------------------------------------- colour presets

    @Test
    void sixColourPresetsShip() {
        assertEquals(6, ThemeService.presets().length);
        for (ThemeService.Preset preset : ThemeService.presets()) {
            assertEquals(preset, ThemeService.byId(preset.id()));
        }
        assertNull(ThemeService.byId(ThemeService.FROSTED));
        assertNull(ThemeService.byId(""));
        assertNull(ThemeService.byId("nope"));
    }

    /** Key fields of every colour preset, asserted literally (not via byId)
     *  so a typo in the table cannot test against itself. */
    @Test
    void eachColourPresetWritesItsSignatureFields() {
        AtomChatConfig c = new AtomChatConfig();
        ThemeService.apply(c, "minimal");
        assertEquals(0xFF3B82F6, c.accentColor);
        assertEquals(0xFFF7F8FA, c.panelBgColor);
        assertEquals(0xFF1A1D21, c.textPrimaryColor);
        assertEquals(0xFFFFFFFF, c.cardColor);

        c = new AtomChatConfig();
        ThemeService.apply(c, "summer");
        assertEquals(0xFF84CC16, c.accentColor);
        assertEquals(0xFFFAFCF3, c.panelBgColor);
        assertEquals(0xFF1F2937, c.textPrimaryColor);
        assertEquals(0xFF84CC16, c.ownBubbleColor);

        c = new AtomChatConfig();
        ThemeService.apply(c, "elegant");
        assertEquals(0xFFD97757, c.accentColor);
        assertEquals(0xFFF5F0E8, c.panelBgColor);
        assertEquals(0xFFE8DDD0, c.cardColor);
        assertEquals(0xFF3D3929, c.textPrimaryColor);

        c = new AtomChatConfig();
        ThemeService.apply(c, "raven");
        assertEquals(0xFFE3B341, c.accentColor);
        assertEquals(0xFF0D1117, c.panelBgColor);
        assertEquals(0xFF161B27, c.cardColor);
        assertEquals(0xFFE6EDF3, c.textPrimaryColor);

        c = new AtomChatConfig();
        ThemeService.apply(c, "hub");
        assertEquals(0xFFFF9000, c.accentColor);
        assertEquals(0xFF0F0F0F, c.panelBgColor);
        assertEquals(0xFF1A1A1A, c.cardColor);
        assertEquals(0xFFF5F5F5, c.textPrimaryColor);

        c = new AtomChatConfig();
        ThemeService.apply(c, "vivid");
        assertEquals(0xFFE85A8A, c.accentColor);
        assertEquals(0xFFFDF0F4, c.panelBgColor);
        assertEquals(0xFFFBE3EB, c.otherBubbleColor);
        assertEquals(0xFF3D2C35, c.textPrimaryColor);
    }

    /** Every colour preset must land visibly away from the shipped defaults on
     *  the four fields that define its identity, and always writes the full
     *  opaque look block (blur under a solid surface is invisible work). */
    @Test
    void colourPresetsDifferFromShippedDefaultsAndAreOpaque() {
        AtomChatConfig defaults = new AtomChatConfig();
        for (ThemeService.Preset preset : ThemeService.presets()) {
            AtomChatConfig c = new AtomChatConfig();
            ThemeService.apply(c, preset.id());
            assertEquals(ThemeService.byId(preset.id()).accent(), c.accentColor);
            assertNotEquals(defaults.accentColor, c.accentColor, preset.id());
            assertNotEquals(defaults.panelBgColor, c.panelBgColor, preset.id());
            assertNotEquals(defaults.textPrimaryColor, c.textPrimaryColor, preset.id());
            assertNotEquals(defaults.ownBubbleColor, c.ownBubbleColor, preset.id());
            assertEquals(1.0F, c.panelOpacity, 1e-6F, preset.id());
            assertFalse(c.blurEnabled, preset.id());
            assertTrue(c.panelOutline, preset.id());
            assertEquals(1.0F, c.cardTint, 1e-6F, preset.id());
            assertEquals(preset.id(), c.themeName);
        }
    }

    /** All twelve colour fields move on a colour preset — a preset that
     *  leaves a colour at its shipped default is a half re-skin.
     *  bubbleTextColor is exempt: minimal/vivid keep white body text in their
     *  own (now accent-coloured) bubbles, which coincides with the shipped
     *  default white — visually correct, byte-wise identical. cornerStyle is
     *  exempt by design: presets never touch it (independent knob). */
    @Test
    void colourPresetWritesAllTwelveColours() {
        AtomChatConfig defaults = new AtomChatConfig();
        for (ThemeService.Preset preset : ThemeService.presets()) {
            AtomChatConfig c = new AtomChatConfig();
            c.cornerStyle = "medium";
            ThemeService.apply(c, preset.id());
            assertNotEquals(defaults.secondaryCapsuleBg, c.secondaryCapsuleBg, preset.id());
            assertNotEquals(defaults.secondaryCapsuleText, c.secondaryCapsuleText, preset.id());
            assertNotEquals(defaults.textSecondaryColor, c.textSecondaryColor, preset.id());
            assertNotEquals(defaults.otherBubbleTextColor, c.otherBubbleTextColor, preset.id());
            assertNotEquals(defaults.panelOutlineColor, c.panelOutlineColor, preset.id());
            assertNotEquals(defaults.otherBubbleColor, c.otherBubbleColor, preset.id());
            assertEquals("medium", c.cornerStyle, preset.id());
            // cardColor: vivid/minimal keep white — exactly like the default,
            // and that is fine; the fields above prove the palette moved.
        }
    }

    /** The frosted tile is now the "Default" reset: after drifting away (a
     *  colour preset plus hand tuning), picking it writes every colour and
     *  look field back to the factory value — the values a hand edit could
     *  never return to. Compared against {@link AtomChatConfig#DEFAULT}, the
     *  single source of factory truth. */
    @Test
    void frostedAfterDriftRestoresEveryFactoryField() {
        AtomChatConfig c = new AtomChatConfig();
        ThemeService.apply(c, "minimal");
        // Hand drift on top of the preset: every resettable field off default.
        c.panelOpacity = 0.5F;
        c.cardTint = 0.9F;
        c.panelOutline = false;
        c.blurEnabled = false;
        c.accentColor = 0xFF112233;
        c.ownBubbleColor = 0xFF445566;
        ThemeService.apply(c, ThemeService.FROSTED);

        AtomChatConfig def = AtomChatConfig.DEFAULT;
        assertEquals(def.accentColor, c.accentColor);
        assertEquals(def.ownBubbleColor, c.ownBubbleColor);
        assertEquals(def.bubbleTextColor, c.bubbleTextColor);
        assertEquals(def.otherBubbleColor, c.otherBubbleColor);
        assertEquals(def.otherBubbleTextColor, c.otherBubbleTextColor);
        assertEquals(def.panelBgColor, c.panelBgColor);
        assertEquals(def.textPrimaryColor, c.textPrimaryColor);
        assertEquals(def.textSecondaryColor, c.textSecondaryColor);
        assertEquals(def.cardColor, c.cardColor);
        assertEquals(def.panelOutlineColor, c.panelOutlineColor);
        assertEquals(def.secondaryCapsuleBg, c.secondaryCapsuleBg);
        assertEquals(def.secondaryCapsuleText, c.secondaryCapsuleText);
        assertEquals(def.panelOpacity, c.panelOpacity, 1e-6F);
        assertEquals(def.cardTint, c.cardTint, 1e-6F);
        assertTrue(c.panelOutline);
        assertTrue(c.blurEnabled);
        assertEquals(ThemeService.FROSTED, c.themeName);
    }

    /** The independent knobs survive the factory reset: corner style and the
     *  non-look fields (size, scale) are personal, not part of the snapshot. */
    @Test
    void frostedResetKeepsIndependentKnobs() {
        AtomChatConfig c = new AtomChatConfig();
        c.cornerStyle = "small";
        c.panelWidth = 500.0F;
        c.uiScale = 1.25F;
        ThemeService.apply(c, ThemeService.FROSTED);
        assertEquals(500.0F, c.panelWidth, 1e-6F);
        assertEquals(1.25F, c.uiScale, 1e-6F);
    }

    /** The no-deadlock rule: after any preset, a hand edit is the last word
     *  because apply is a snapshot — nothing re-writes it behind the user. */
    @Test
    void handEditWinsAfterPreset() {
        AtomChatConfig c = new AtomChatConfig();
        ThemeService.apply(c, "elegant");
        c.accentColor = 0xFF112233;
        assertEquals(0xFF112233, c.accentColor);
        // Re-picking the preset is the user asking for the snapshot back.
        ThemeService.apply(c, "elegant");
        assertEquals(0xFFD97757, c.accentColor);
    }

    /** An old config file (themeName empty) survives a Gson round trip with
     *  every field byte-identical — loading must never migrate or mutate it. */
    @Test
    void legacyConfigRoundTripsUnchanged() {
        Gson gson = new Gson();
        AtomChatConfig legacy = new AtomChatConfig();
        String json = gson.toJson(legacy);
        AtomChatConfig loaded = gson.fromJson(json, AtomChatConfig.class);
        assertNotNull(loaded);
        assertEquals(legacy.accentColor, loaded.accentColor);
        assertEquals(legacy.ownBubbleColor, loaded.ownBubbleColor);
        assertEquals(legacy.otherBubbleColor, loaded.otherBubbleColor);
        assertEquals(legacy.bubbleTextColor, loaded.bubbleTextColor);
        assertEquals(legacy.otherBubbleTextColor, loaded.otherBubbleTextColor);
        assertEquals(legacy.panelBgColor, loaded.panelBgColor);
        assertEquals(legacy.textPrimaryColor, loaded.textPrimaryColor);
        assertEquals(legacy.textSecondaryColor, loaded.textSecondaryColor);
        assertEquals(legacy.cardColor, loaded.cardColor);
        assertEquals(legacy.panelOutlineColor, loaded.panelOutlineColor);
        assertEquals(legacy.secondaryCapsuleBg, loaded.secondaryCapsuleBg);
        assertEquals(legacy.secondaryCapsuleText, loaded.secondaryCapsuleText);
        assertEquals(legacy.cornerStyle, loaded.cornerStyle);
        assertEquals(legacy.panelOpacity, loaded.panelOpacity, 1e-6F);
        assertEquals(legacy.cardTint, loaded.cardTint, 1e-6F);
        assertEquals(legacy.themeName, loaded.themeName);
        assertEquals("", loaded.themeName);
    }
    /** Backing-shadow polarity: relative luminance of the panel background
     *  (alpha ignored) against the 0.5 threshold picks the shadow strength. */
    @Test
    void panelIsLightFollowsPanelBackgroundLuminance() {
        AtomChatConfig config = new AtomChatConfig();
        config.panelBgColor = 0xFFFFFFFF;
        assertTrue(ThemeService.panelIsLight(config), "pure white panel is light");
        config.panelBgColor = 0xFF16191F;
        assertFalse(ThemeService.panelIsLight(config), "shipped dark default is dark");
        config.panelBgColor = 0xEE16191F;
        assertFalse(ThemeService.panelIsLight(config), "alpha is ignored, only RGB counts");
        config.panelBgColor = 0xFF808080;
        assertFalse(ThemeService.panelIsLight(config),
                "mid grey linearises to ~0.216 luminance - dark side of the 0.5 threshold");
        config.panelBgColor = 0xFFCCCCCC;
        assertTrue(ThemeService.panelIsLight(config),
                "light grey linearises to ~0.604 luminance - light side of the 0.5 threshold");
    }
}
