package com.atom.chat.ui.input;

import com.atom.chat.ui.UiLayout;

/** Closes the shell on a valid click in the exposed world area. */
public final class OutsidePanelInput implements InputHandler {
    public interface Host {
        UiLayout.Rect panelRect();
        float toVirtualX(double guiX);
        float toVirtualY(double guiY);
        void requestClose();
        /** Vanilla completion popups may extend past the panel boundary. */
        default boolean floatingUiAt(double guiX, double guiY) { return false; }
        default boolean clickFloatingUi(double guiX, double guiY, int button) { return false; }
    }

    private final Host host;

    public OutsidePanelInput(Host host) { this.host = host; }

    @Override public ClickTarget clickTarget(double x, double y, int button) {
        return outside(x, y, button) && !host.floatingUiAt(x, y)
                ? ClickTarget.action(this, host::requestClose) : null;
    }

    @Override public boolean onClick(double x, double y, int button) {
        return outside(x, y, button) && host.floatingUiAt(x, y) && host.clickFloatingUi(x, y, button);
    }

    private boolean outside(double x, double y, int button) {
        return button == 0 && Double.isFinite(x) && Double.isFinite(y)
                && !host.panelRect().contains(host.toVirtualX(x), host.toVirtualY(y));
    }
}
