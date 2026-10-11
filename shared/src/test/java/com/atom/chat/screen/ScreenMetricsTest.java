package com.atom.chat.screen;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry, frozen: every metric of {@link ScreenMetrics} under fixed window
 * sizes, at the panel frame's clamps and on the viewport boundary.
 *
 * <p>These numbers are not "looks about right" — each one was recorded from the
 * pre-migration arithmetic in {@code AtomChatScreen} (a throwaway probe that
 * transcribed the ten formulas verbatim and printed them for these exact
 * inputs), then frozen here. That is the point of the net: the panel frame is
 * read by every drawing and hit-testing domain, so a moved decimal here moves
 * every rect on screen, and nothing else in the suite would notice.
 *
 * <p>The cases cover what the clamps exist for: a 320x240 window (where the
 * "viewport minus 32" term is the binding one, not the configured panel size),
 * 1080p (where it binds for the height only), an ultrawide and a 4K window
 * (where the 1080p-anchored density makes vh land on 1080 again), plus the
 * content-zoom clamp at both ends and the UI-scale floor.
 *
 * <p>No Minecraft type is named here: the arithmetic takes the window
 * measurement, which is the whole reason it is reachable offline.
 */
class ScreenMetricsTest {
    /** The values the probe recorded for the default config (panel 480x860, uiScale 1, contentScale 1). */
    private static final float DEFAULT_CS = 1.0F;
    private static final float DEFAULT_UI = 1.0F;

    @TempDir
    static Path platformRoot;

    @BeforeAll
    static void installTemporaryPlatform() {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return platformRoot.resolve("config");
            }

