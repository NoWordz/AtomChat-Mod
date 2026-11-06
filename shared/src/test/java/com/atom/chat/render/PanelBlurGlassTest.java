package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks all platform copies keep the unmodified RGB and mask alpha wiring. */
class PanelBlurGlassTest {

    private static final List<String> TARGETS = List.of("1.20.1-forge", "1.21.1-neoforge", "1.21.1-fabric");

    private static Path root() {
        // Shared tests mount into every platform's suite; the repo root is two
        // levels up from the platform project directory.
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !dir.resolve("shared").resolve("PLATFORM-SEAMS.md").toFile().exists()) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repo root not found from " + Path.of("").toAbsolutePath());
        }
        return dir;
    }

    private static String shaderDir(String target) {
        // The fabric resources live under minecraft's namespace; the other two
        // under atomchat's.
        return target.equals("1.21.1-fabric")
                ? "platforms/" + target + "/src/main/resources/assets/minecraft/shaders/core"
                : "platforms/" + target + "/src/main/resources/assets/atomchat/shaders/core";
    }

    @Test
    void roundedShaderRestoresColorAndKeepsMaskAlpha() throws Exception {
        for (String target : TARGETS) {
            String fsh = Files.readString(root().resolve(shaderDir(target) + "/atomchat_panel_blur.fsh"));
            String json = Files.readString(root().resolve(shaderDir(target) + "/atomchat_panel_blur.json"));
            String renderer = Files.readString(root().resolve("platforms/" + target + "/src/main/java/com/atom/chat/render/PanelBlurRenderer.java"));
            assertTrue(fsh.contains("texColor.rgb * vertexColor.rgb * ColorModulator.rgb"), target);
            assertTrue(fsh.contains("color.a = vertexColor.a * ColorModulator.a * shapeAlpha;"), target);
            assertFalse(fsh.contains("u_MaxLuma"), target);
            assertFalse(json.contains("u_MaxLuma"), target);
            assertFalse(renderer.contains("u_MaxLuma"), target);
            assertTrue(renderer.contains("KAWASE_PASSES = 5;"), target);
            assertTrue(renderer.contains("(2.0F + i * 1.5F) * strength"), target);
        }
    }
    @Test
    void legacyLumaFieldIsIgnoredWhenReadingConfig() {
        var config = new com.google.gson.Gson().fromJson("{\"glassMaxLuma\":true,\"blurEnabled\":false}", com.atom.chat.config.AtomChatConfig.class);
        assertFalse(config.blurEnabled);
    }

}
