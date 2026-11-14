package com.atom.chat.ui.input;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered input-event router. Handlers answer events in registration order;
 * the first handler to consume an event wins and later handlers never see it.
 * The screen's own {@code super.*} fallbacks stay outside the router: when no
 * handler consumes an event the screen forwards it to vanilla itself.
 *
 * <p>Pure Java, no Minecraft types — the routing order semantics are covered
 * by unit tests.</p>
 */
public final class InputRouter {
    private final List<InputHandler> handlers = new ArrayList<>();

    /** Appends a handler; earlier registrations have higher priority. */
    public InputRouter add(InputHandler handler) {
        handlers.add(handler);
        return this;
    }

    public boolean click(double mouseX, double mouseY, int button) {
        for (InputHandler handler : handlers) {
            if (handler.onClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean drag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (InputHandler handler : handlers) {
            if (handler.onDrag(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean release(double mouseX, double mouseY, int button) {
        for (InputHandler handler : handlers) {
            if (handler.onRelease(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean scroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (InputHandler handler : handlers) {
            if (handler.onScroll(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    public boolean key(int keyCode, int scanCode, int modifiers) {
        for (InputHandler handler : handlers) {
            if (handler.onKey(keyCode, scanCode, modifiers)) {
                com.atom.chat.diagnostics.FaultTrace.input(false,handler.getClass().getName());
                return true;
            }
        }
        com.atom.chat.diagnostics.FaultTrace.input(false,"vanilla");
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        for (InputHandler handler : handlers) {
            if (handler.onChar(chr, modifiers)) {
                com.atom.chat.diagnostics.FaultTrace.input(true,handler.getClass().getName());
                return true;
            }
        }
        com.atom.chat.diagnostics.FaultTrace.input(true,"vanilla");
        return false;
    }
}
