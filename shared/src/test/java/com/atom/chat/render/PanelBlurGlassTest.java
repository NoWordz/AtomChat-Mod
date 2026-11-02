package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring guards for the glass readability clamp on the panel blur's rounded
 * draw-back shader (the DangoMusic glass adoption, unit B).
 *
 * <p>What is pinned and why:</p>
 * <ul>
 *   <li><b>The clamp lives in the fragment shader, RGB only</b> — a bright
 *       backdrop (snow, desert noon) blurred to near-white drowns white panel
 *       text. Dango's glass fixes this by scaling RGB down to a luma ceiling
 *       ({@code col *= u_MaxLuma / luma}); alpha must stay untouched because
 *       the display path treats framebuffer alpha as color (any non-1 write
 *       reads back as a black block).</li>
 *   <li><b>The uniform defaults to 1.0 in the json</b> — 1.0 is an exact
 *       no-op for a blur texture already clamped to [0,1], so a code path that
 *       forgets to set the uniform (or a driver that keeps the json default)
 *       degrades to today's rendering, never to a black panel. GLSL
 *       initializes an unset uniform to 0, which would clamp everything to
 *       black — the json default is the fallback, not a formality.</li>
 *   <li><b>All three platform copies carry the same change</b> — the shader
 *       triplet (fsh/json) and the renderer are per-platform copies; a fix
 *       landed in one is invisible on the other two loaders.</li>
 *   <li><b>The Kawase pipeline stays untouched</b> — pass count, radius and
 *       the kawase fragment shader are out of scope; the clamp runs once, on
 *       the final draw-back, not per blur pass.</li>
 * </ul>
 */
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
    void roundedFragmentShaderClampsLumaOnRgbOnly() throws Exception {
        for (String target : TARGETS) {
            String fsh = Files.readString(root().resolve(shaderDir(target) + "/atomchat_panel_blur.fsh"));
            assertTrue(fsh.contains("uniform float u_MaxLuma;"), target);
            // The clamp itself: RGB scaled to the ceiling…
            assertTrue(fsh.contains("col *= u_MaxLuma / luma;"), target);
            // …and the alpha line must stay mask/fade-only: no u_MaxLuma reach.
            assertTrue(fsh.contains("color.a = vertexColor.a * ColorModulator.a * shapeAlpha;"), target);
            String alphaLine = fsh.substring(fsh.indexOf("color.a ="));
            assertFalse(alphaLine.contains("u_MaxLuma"), target);
        }
    }

    @Test
    void jsonDefaultsTheUniformToTheNoOpValue() throws Exception {
        for (String target : TARGETS) {
            String json = Files.readString(root().resolve(shaderDir(target) + "/atomchat_panel_blur.json"));
            assertTrue(json.contains("\"name\": \"u_MaxLuma\""), target);
            int entry = json.indexOf("\"name\": \"u_MaxLuma\"");
            String entryText = json.substring(entry, json.indexOf('}', entry));
            assertTrue(entryText.contains("1.0"), target + ": default must be the no-op 1.0, got " + entryText);
        }
    }

    @Test
    void rendererSetsTheUniformEveryFrameFromTheConfigGate() throws Exception {
        for (String target : TARGETS) {
            String renderer = Files.readString(root().resolve(
                    "platforms/" + target + "/src/main/java/com/atom/chat/render/PanelBlurRenderer.java"));
            // "Uniform(\"u_MaxLuma\")" matches both the official-name
            // safeGetUniform and the Yarn-name getUniform call.
            assertTrue(renderer.contains("Uniform(\"u_MaxLuma\")"), target);
            assertTrue(renderer.contains("glassMaxLuma ? GLASS_MAX_LUMA : 1.0F"), target);
        }
    }

    @Test
    void configGateExistsAndDefaultsOn() throws Exception {
        String config = Files.readString(root().resolve(
                "shared/src/main/java/com/atom/chat/config/AtomChatConfig.java"));
        assertTrue(config.contains("public boolean glassMaxLuma = true;"),
                "the readability clamp ships enabled; the switch is the escape hatch");
    }

    @Test
    void kawasePipelineStaysUntouched() throws Exception {
        for (String target : TARGETS) {
            String kawaseFsh = Files.readString(root().resolve(shaderDir(target) + "/atomchat_kawase_blur.fsh"));
            assertFalse(kawaseFsh.contains("u_MaxLuma"),
                    target + ": the clamp must not ride the blur passes");
            String renderer = Files.readString(root().resolve(
                    "platforms/" + target + "/src/main/java/com/atom/chat/render/PanelBlurRenderer.java"));
            assertTrue(renderer.contains("KAWASE_PASSES = 5;"), target);
        }
    }
}
