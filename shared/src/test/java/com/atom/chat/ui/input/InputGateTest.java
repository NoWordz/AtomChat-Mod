package com.atom.chat.ui.input;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate's contract (D23a). The routing mechanics themselves are covered by
 * {@link InputRouterTest}; what is pinned here is the one thing the gate adds:
 * while the panel animates, pointer input is swallowed and the keyboard is not.
 * That asymmetry is the screen's current behaviour, moved here unchanged, and
 * these tests exist so a future "let's make it symmetric" edit goes red.
 */
class InputGateTest {
    @Test void aReleaseDuringTransitionCancelsHeldStateWithoutCommittingActions() {
        int[] cancelled = {0}, committed = {0};
        InputGate gate = new InputGate(new InputGate.Host() {
            public boolean panelAnimating() { return true; }
            public void cancelPointer(int button) { cancelled[0]++; }
        }).add(new InputHandler() {
            public boolean onRelease(double x, double y, int button) { committed[0]++; return true; }
        });
        assertEquals(Boolean.TRUE, gate.release(0, 0, 0));
        assertEquals(1, cancelled[0]);
        assertEquals(0, committed[0]);
    }

    /** Records every question the gate asks and every handler it consults, in order. */
    private final List<String> seen = new ArrayList<>();

    private final class RecordingHost implements InputGate.Host {
        private final boolean animating;

        RecordingHost(boolean animating) {
            this.animating = animating;
        }

        @Override
        public boolean panelAnimating() {
            seen.add("animating?");
            return animating;
        }
    }

    private final class RecordingHandler implements InputHandler {
        private final String name;
        private final boolean consumes;

        RecordingHandler(String name, boolean consumes) {
            this.name = name;
            this.consumes = consumes;
        }

        private boolean record() {
            seen.add(name);
            return consumes;
        }

        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            return record();
        }

        @Override
        public boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return record();
        }

        @Override
        public boolean onRelease(double mouseX, double mouseY, int button) {
            return record();
        }

        @Override
        public boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            return record();
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            return record();
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            return record();
        }
    }

    /** A gate that swallows if it can, with one handler that would consume everything. */
    private InputGate swallowingGate(boolean animating) {
        return new InputGate(new RecordingHost(animating))
                .add(new RecordingHandler("greedy", true));
    }

    @Test
    void whileAnimatingAllFourPointerPathsAreSwallowedWithoutConsultingAnyHandler() {
        for (boolean animating : new boolean[] {true, false}) {
            List<String> expected = animating
                    ? List.of("animating?")
                    : List.of("animating?", "greedy");

            seen.clear();
            assertSame(Boolean.TRUE, swallowingGate(animating).click(1, 2, 0),
                    "click with animating=" + animating);
            assertEquals(expected, seen, "click order with animating=" + animating);

            seen.clear();
            assertSame(Boolean.TRUE, swallowingGate(animating).drag(1, 2, 0, 3, 4),
                    "drag with animating=" + animating);
            assertEquals(expected, seen, "drag order with animating=" + animating);

            seen.clear();
            assertSame(Boolean.TRUE, swallowingGate(animating).release(1, 2, 0),
                    "release with animating=" + animating);
            assertEquals(expected, seen, "release order with animating=" + animating);

            seen.clear();
            assertSame(Boolean.TRUE, swallowingGate(animating).scroll(1, 2, 0.0, 5.0),
                    "scroll with animating=" + animating);
            assertEquals(expected, seen, "scroll order with animating=" + animating);
        }
    }

    @Test
    void whileAnimatingTheKeyboardStillReachesTheHandlers() {
        seen.clear();
        InputGate gate = swallowingGate(true);

        assertSame(Boolean.TRUE, gate.key(65, 0, 0));
        assertSame(Boolean.TRUE, gate.charTyped('a', 0));
        // No "animating?" at all: the keyboard path never asks the gate question.
        assertEquals(List.of("greedy", "greedy"), seen);
    }

    @Test
    void superFallbackOnlyHappensWhenTheGateDidNotConsume() {
        // Swallowed: the gate never consults a handler...
        seen.clear();
        assertSame(Boolean.TRUE, swallowingGate(true).click(1, 2, 0));
        assertEquals(List.of("animating?"), seen);
        assertTrue(seen.contains("animating?"));
        assertFalse(seen.contains("greedy"), "a swallowed click must not reach a handler");

        // ...and when it does ask, that question comes before the handler, so the
        // caller's `if (direct != null) return direct;` runs before any super call.
        seen.clear();
        assertSame(Boolean.TRUE, swallowingGate(false).click(1, 2, 0));
        assertEquals(List.of("animating?", "greedy"), seen);
        assertEquals(0, seen.indexOf("animating?"));
        assertEquals(1, seen.indexOf("greedy"));
    }

    @Test
    void aPassingChainReportsUnconsumedWhichIsWhatLetsTheScreenFallBackToVanilla() {
        InputGate gate = new InputGate(new RecordingHost(false))
                .add(new RecordingHandler("first", false));
        assertNull(gate.click(1, 2, 0));
        assertNull(gate.key(65, 0, 0));
        assertNull(gate.charTyped('a', 0));

        // A swallowed event is never "unconsumed": the screen must not treat a
        // mid-animation click as a miss and forward it to vanilla.
        InputGate animating = swallowingGate(true);
        assertSame(Boolean.TRUE, animating.click(1, 2, 0));
        assertSame(Boolean.TRUE, animating.scroll(1, 2, 0.0, 1.0));
    }

    @Test
    void theChainIsConsultedInRegistrationOrder() {
        seen.clear();
        InputGate gate = new InputGate(new RecordingHost(false))
                .add(new RecordingHandler("first", false))
                .add(new RecordingHandler("second", true))
                .add(new RecordingHandler("third", true));
        assertSame(Boolean.TRUE, gate.click(1, 2, 0));
        assertEquals(List.of("animating?", "first", "second"), seen);
        assertEquals(1, seen.indexOf("first"));
        assertEquals(2, seen.indexOf("second"));
    }
}
