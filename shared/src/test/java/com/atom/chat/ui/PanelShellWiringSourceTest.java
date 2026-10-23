package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for the panel shell's wiring to its own machinery (D25).
 *
 * <p><b>Why a source probe.</b> {@code PanelOpenMotionTest} covers the arithmetic
 * by value, but it cannot see whether the shell ever <em>asks</em> for it — and
 * the shell is exactly where the two copies of the open transform used to live.
 * Nothing offline can see it: {@code AtomChatScreen} needs the Minecraft client
 * to load, and the golden scenes never composite the panel frame. Block D23a
 * shipped that class of defect (a second chain beside the first, the whole input
 * layer falling through) with every gate green.
 *
 * <p>So the wiring is asserted where it is written: no leftover copy of the
 * clocks, the transform or the probe in the screen; the blur capture rect and
 * the painted rect both come from the one derivation; the leak probe is called
 * through its class and handed over as a method reference rather than kept as a
 * second implementation.
 */
class PanelShellWiringSourceTest {

    private static final List<String> SCREENS = List.of(
            "platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
            "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java",
            "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java");

    /** Members that must have left the screen, with the call that must replace them. */
    private static final List<String> MOVED = List.of(
            "private static int leakProbeCount",
            "private static void probeCanvasLeak(",
            "private float panelOpenScale(",
            "private float panelOpenSlidePx(",
            "private boolean panelAnimating()",
            "panelProgress != (closing ? 0.0F : 1.0F)",
            "panelProgress < 0.06F",
            "Math.min(50.0F, (nowNanos - lastFrameNanos)",
            "Math.min(50L, Math.max(1L, nowMs - lastFrameMs))",
            "(progress - 1.0F) * s(24)",
            // The shape floor is part of the mapping that moved: a second copy of
            // 0.94 left behind in the screen is a second source of truth for it,
            // and the main-session review found exactly that (the constant stayed
            // behind unused after the mapping left, with its own javadoc rewritten
            // to point at PanelOpenMotion).
            "private static final float OPEN_MIN_SCALE");

    /** Every call the screen must make into the two new classes, and how often. */
    private static final Map<String, Integer> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("PanelOpenMotion.frameDtMs(nowNanos, lastFrameNanos)", 1);
        REQUIRED.put("PanelOpenMotion.domainFrameDtMs(nowMs, lastFrameMs)", 1);
        REQUIRED.put("PanelOpenMotion.stepProgress(panelProgress, closing, frameDtMs,", 1);
        REQUIRED.put("PanelOpenMotion.settled(panelProgress, closing)", 1);
        REQUIRED.put("PanelOpenMotion.closeDue(panelProgress, closing)", 1);
        REQUIRED.put("PanelOpenMotion.blurCapture(", 1);
        REQUIRED.put("PanelOpenMotion.openScale(progress, AtomChatConfig.get().pageNavStyle)", 1);
        REQUIRED.put("PanelOpenMotion.openSlidePx(progress, AtomChatConfig.get().pageNavStyle)", 1);
        // The fade is asked for twice: the pure-overdraw judgement and the layer's paint.
        REQUIRED.put("PanelOpenMotion.fade(progress)", 2);
        REQUIRED.put("PanelOpenMotion.fadeLayer(progress,", 1);
        // Input gate, foreign-widget input, and the deferred migration warning.
        REQUIRED.put("PanelOpenMotion.animating(panelProgress, closing)", 3);
        REQUIRED.put("CanvasLeakWatch.probe(\"drawPanel+banners\"", 2);
        REQUIRED.put("CanvasLeakWatch.probe(\"messageListView.draw\"", 1);
        REQUIRED.put("CanvasLeakWatch::probe,", 1);
    }

    // ---------------------------------------------------------------- helpers

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("platforms"))) {
                return p;
            }
        }
        throw new AssertionError("repo root (the directory holding platforms/) not found above " + dir);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(repoRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------ assertions

    /**
     * The screen keeps no copy of the clocks, the transform, the cut-off or the
     * probe. Every one of these was the only implementation of its rule; a
     * leftover is how two versions of it start to disagree (the blur capture rect
     * drifting off the panel is exactly that accident).
     */
    @Test
    void theScreenKeepsNoCopyOfItsOwnMachinery() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (String moved : MOVED) {
                assertEquals(0, occurrences(src, moved),
                        screen + ": '" + moved + "' is still in the screen. That rule now lives in"
                                + " PanelOpenMotion / CanvasLeakWatch, and a second copy is the"
                                + " defect this guard exists for.");
            }
        }
    }

    /** Every rule is asked for instead. */
    @Test
    void everyRuleIsAskedForThroughTheNewClasses() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            for (Map.Entry<String, Integer> call : REQUIRED.entrySet()) {
                assertEquals(call.getValue().intValue(), occurrences(src, call.getKey()),
                        screen + ": expected " + call.getValue() + " x " + call.getKey());
            }
        }
    }

    /**
     * The one-transform contract. Before this block the shell held the arithmetic
     * twice -- once in drawPhone, once in the capture rect -- and the two could
     * drift apart with nothing offline noticing. Now the screen names the
     * transform exactly once (drawPhone, which paints) and asks for the capture
     * rect through the one derivation, so "same mapping" is structural; the
     * identity itself is pinned by value in {@code PanelOpenMotionTest}.
     */
    @Test
    void theCaptureRectAndThePaintedRectShareTheOneTransform() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(1, occurrences(src, "PanelOpenMotion.openScale(progress, AtomChatConfig.get().pageNavStyle)"),
                    screen + ": the screen must name the open transform exactly once (drawPhone); a"
                            + " second copy is the drift this block removed");
            assertEquals(1, occurrences(src, "PanelOpenMotion.openSlidePx(progress, AtomChatConfig.get().pageNavStyle)"),
                    screen + ": same for the slide");
            assertEquals(1, occurrences(src, "PanelOpenMotion.PanelRect capture = PanelOpenMotion.blurCapture("),
                    screen + ": the capture rect must be the one derivation, not hand-rolled"
                            + " arithmetic beside it");
        }
    }

    /**
     * The probe is handed over, not reimplemented: the screen calls the class
     * twice by name and passes its method reference to the banner service, so the
     * occurrence series stays one series (a local counter would restart at zero
     * for the second caller and re-log the first leak).
     */
    @Test
    void theLeakProbeIsOneSeries() throws IOException {
        for (String screen : SCREENS) {
            String src = source(screen);
            assertEquals(3, occurrences(src, "CanvasLeakWatch.probe("),
                    screen + ": the panel draws, banner passes and message list must report through"
                            + " the one probe");
            assertTrue(src.contains("CanvasLeakWatch::probe,"),
                    screen + ": the banner service must be handed the same probe, not a second one");
            assertFalse(src.contains("AtomChatScreen::probeCanvasLeak"),
                    screen + ": the screen's own probe method is gone; the method reference must go"
                            + " with it");
        }
    }
}
