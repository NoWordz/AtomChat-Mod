package com.atom.chat.ui.input;

/**
 * One slot in the ordered input-routing chain. Every method answers a single
 * input event; returning {@code true} consumes it, {@code false} passes the
 * event on to the next handler. Handlers are consulted in registration order,
 * so a modal (cropper, colour picker) registered first naturally owns every
 * event while it is open — and a new feature adds its own handler instead of
 * inserting a branch into the middle of an if-else chain (the mistake that
 * swallowed unrelated pages' clicks three times before this router existed).
 */
public interface InputHandler {
    /**
     * Hit test for an AtomChat click. It runs on press, in input order.
     * Return a blocked target when this surface owns the event without an action.
     */
    default ClickTarget clickTarget(double mouseX, double mouseY, int button) {
        return null;
    }

    /** GUI-coordinate mouse press for continuous interaction and native forwarding. */
    default boolean onClick(double mouseX, double mouseY, int button) {
        return false;
    }

    /** GUI-coordinate mouse drag. */
    default boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /** GUI-coordinate mouse release. */
    default boolean onRelease(double mouseX, double mouseY, int button) {
        return false;
    }

    /** GUI-coordinate mouse wheel. */
    default boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    /** Key press. */
    default boolean onKey(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** Typed character (IME-commit included). */
    default boolean onChar(char chr, int modifiers) {
        return false;
    }
}
