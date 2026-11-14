package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.input.InputGate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelEditorInputTest {
    @Test void editorYieldsToModalsAndNormalContentAndUsesTheAnimationGate() {
        var config = new AtomChatConfig();
        var editor = new PanelEditor(config, key -> key, 100, 100, 300, 250, 900, 700);
        var modal = new AtomicBoolean(false);
        var animating = new AtomicBoolean(false);
        var input = new PanelEditorInput(() -> editor, save -> fail("unexpected finish"),
                value -> (float) value, value -> (float) value, () -> 900F, () -> 700F, modal::get);
        var gate = new InputGate(animating::get).add(input);
        assertFalse(input.onClick(200, 200, 0), "real panel content receives clicks");
        modal.set(true);
        assertFalse(input.onClick(250, 100, 0), "an open modal owns the handle area too");
        assertFalse(input.onKey(256, 0, 0), "Escape belongs to the open modal");
        modal.set(false);
        animating.set(true);
        assertTrue(gate.click(250, 100, 0));
        animating.set(false);
        assertFalse(input.onDrag(350, 150, 0, 100, 50), "gated press cannot start editing");
        assertTrue(gate.click(250, 100, 0));
        assertFalse(input.onRelease(250, 100, 1), "right release cannot end a left drag");
        assertTrue(input.onDrag(350, 150, 0, 100, 50));
        assertTrue(input.onRelease(350, 150, 0));
    }

    @Test void editorOutsideClickCannotReachThePanelCloseHandler() {
        var config = new AtomChatConfig();
        var editor = new PanelEditor(config, key -> key, 100, 100, 300, 250, 900, 700);
        var closed = new AtomicBoolean();
        var input = new PanelEditorInput(() -> editor, save -> fail("unexpected finish"),
                value -> (float) value, value -> (float) value, () -> 900F, () -> 700F, () -> false);
        var gate = new InputGate(() -> false).add(input).add(new com.atom.chat.ui.input.InputHandler() {
            @Override public boolean onClick(double x, double y, int button) { closed.set(true); return true; }
        });
        assertTrue(gate.click(850, 500, 0));
        assertFalse(closed.get(), "editing keeps the real panel open when the world is clicked");
    }

    @Test void theMiddleOfTheRightEdgeResizesOnlyTheWidth() {
        var config = new AtomChatConfig();
        var editor = new PanelEditor(config, key -> key, 100, 100, 300, 250, 900, 700);
        float width = config.panelWidth, height = config.panelHeight;
        assertEquals(PanelEditor.Result.CONSUMED, editor.click(400, 225, 0, 900, 700));
        assertTrue(editor.drag(450, 225, 900, 700));
        assertTrue(editor.release(900, 700));
        assertTrue(config.panelWidth > width);
        assertEquals(height, config.panelHeight, .001F);
    }
}
