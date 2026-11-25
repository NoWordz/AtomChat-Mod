package com.atom.chat.ui.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final ClickTarget CANCELLED = ClickTarget.blocked();

    private final List<InputHandler> handlers = new ArrayList<>();
    private final Map<Integer, ClickTarget> claims = new HashMap<>();

    /** Appends a handler; earlier registrations have higher priority. */
    public InputRouter add(InputHandler handler) {
        handlers.add(handler);
        return this;
    }

    public boolean click(double mouseX, double mouseY, int button) {
        clear(claims.remove(button));
        for (InputHandler handler : handlers) {
            ClickTarget target = handler.clickTarget(mouseX, mouseY, button);
            if (target != null) {
                claims.put(button, target);
                target.press().run();
                target.activate().run();
                return true;
            }
            if (handler.onClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean drag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (cancel(button)) return true;
        for (InputHandler handler : handlers) {
            if (handler.onDrag(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean release(double mouseX, double mouseY, int button) {
        ClickTarget claim = claims.remove(button);
        if (claim != null) {
            clear(claim);
            return true;
        }
        for (InputHandler handler : handlers) {
            if (handler.onRelease(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /** Drop feedback immediately, keeping ownership until the physical button releases. */
    public boolean cancel(int button) {
        ClickTarget claim = claims.get(button);
        if (claim == null) return false;
        claims.put(button, CANCELLED);
        clear(claim);
        return true;
    }

    public void discardRelease(int button) {
        clear(claims.remove(button));
    }

    private static void clear(ClickTarget claim) {
        if (claim != null) claim.clear().run();
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