            @Override
            public Path gameDir() {
                return platformRoot.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    /** The config is a process-wide singleton; every case puts back what it borrowed. */
    @AfterEach
    void restoreConfig() {
        AtomChatConfig.get().contentScale = DEFAULT_CS;
        AtomChatConfig.get().uiScale = DEFAULT_UI;
    }

    @Test
    void aSmallWindowClampsThePanelToTheViewportNotTheConfig() {
        // 320x240, guiScale 2: density is pinned at its 1.0 floor, so vw=320.
        assertEquals(1.0F, ScreenMetrics.uiDensity(240), 1e-4F);
        assertEquals(320.0F, ScreenMetrics.vw(320, 240), 1e-4F);
        assertEquals(240.0F, ScreenMetrics.vh(240), 1e-4F);
        assertEquals(288.0F, ScreenMetrics.panelWidth(320, 240), 1e-4F);
        assertEquals(208.0F, ScreenMetrics.panelHeight(240), 1e-4F);
        assertEquals(30.0F, ScreenMetrics.panelX(), 1e-4F);
        assertEquals(16.0F, ScreenMetrics.panelY(240), 1e-4F);
        assertEquals(200.0F, ScreenMetrics.toVirtualX(100.0, 2.0, 240), 1e-4F);
        assertEquals(200.0F, ScreenMetrics.toVirtualY(100.0, 2.0, 240), 1e-4F);

        // ... and it is the viewport term that binds, not the configured 600.
        assertEquals(ScreenMetrics.vw(320, 240) - 32.0F, ScreenMetrics.panelWidth(320, 240), 1e-4F);
        assertEquals(ScreenMetrics.vh(240) - 32.0F, ScreenMetrics.panelHeight(240), 1e-4F);
    }

    @Test
    void a1080pWindowBindsTheViewportTermForTheHeightOnly() {
        assertEquals(1.0F, ScreenMetrics.uiDensity(1080), 1e-4F);
        assertEquals(1920.0F, ScreenMetrics.vw(1920, 1080), 1e-4F);
        assertEquals(1080.0F, ScreenMetrics.vh(1080), 1e-4F);
        assertEquals(600.0F, ScreenMetrics.panelWidth(1920, 1080), 1e-4F);
        assertEquals(1048.0F, ScreenMetrics.panelHeight(1080), 1e-4F);
        assertEquals(16.0F, ScreenMetrics.panelY(1080), 1e-4F);

        assertEquals(600.0F, ScreenMetrics.panelWidth(1920, 1080), 1e-4F,
                "the configured panel (480 * 1.25) fits the 1080p viewport, so it binds");
        assertEquals(ScreenMetrics.vh(1080) - 32.0F, ScreenMetrics.panelHeight(1080), 1e-4F,
                "the 860-tall panel does not fit 1080, so the viewport term binds for the height");
    }

    @Test
    void anUltrawideWindowKeepsThe1080pVirtualHeight() {
        // 1440 / 1080 = 1.3333334 as a float: the density is not exactly 4/3.
        assertEquals(1.3333334F, ScreenMetrics.uiDensity(1440), 1e-4F);
        assertEquals(2580.0F, ScreenMetrics.vw(3440, 1440), 1e-4F);
        assertEquals(1080.0F, ScreenMetrics.vh(1440), 1e-4F);
        assertEquals(600.0F, ScreenMetrics.panelWidth(3440, 1440), 1e-4F);
        assertEquals(1048.0F, ScreenMetrics.panelHeight(1440), 1e-4F);
        assertEquals(16.0F, ScreenMetrics.panelY(1440), 1e-4F);
        assertEquals(150.0F, ScreenMetrics.toVirtualX(100.0, 2.0, 1440), 1e-4F);
    }

    @Test
    void a4kWindowWithAGuiScaleOfThreeKeepsTheSameVirtualSpace() {
        assertEquals(2.0F, ScreenMetrics.uiDensity(2160), 1e-4F);
        assertEquals(1920.0F, ScreenMetrics.vw(3840, 2160), 1e-4F);
        assertEquals(1080.0F, ScreenMetrics.vh(2160), 1e-4F);
        assertEquals(600.0F, ScreenMetrics.panelWidth(3840, 2160), 1e-4F);
        assertEquals(1048.0F, ScreenMetrics.panelHeight(2160), 1e-4F);
        assertEquals(150.0F, ScreenMetrics.toVirtualX(100.0, 3.0, 2160), 1e-4F);
        assertEquals(150.0F, ScreenMetrics.toVirtualY(100.0, 3.0, 2160), 1e-4F);
    }

    @Test
    void theContentZoomClampsAtBothEndsAndRescalesTheFrame() {
        AtomChatConfig.get().contentScale = 0.1F;
        assertEquals(0.8F, ScreenMetrics.contentScale(), 1e-4F, "below the slider span clamps up");
        assertEquals(0.8F, ScreenMetrics.uiDensity(1080), 1e-4F);
        assertEquals(2400.0F, ScreenMetrics.vw(1920, 1080), 1e-4F);
        assertEquals(1350.0F, ScreenMetrics.vh(1080), 1e-4F);
        assertEquals(750.0F, ScreenMetrics.panelWidth(1920, 1080), 1e-4F);
        assertEquals(1318.0F, ScreenMetrics.panelHeight(1080), 1e-4F);
        assertEquals(37.5F, ScreenMetrics.panelX(), 1e-4F);
        assertEquals(250.0F, ScreenMetrics.toVirtualX(100.0, 2.0, 1080), 1e-4F);

        AtomChatConfig.get().contentScale = 5.0F;
        assertEquals(1.5F, ScreenMetrics.contentScale(), 1e-4F, "above the slider span clamps down");
        assertEquals(1.5F, ScreenMetrics.uiDensity(1080), 1e-4F);
        assertEquals(1280.0F, ScreenMetrics.vw(1920, 1080), 1e-4F);
        assertEquals(720.0F, ScreenMetrics.vh(1080), 1e-4F);
        assertEquals(400.0F, ScreenMetrics.panelWidth(1920, 1080), 1e-4F);
        assertEquals(688.0F, ScreenMetrics.panelHeight(1080), 1e-4F);
        assertEquals(20.0F, ScreenMetrics.panelX(), 1e-4F);
        assertEquals(133.33333F, ScreenMetrics.toVirtualX(100.0, 2.0, 1080), 1e-4F);
    }

    @Test
    void theUiScaleHasAFloorOfAHalf() {
        AtomChatConfig.get().uiScale = 0.1F;
        assertEquals(0.5F, ScreenMetrics.uiDensity(1080), 1e-4F, "the UI scale cannot shrink past 0.5");
        assertEquals(3840.0F, ScreenMetrics.vw(1920, 1080), 1e-4F);
        assertEquals(2160.0F, ScreenMetrics.vh(1080), 1e-4F);
        assertEquals(600.0F, ScreenMetrics.panelWidth(1920, 1080), 1e-4F);
        assertEquals(1075.0F, ScreenMetrics.panelHeight(1080), 1e-4F,
                "at this density the configured panel fits, so the config term binds");
        assertEquals(542.5F, ScreenMetrics.panelY(1080), 1e-4F);
        assertEquals(400.0F, ScreenMetrics.toVirtualX(100.0, 2.0, 1080), 1e-4F);
    }

    @Test
    void theGuiScaleIsWhatTurnsGuiPixelsIntoPanelPixels() {
        // Same window, same pointer: only the GUI scale differs, and the panel
        // space result scales with it (density is scale-independent).
        float atTwo = ScreenMetrics.toVirtualX(100.0, 2.0, 1080);
        float atThree = ScreenMetrics.toVirtualX(100.0, 3.0, 1080);
        assertEquals(200.0F, atTwo, 1e-4F);
        assertEquals(300.0F, atThree, 1e-4F);
        assertTrue(atThree > atTwo, "a higher GUI scale means more panel pixels per GUI pixel");
    }
}
