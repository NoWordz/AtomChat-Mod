package com.atom.chat.ui.input;

/**
 * The panel's input gate (D23a): the handlers' ordered chain plus the one rule
 * that decides whether a pointer event reaches that chain at all.
 *
 * <p><b>While the open/close animation runs, pointer input is swallowed.</b>
 * {@link #scroll}, {@link #click}, {@link #drag} and {@link #release} return
 * {@link Boolean#TRUE} without consulting a single handler. The panel is drawn
 * through the open transform (zoom/slide) while hit tests run on the settled
 * virtual layout, so a click mid-animation would land somewhere other than where
 * it looks.</p>
 *
 * <p><b>The gate is deliberately asymmetric.</b> {@link #key} and
 * {@link #charTyped} are <em>not</em> gated: they route while the panel is
 * still animating. That is the behaviour this screen has always had, moved here
 * unchanged — it is the current contract, not an oversight, and it is not to be
 * "symmetrised" into a sixth gated entry point without a real-device check
 * first. This class is the single place that asymmetry lives, so that any such
 * change has exactly one address.</p>
 *
 * <p><b>What this class is not.</b> The routing mechanics (first consumer wins,
 * non-consumers fall through in registration order) live in {@link InputRouter}
 * and are already covered by {@code InputRouterTest}; the registration order
 * itself stays in the screen, because its entries are the screen's own handler
 * classes. This class adds the gate and holds the chain — it does not own the
 * ordering semantics.</p>
 *
 * <p>The screen keeps its own {@code super.*} fallbacks: a {@code null} answer
 * means "no handler consumed this", and only then does the screen forward the
 * event to vanilla.</p>
 */
public final class InputGate {
    /**
     * What the gate needs from the screen: whether the panel or page transition is
     * still in flight. Kept behind a name that describes the state rather than
     * the screen's own private spelling.
     */
    public interface Host {
        /** True while the panel or page animation has not settled yet. */
        boolean panelAnimating();
        /** Cancels held pointer state without committing a click or card choice. */
        default void cancelPointer(int button) { }
    }

    private final Host host;
    private final InputRouter router = new InputRouter();

    public InputGate(Host host) {
        this.host = host;
    }

    /**
     * Appends a handler; earlier registrations have higher priority. The screen
     * builds the chain in the same order it always had.
     */
    public InputGate add(InputHandler handler) {
        router.add(handler);
        return this;
    }

    /** {@code TRUE} while animating, else the first consumer's answer, else {@code null}. */
    public Boolean scroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (host.panelAnimating()) {
            return Boolean.TRUE;
        }
        return route(router.scroll(mouseX, mouseY, horizontalAmount, verticalAmount));
    }

    /** {@code TRUE} while animating, else the first consumer's answer, else {@code null}. */
    public Boolean click(double mouseX, double mouseY, int button) {
        if (host.panelAnimating()) {
            return Boolean.TRUE;
        }
        return route(router.click(mouseX, mouseY, button));
    }

    /** {@code TRUE} while animating, else the first consumer's answer, else {@code null}. */
    public Boolean drag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (host.panelAnimating()) {
            return Boolean.TRUE;
        }
        return route(router.drag(mouseX, mouseY, button, dragX, dragY));
    }

    /** {@code TRUE} while animating, else the first consumer's answer, else {@code null}. */
    public Boolean release(double mouseX, double mouseY, int button) {
        if (host.panelAnimating()) {
            host.cancelPointer(button);
            return Boolean.TRUE;
        }
        return route(router.release(mouseX, mouseY, button));
    }

    /**
     * Never gated: the keyboard is not swallowed while the panel animates (see
     * the class note). {@code TRUE} on consumption, {@code null} otherwise.
     */
    public Boolean key(int keyCode, int scanCode, int modifiers) {
        return route(router.key(keyCode, scanCode, modifiers));
    }

    /** Never gated, for the same reason as {@link #key}. */
    public Boolean charTyped(char chr, int modifiers) {
        return route(router.charTyped(chr, modifiers));
    }

    /** {@code true} becomes {@link Boolean#TRUE}; {@code false} becomes "not consumed". */
    private static Boolean route(boolean consumed) {
        return consumed ? Boolean.TRUE : null;
    }
}
