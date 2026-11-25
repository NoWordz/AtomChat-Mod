package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GraphicsQualityMigrationTest {
    @Test void legacyAndExplicitTiersRoundTripThroughTheClientConfig(@TempDir Path root) throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root; }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
        Path file = root.resolve("atomchat/atomchat-client.json");
        Files.createDirectories(file.getParent());
        assertCase(file, "", "ULTRA", false);
        assertCase(file, "\"performanceMode\":false,", "ULTRA", false);
        assertCase(file, "\"performanceMode\":true,", "PERFORMANCE", true);
        assertCase(file, "\"performanceMode\":true,\"graphicsQuality\":\"BALANCED\",", "BALANCED", false);
        assertCase(file, "\"performanceMode\":true,\"graphicsQuality\":\"unknown\",", "PERFORMANCE", true);
        assertCase(file, "\"graphicsQuality\":null,", "ULTRA", false);
    }

    private static void assertCase(Path file, String fields, String tier, boolean performance) throws Exception {
        Files.writeString(file, "{" + fields + """
                "blurEnabled":true,"blurStrength":1.25,"cardBlurStrength":2.75,"animationEnabled":true}
                """);
        AtomChatConfig.reload();
        assertTrue(AtomChatConfig.saveChecked(AtomChatConfig.get()));
        JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(saved.has("graphicsQuality"), "saved config must declare the migrated quality tier");
        assertEquals(tier, saved.get("graphicsQuality").getAsString());
        assertFalse(saved.has("performanceMode"), "the old key is consumed on migration");
        AtomChatConfig.reload();
        var reloaded = AtomChatConfig.get();
        assertEquals(!performance, reloaded.effectiveAnimationEnabled());
        assertEquals(!performance, reloaded.effectiveBlurEnabled());
        assertEquals(1.25F, reloaded.blurStrength);
        assertEquals(2.75F, reloaded.storedCardBlurStrength());
        assertEquals(performance ? 0F : 1.25F, reloaded.effectiveBlurStrength());
        assertEquals(performance ? 0F : 2.75F, reloaded.effectiveCardBlurStrength());
    }

    @Test void balancedHonorsTheSameBlurSettingsAsUltra() {
        var config = new AtomChatConfig();
        config.blurEnabled = true;
        assertEquals(2F, config.blurStrength);
        assertEquals(2F, config.cardBlurStrength);
        config.graphicsQuality = GraphicsQuality.BALANCED;
        assertTrue(config.effectiveBlurEnabled());
        assertEquals(2F, config.effectiveBlurStrength());
        assertEquals(2F, config.effectiveCardBlurStrength());
        config.blurStrength = 3.5F;
        config.cardBlurStrength = 3.5F;
        assertEquals(3F, config.effectiveBlurStrength());
        assertEquals(3F, config.effectiveCardBlurStrength());
        config.blurStrength = 0.65F;
        config.cardBlurStrength = 0F;
        assertEquals(0.65F, config.effectiveBlurStrength());
        assertEquals(0F, config.effectiveCardBlurStrength());
        config.graphicsQuality = GraphicsQuality.ULTRA;
        assertEquals(0.65F, config.effectiveBlurStrength());
        assertEquals(0F, config.effectiveCardBlurStrength());
    }
}
