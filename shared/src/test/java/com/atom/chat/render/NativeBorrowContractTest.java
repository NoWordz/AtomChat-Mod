package com.atom.chat.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AC consumes AtomLib bridge through a synchronous borrow. These guards keep
 * the borrowed Skija context inside its guarded callback: no accessor may hand
 * the handle out, and the vanilla item render must sit between the two borrows
 * rather than inside one.
 */
class NativeBorrowContractTest {
    private static final List<String> TARGETS = List.of("layers/mapping/official", "platforms/1.21.1-fabric");

    @Test void theGpuFacadeNeverHandsTheBridgeContextOut() throws Exception {
        for (String target : TARGETS) {
            String s = read(target + "/src/main/java/com/atom/chat/render/SkiaGraphics.java");
            assertFalse(s.contains("requireContext"), target);
            assertFalse(s.contains("withContext(context -> context)"), target);
            assertTrue(s.contains("public <T> T withContext("), target);
            assertEquals(1, count(s, "MinecraftRenderBridge.get().withContext("), target);
        }
    }

    @Test void itemIconsRenderInTheVanillaPhaseAndImportInsideTheBorrowedPhase() throws Exception {
        for (String target : TARGETS) {
            String s = read(target + "/src/main/java/com/atom/chat/render/ItemIconRenderer.java");
            assertFalse(s.contains("requireContext"), target);
            assertFalse(s.contains("static DirectContext owner"), "a strong static context owner is the escape hatch: " + target);
            assertTrue(s.contains("private static WeakReference<DirectContext> owner"), target);
            int firstBorrow = s.indexOf("bridge.withContext(");
            int vanilla = s.indexOf("drawOffscreen(items, info, stack.copy())");
            int secondBorrow = s.indexOf("bridge.withContext(", firstBorrow + 1);
            int importCall = s.indexOf("icon.importInto(context, gl)");
            int textureImport = s.indexOf("Image.borrowTextureFrom");
            assertTrue(firstBorrow >= 0, target);
            assertTrue(firstBorrow < vanilla, "queued sampling must be submitted before the vanilla render: " + target);
            assertTrue(vanilla < secondBorrow, "the vanilla render must sit between the two borrowed phases: " + target);
            assertTrue(secondBorrow < importCall, "the Skija import must run inside its own borrow: " + target);
            assertTrue(importCall < textureImport, target);
            assertEquals(2, count(s, "bridge.withContext("), target);
        }
    }

    /**
     * The device-pixel snapper must be told the same density the canvas is
     * scaled by. The scale lives in {@link com.atom.chat.render.SkiaGraphics};
     * a screen dividing it by the GUI scale again (which this guard rejects)
     * quantises every snapped edge to a 2-4 device pixel grid — the exact
     * opposite of the crispness the snapping exists for.
     */
    @Test void theCanvasScaleIsTheOneTheSnapperIsTold() throws Exception {
        for (String target : TARGETS) {
            String s = read(target + "/src/main/java/com/atom/chat/render/SkiaGraphics.java");
            assertTrue(s.contains("canvas.scale(scale, scale)"), target);
            assertTrue(s.contains("UiPixelSnap.beginFrame(scale)"), target);
            assertTrue(s.indexOf("UiPixelSnap.beginFrame(scale)") < s.indexOf("bridge.vanillaPass(preUi)"),
                    "the density must be set before the pre-pass that snaps the capture rect: " + target);
        }
        for (String screen : List.of(
                "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java")) {
            String s = read(screen);
            assertFalse(s.contains("UiPixelSnap.beginFrame("),
                    "only SkiaGraphics owns the frame density; a screen writing it again can disagree: " + screen);
        }
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) found++;
        return found;
    }
    private static String read(String relative) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("platforms"))) root = root.getParent();
        return Files.readString(java.util.Objects.requireNonNull(root).resolve(relative));
    }
}

