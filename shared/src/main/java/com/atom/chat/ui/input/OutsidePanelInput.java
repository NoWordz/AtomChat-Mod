package com.atom.chat.ui.input;

import com.atom.chat.ui.UiLayout;

/** Closes the shell on a fresh left press in the exposed world area. */
public final class OutsidePanelInput implements InputHandler {
    public interface Host {
        UiLayout.Rect panelRect();
        float toVirtualX(double guiX);
        float toVirtualY(double guiY);
        void requestClose();
        /** Vanilla completion popups may extend past the panel boundary. */
        default boolean clickFloatingUi(double guiX, double guiY, int button) { return false; }
    }

    private final Host host;

    public OutsidePanelInput(Host host) { this.host = host; }

    @Override public boolean onClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) return false;
        if (host.panelRect().contains(host.toVirtualX(mouseX), host.toVirtualY(mouseY))) return false;
        if (host.clickFloatingUi(mouseX, mouseY, button)) return true;
        host.requestClose();
        return true;
    }
}
