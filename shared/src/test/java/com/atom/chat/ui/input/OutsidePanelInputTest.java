package com.atom.chat.ui.input;

import com.atom.chat.ui.UiLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutsidePanelInputTest {
    private static final class Host implements OutsidePanelInput.Host {
        int closes;
        boolean floating;
        public UiLayout.Rect panelRect() { return new UiLayout.Rect(30, 20, 600, 900); }
        public float toVirtualX(double x) { return (float) (x * 2); }
        public float toVirtualY(double y) { return (float) (y * 2); }
        public void requestClose() { closes++; }
        public boolean clickFloatingUi(double x, double y, int button) { return floating; }
    }

    @Test void leftClickInWorldClosesAndConsumesBeforeAnOpenModal() {
        Host host = new Host();
        int[] modalClicks = {0};
        InputGate gate = new InputGate(() -> false)
                .add(new OutsidePanelInput(host))
                .add(new InputHandler() {
                    public boolean onClick(double x, double y, int button) { modalClicks[0]++; return true; }
                });
        assertEquals(Boolean.TRUE, gate.click(400, 100, 0));
        assertEquals(1, host.closes); assertEquals(0, modalClicks[0]);
        assertEquals(Boolean.TRUE, gate.click(100, 100, 0));
        assertEquals(1, host.closes); assertEquals(1, modalClicks[0]);
    }

    @Test void boundsAndCoordinateConversionMatchTheVisiblePanel() {
        Host host = new Host(); var input = new OutsidePanelInput(host);
        assertFalse(input.onClick(15, 10, 0));
        assertFalse(input.onClick(315, 460, 0));
        assertTrue(input.onClick(14, 100, 0));
        assertTrue(input.onClick(100, 9, 0));
        assertTrue(input.onClick(100, 461, 0));
        assertEquals(3, host.closes);
    }

    @Test void otherButtonsDraggingAndAnimationCannotCloseThePanel() {
        Host host = new Host(); var input = new OutsidePanelInput(host);
        assertFalse(input.onClick(400, 100, 1));
        assertFalse(input.onDrag(400, 100, 0, 10, 10));
        assertFalse(input.onRelease(400, 100, 0));
        assertFalse(input.onClick(Double.NaN, 100, 0));
        assertEquals(Boolean.TRUE, new InputGate(() -> true).add(input).click(400, 100, 0));
        assertEquals(0, host.closes);
    }

    @Test void aVisibleSuggestionExtendingIntoTheWorldOwnsItsClick() {
        Host host = new Host(); host.floating = true;
        var input = new OutsidePanelInput(host);
        assertTrue(input.onClick(400, 100, 0));
        assertEquals(0, host.closes);
        host.floating = false;
        assertTrue(input.onClick(400, 100, 0));
        assertEquals(1, host.closes);
    }
}
