package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The D25 contract for the canvas save-stack leak probe: its two gates and its
 * log throttle.
 *
 * <p>Both gates matter for opposite reasons. The leak gate keeps a balanced draw
 * from being reported at all; the debug gate keeps the probe free in a normal
 * client. And the throttle is the reason a real leak is readable: it fires once
 * per frame, so only the first occurrence and then every two-hundredth are
 * logged -- the shape a one-line cause needed to stop becoming a stack trace per
 * frame.
 */
class CanvasLeakWatchTest {

    private boolean debug;

    /**
     * The debug flag is read through {@code Platform}-backed config, so this class
     * installs its own temporary platform: a filtered run ({@code --tests}) must
     * pass on its own.
     */
    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @BeforeEach
    void setUp() {
        debug = AtomChatConfig.get().debug;
        AtomChatConfig.get().debug = true;
        CanvasLeakWatch.resetForTest();
    }

    @AfterEach
    void tearDown() {
        AtomChatConfig.get().debug = debug;
        CanvasLeakWatch.resetForTest();
    }

    /** A canvas holding one unbalanced save: the leak the probe is looking for. */
    private static int leakOne(Canvas canvas) {
        int before = canvas.getSaveCount();
        canvas.save();
        return before;
    }

    /** A balanced pair: the probe must say nothing about it. */
    @Test
    void aBalancedDrawIsNotReported() {
        try (Surface surface = Surface.makeRasterN32Premul(8, 8)) {
            Canvas canvas = surface.getCanvas();
            int before = canvas.getSaveCount();
            canvas.save();
            canvas.restore();

            CanvasLeakWatch.probe("balanced", before, canvas);

            assertEquals(0, CanvasLeakWatch.occurrences(), "a balanced draw is not a leak");
        }
    }

    /** The debug gate: with debug off, a real leak is not counted at all. */
    @Test
    void aLeakIsIgnoredWhenDebugIsOff() {
        AtomChatConfig.get().debug = false;
        try (Surface surface = Surface.makeRasterN32Premul(8, 8)) {
            Canvas canvas = surface.getCanvas();

            CanvasLeakWatch.probe("leaked", leakOne(canvas), canvas);

            assertEquals(0, CanvasLeakWatch.occurrences());
        }
    }

    /** A real leak while debug is on is counted, once per call. */
    @Test
    void aLeakIsCountedOncePerProbe() {
        try (Surface surface = Surface.makeRasterN32Premul(8, 8)) {
            Canvas canvas = surface.getCanvas();

            CanvasLeakWatch.probe("leaked", leakOne(canvas), canvas);
            assertEquals(1, CanvasLeakWatch.occurrences());

            CanvasLeakWatch.probe("leaked again", leakOne(canvas), canvas);
            assertEquals(2, CanvasLeakWatch.occurrences());
        }
    }

    /**
     * The throttle: the first occurrence and every two-hundredth are logged. A
     * leak that happens once must survive the throttle, and one that happens every
     * frame must not flood.
     */
    @Test
    void theLogThrottleKeepsTheFirstAndEveryTwoHundredth() {
        assertTrue(CanvasLeakWatch.shouldLog(1));
        assertFalse(CanvasLeakWatch.shouldLog(2));
        assertFalse(CanvasLeakWatch.shouldLog(199));
        assertTrue(CanvasLeakWatch.shouldLog(200));
        assertFalse(CanvasLeakWatch.shouldLog(201));
        assertTrue(CanvasLeakWatch.shouldLog(400));
        // The probe only ever asks with a count of one or more (it increments
        // before asking), so zero is not a case this rule has to answer.
    }
}
