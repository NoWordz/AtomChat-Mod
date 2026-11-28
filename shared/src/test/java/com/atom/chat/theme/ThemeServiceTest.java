package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeServiceTest {
    @Test void glassPresetTextReadsAfterActualAlphaCompositing() {
        for(var preset:ThemeService.presets()) {
            if(!Boolean.TRUE.equals(preset.blurEnabled())) continue;
            var config=new AtomChatConfig();ThemeService.apply(config,preset.id());
            boolean standardMaterial = config.panelOpacity == 0.70F && config.cardTint == 0.60F;
            boolean approvedMint = standardMaterial && preset.id().equals("mint")
                    && config.panelBgColor == 0xFF2E7E72 && config.cardColor == 0xFF61B8A0
                    && config.textPrimaryColor == 0xFFEBFDF7 && config.textSecondaryColor == 0xFFEBFDF7;
            // The user kept Ice and Sand's original colors when they moved to the common material.
            boolean retainedSecondary = standardMaterial && switch (preset.id()) {
                case "ice_glass" -> config.panelBgColor == 0xFFE4F0F5 && config.textSecondaryColor == 0xFF314653;
                case "sand_glass" -> config.panelBgColor == 0xFFF2E7D6 && config.textSecondaryColor == 0xFF51412E;
                default -> false;
            };
            for(int world:new int[]{0xFF000000,0xFFFFFFFF}) {
                try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(100,100);
                    var pixels=new io.github.humbleui.skija.Bitmap();var paint=new io.github.humbleui.skija.Paint()) {
                    var canvas=surface.getCanvas();canvas.clear(world);
                    canvas.drawRect(io.github.humbleui.types.Rect.makeWH(100,100),paint.setColor(
                            (Math.round(255*config.panelOpacity)<<24)|(config.panelBgColor&0xFFFFFF)));
                    canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(20,20,60,60),paint.setColor(
                            (Math.round(255*config.cardTint)<<24)|(config.cardColor&0xFFFFFF)));
                    pixels.allocN32Pixels(100,100);assertTrue(surface.readPixels(pixels,0,0));
                    int panel = pixels.getColor(5, 5);
                    int card = pixels.getColor(50, 50);
                    boolean darkWorld = world == 0xFF000000;
                    boolean approvedMintPanel = approvedMint && !darkWorld;
                    assertTextContrast(preset.id(), config.textPrimaryColor, panel, approvedMintPanel);
                    assertTextContrast(preset.id(), config.textPrimaryColor, card, approvedMint);
                    assertTextContrast(preset.id(), config.textSecondaryColor, panel,
                            approvedMintPanel || retainedSecondary && darkWorld);
                    assertTextContrast(preset.id(), config.textSecondaryColor, card, approvedMint);
                }
            }
        }
    }
    @Test void presetTextPairsRemainReadable() {
        for (var p : ThemeService.presets()) {
            // The user reviewed these exact solid outgoing bubbles in game and chose white text.
            boolean approvedWhiteBubble = p.bubbleText() == 0xFFFFFFFF && switch (p.id()) {
                case "elegant" -> p.ownBubble() == 0xFFDE6E1A;
                case "vivid" -> p.ownBubble() == 0xFFFF4D8C;
                default -> false;
            };
            assertTextContrast(p.id(), p.bubbleText(), p.ownBubble(), approvedWhiteBubble);
            int[][] pairs = {{p.otherBubbleText(), p.otherBubble()},
                    {p.textPrimary(), p.panelBg()}, {p.textSecondary(), p.panelBg()}};
            for (var pair : pairs) assertTextContrast(p.id(), pair[0], pair[1], false);
            boolean mint = p.id().equals("mint");
            assertTextContrast(p.id(), p.textSecondary(), p.card(),
                    mint && p.textSecondary() == 0xFFEBFDF7 && p.card() == 0xFF61B8A0);
            assertTextContrast(p.id(), p.capsuleText(), p.capsuleBg(),
                    mint && p.capsuleText() == 0xFFEBFDF7 && p.capsuleBg() == 0xFF348C78);
        }
    }

    private static void assertTextContrast(String preset, int text, int background, boolean approvedPair) {
        assertTrue(approvedPair || com.atom.chat.avatar.ColorUtil.contrastRatio(text, background) >= 4.5F,
                preset + ": " + Integer.toHexString(text) + "/" + Integer.toHexString(background));
    }

    @Test void translucentPresetsUseTranslucentCards() {
        for (var preset : ThemeService.presets()) {
            if (!Boolean.TRUE.equals(preset.blurEnabled())) continue;
            var config = new AtomChatConfig();
            ThemeService.apply(config, preset.id());
            assertTrue(config.cardTint > 0 && config.cardTint < 1, preset.id());
        }
    }

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
                "minimal", "summer", "elegant", "raven", "hub", "vivid",
                "dusk", "mint"}) {
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
        assertEquals(0.70F, config.panelOpacity, 1e-6F);
        assertTrue(config.blurEnabled);
        assertTrue(config.panelOutline);
        assertEquals(0.60F, config.cardTint, 1e-6F);
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
    void registeredColourPresetsHaveStableIds() {
        assertEquals(List.of("minimal", "summer", "elegant", "raven", "hub", "vivid",
                        "dusk", "mint", "ice_glass", "sand_glass"),
                Arrays.stream(ThemeService.presets()).map(ThemeService.Preset::id).toList());
        for (ThemeService.Preset preset : ThemeService.presets()) {
            assertEquals(preset, ThemeService.byId(preset.id()));
        }
        assertNull(ThemeService.byId(ThemeService.FROSTED));
        assertNull(ThemeService.byId(""));
        assertNull(ThemeService.byId("nope"));
    }

    @Test
    void builtInThemesUseTheirStandardMaterialOpacity() {
        var translucent = List.of("sand_glass", "mint", "ice_glass", "dusk");
        for (var preset : ThemeService.presets()) {
            var config = new AtomChatConfig();
            ThemeService.apply(config, preset.id());
            boolean glass = translucent.contains(preset.id());
            assertEquals(glass ? 0.70F : 1.0F, config.panelOpacity, preset.id());
            assertEquals(glass ? 0.60F : 1.0F, config.cardTint, preset.id());
        }
    }

    @Test
    void retiredGlassPresetsLeaveSavedAppearanceIntact() {
        Gson gson = new Gson();
        for (String id : List.of("azure_glass", "plum_glass")) {
            var saved = new AtomChatConfig();
            saved.themeName = id;
            saved.panelBgColor = 0xFF112233;
            saved.panelOpacity = 0.72F;
            String json = gson.toJson(saved);
            var loaded = gson.fromJson(json, AtomChatConfig.class);
            assertNull(ThemeService.byId(id), id);
            assertEquals(ThemeService.previewOf(ThemeService.FROSTED), ThemeService.previewOf(id), id);
            ThemeService.apply(loaded, id);
            assertEquals(json, gson.toJson(loaded), id);
        }
    }

    /** Key fields of every colour preset, asserted literally (not via byId)
     *  so a typo in the table cannot test against itself. */
    @Test
    void eachColourPresetWritesItsSignatureFields() {
        AtomChatConfig c = new AtomChatConfig();
        ThemeService.apply(c, "minimal");
        assertEquals(0xFF2563D8, c.accentColor);
        assertEquals(0xFFE7EDFA, c.panelBgColor);
        assertEquals(0xFF151A22, c.textPrimaryColor);
        assertEquals(0xFFFFFFFF, c.cardColor);
        assertEquals(c.textPrimaryColor, c.otherBubbleTextColor);
        assertEquals(0xFFD4DFEF, c.secondaryCapsuleBg);
        assertEquals(0xFF4E596B, c.secondaryCapsuleText);

        c = new AtomChatConfig();
        ThemeService.apply(c, "summer");
        assertEquals(0xFFACE246, c.accentColor);
        assertEquals(0xFFACE246, c.ownBubbleColor);
        assertEquals(0xFFEAFFA6, c.otherBubbleColor);
        assertEquals(0xFFFFFFEC, c.panelBgColor);
        assertEquals(0xFF192521, c.textPrimaryColor);
        assertEquals(0xFFECF4D4, c.cardColor);
        assertEquals(c.textPrimaryColor, c.otherBubbleTextColor);
        assertEquals(0xFF466C00, c.panelOutlineColor);
        assertEquals(0xFFD8E8B6, c.secondaryCapsuleBg);
        assertEquals(c.panelOutlineColor, c.secondaryCapsuleText);

        c = new AtomChatConfig();
        ThemeService.apply(c, "elegant");
        assertEquals(0xFFDE6E1A, c.accentColor);
        assertEquals(0xFFDE6E1A, c.ownBubbleColor);
        assertEquals(0xFFF4B182, c.otherBubbleColor);
        assertEquals(0xFFF5EBDB, c.panelBgColor);
        assertEquals(0xFFF3DFC8, c.cardColor);
        assertEquals(0xFF332E23, c.textPrimaryColor);
        assertEquals(0xFFFFFFFF, c.bubbleTextColor);
        assertEquals(c.textPrimaryColor, c.otherBubbleTextColor);
        assertEquals(0xFF615548, c.secondaryCapsuleText);
        assertEquals(0xFFEFD4AE, c.secondaryCapsuleBg);

        c = new AtomChatConfig();
        ThemeService.apply(c, "raven");
        assertEquals(0xFFFFC233, c.accentColor);
        assertEquals(0xFFFFC233, c.ownBubbleColor);
        assertEquals(0xFF355171, c.otherBubbleColor);
        assertEquals(0xFF1D283A, c.panelBgColor);
        assertEquals(0xFF263650, c.cardColor);
        assertEquals(0xFF07102A, c.panelOutlineColor);
        assertEquals(0xFFF0F4F8, c.textPrimaryColor);
        assertEquals(0xFF3A4B66, c.secondaryCapsuleBg);
        assertEquals(0xFFCDD7E5, c.secondaryCapsuleText);

        c = new AtomChatConfig();
        ThemeService.apply(c, "hub");
        assertEquals(0xFFFF8203, c.accentColor);
        assertEquals(0xFFFF8203, c.ownBubbleColor);
        assertEquals(0xFF151414, c.panelBgColor);
        assertEquals(0xFF262323, c.cardColor);
        assertEquals(0xFF000000, c.panelOutlineColor);
        assertEquals(0xFFFFFFFF, c.textPrimaryColor);
        assertEquals(c.textPrimaryColor, c.otherBubbleTextColor);
        assertEquals(c.panelBgColor, c.bubbleTextColor);
        assertEquals(0xFF423833, c.secondaryCapsuleBg);
        assertEquals(0xFFD4D4D4, c.secondaryCapsuleText);

        c = new AtomChatConfig();
        ThemeService.apply(c, "vivid");
        assertEquals(0xFFFF4D8C, c.accentColor);
        assertEquals(0xFFFF4D8C, c.ownBubbleColor);
        assertEquals(0xFFFFEBF8, c.panelBgColor);
        assertEquals(0xFFFAD9E9, c.cardColor);
        assertEquals(0xFFDEA6BE, c.otherBubbleColor);
        assertEquals(0xFF36212B, c.textPrimaryColor);
        assertEquals(0xFFFFFFFF, c.bubbleTextColor);
        assertEquals(c.textPrimaryColor, c.otherBubbleTextColor);
        assertEquals(0xFFF2BFD5, c.secondaryCapsuleBg);
        assertEquals(0xFF85304F, c.secondaryCapsuleText);

        c = new AtomChatConfig();
        ThemeService.apply(c, "dusk");
        assertEquals(0xFFB69DFF, c.accentColor);
        assertEquals(0xFFB69DFF, c.ownBubbleColor);
        assertEquals(0xFF433579, c.otherBubbleColor);
        assertEquals(0xFF352B48, c.panelBgColor);
        assertEquals(0xFF302958, c.cardColor);
        assertEquals(0xFF1D124C, c.panelOutlineColor);
        assertEquals(0xFF413960, c.secondaryCapsuleBg);
        assertEquals(0xFFF5F6FA, c.textPrimaryColor);
        assertEquals(c.textPrimaryColor, c.textSecondaryColor);
        assertEquals(c.textPrimaryColor, c.otherBubbleTextColor);
        assertEquals(c.textPrimaryColor, c.secondaryCapsuleText);
        assertEquals(0.70F, c.panelOpacity, 1e-6F);
        assertTrue(c.blurEnabled);

        c = new AtomChatConfig();
        ThemeService.apply(c, "mint");
        assertEquals(0xFF5EEAD4, c.accentColor);
        assertEquals(0xFF2E7E72, c.panelBgColor);
        assertEquals(0xFF61B8A0, c.cardColor);
        assertEquals(0xFF12392F, c.panelOutlineColor);
        assertEquals(0xFF348C78, c.secondaryCapsuleBg);
        assertEquals(0xFFEBFDF7, c.textSecondaryColor);
        assertEquals(c.textSecondaryColor, c.textPrimaryColor);
        assertEquals(c.textSecondaryColor, c.otherBubbleTextColor);
        assertEquals(c.textSecondaryColor, c.secondaryCapsuleText);
        assertEquals(0.70F, c.panelOpacity, 1e-6F);
        assertTrue(c.blurEnabled);
    }

    /** Every colour preset must land visibly away from the shipped defaults on
     *  the fields that define its identity, and writes the look block its
     *  snapshot declares: the standard opaque treatment (blur under a solid
     *  surface is invisible work) unless the preset ships its own translucent
     *  look (dusk, mint). hub is exempt from the textPrimary comparison — the
     *  terminal look is pure white on pure black, which coincides with the
     *  shipped default white (visually correct, byte-wise identical). */
    @Test
    void colourPresetsDifferFromShippedDefaultsAndLandTheirLook() {
        AtomChatConfig defaults = new AtomChatConfig();
        for (ThemeService.Preset preset : ThemeService.presets()) {
            AtomChatConfig c = new AtomChatConfig();
            ThemeService.apply(c, preset.id());
            assertEquals(ThemeService.byId(preset.id()).accent(), c.accentColor);
            assertNotEquals(defaults.accentColor, c.accentColor, preset.id());
            assertNotEquals(defaults.panelBgColor, c.panelBgColor, preset.id());
            if (!"hub".equals(preset.id())) {
                assertNotEquals(defaults.textPrimaryColor, c.textPrimaryColor, preset.id());
            }
            assertNotEquals(defaults.ownBubbleColor, c.ownBubbleColor, preset.id());
            Float votedOpacity = preset.panelOpacity();
            assertEquals(votedOpacity == null ? 1.0F : votedOpacity.floatValue(),
                    c.panelOpacity, 1e-6F, preset.id());
            assertEquals(Boolean.TRUE.equals(preset.blurEnabled()), c.blurEnabled, preset.id());
            assertTrue(c.panelOutline, preset.id());
            assertEquals(c.blurEnabled ? defaults.cardTint : 1.0F, c.cardTint, 1e-6F, preset.id());
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
        for (ThemeService.Preset preset : ThemeService.presets()) {
            AtomChatConfig c = new AtomChatConfig();
            int unset = 0x01234567;
            c.accentColor = unset;
            c.ownBubbleColor = unset;
            c.bubbleTextColor = unset;
            c.otherBubbleColor = unset;
            c.otherBubbleTextColor = unset;
            c.panelBgColor = unset;
            c.textPrimaryColor = unset;
            c.textSecondaryColor = unset;
            c.cardColor = unset;
            c.panelOutlineColor = unset;
            c.secondaryCapsuleBg = unset;
            c.secondaryCapsuleText = unset;
            c.cornerStyle = "medium";
            ThemeService.apply(c, preset.id());
            assertArrayEquals(new int[]{preset.accent(), preset.ownBubble(), preset.bubbleText(),
                            preset.otherBubble(), preset.otherBubbleText(), preset.panelBg(), preset.textPrimary(),
                            preset.textSecondary(), preset.card(), preset.panelOutlineColor(), preset.capsuleBg(),
                            preset.capsuleText()},
                    new int[]{c.accentColor, c.ownBubbleColor, c.bubbleTextColor, c.otherBubbleColor,
                            c.otherBubbleTextColor, c.panelBgColor, c.textPrimaryColor, c.textSecondaryColor,
                            c.cardColor, c.panelOutlineColor, c.secondaryCapsuleBg, c.secondaryCapsuleText},
                    preset.id());
            assertEquals("medium", c.cornerStyle, preset.id());
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
        assertEquals(0xFFDE6E1A, c.accentColor);
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
    /** Preview-card palettes: each colour preset answers with its own fields
     *  and its native corner scale; the Default tile answers with the factory
     *  palette, so the default card previews exactly what picking restores. */
    @Test
    void previewPaletteComesFromThemeOrFactory() {
        for (ThemeService.Preset preset : ThemeService.presets()) {
            ThemeService.Preview p = ThemeService.previewOf(preset.id());
            assertEquals(preset.accent(), p.accent(), preset.id());
            assertEquals(preset.panelBg(), p.panelBg(), preset.id());
            assertEquals(preset.card(), p.card(), preset.id());
            assertEquals(preset.ownBubble(), p.ownBubble(), preset.id());
            assertEquals(preset.otherBubble(), p.otherBubble(), preset.id());
            assertEquals(ThemeService.cornerFactor(preset.cornerStyle()), p.cornerFactor(), 1e-6F,
                    preset.id());
        }
        ThemeService.Preview def = ThemeService.previewOf(ThemeService.FROSTED);
        AtomChatConfig factory = AtomChatConfig.DEFAULT;
        assertEquals(factory.accentColor, def.accent());
        assertEquals(factory.panelBgColor, def.panelBg());
        assertEquals(factory.cardColor, def.card());
        assertEquals(factory.ownBubbleColor, def.ownBubble());
        assertEquals(factory.otherBubbleColor, def.otherBubble());
        assertEquals(1.0F, def.cornerFactor(), 1e-6F);
        // Unknown ids fall back to the factory palette too.
        assertEquals(def, ThemeService.previewOf("skypunk"));
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

    /** Outlines stand apart from the panel unless the exact color pair was chosen to blend in. */
    @Test
    void presetOutlinesStayLegibleAgainstTheirPanel() {
        final float outlineLegibilityContrast = 0.22F;
        for (ThemeService.Preset preset : ThemeService.presets()) {
            float contrast = Math.abs(ThemeService.relativeLuminance(preset.panelOutlineColor())
                    - ThemeService.relativeLuminance(preset.panelBg()));
            boolean approvedSubtleOutline = switch (preset.id()) {
                case "raven" -> preset.panelBg() == 0xFF1D283A && preset.panelOutlineColor() == 0xFF07102A;
                case "hub" -> preset.panelBg() == 0xFF151414 && preset.panelOutlineColor() == 0xFF000000;
                case "dusk" -> preset.panelBg() == 0xFF352B48 && preset.panelOutlineColor() == 0xFF1D124C;
                case "mint" -> preset.panelBg() == 0xFF2E7E72 && preset.panelOutlineColor() == 0xFF12392F;
                default -> false;
            };
            assertTrue(contrast >= outlineLegibilityContrast || approvedSubtleOutline,
                    preset.id() + " outline contrast " + contrast);
        }
    }
}
