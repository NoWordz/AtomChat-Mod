package com.atom.chat.compat;

/** Tracks a raw primary press across covering popups, animation and screen resize. */
public final class ForeignChatPointer {
    private boolean primaryHeld;
    private boolean blocked;

    public boolean press(int button, boolean interactive, boolean covered) {
        boolean consumedByCover = !interactive || covered;
        if (button == 0) {
            primaryHeld = true;
            blocked = consumedByCover;
        }
        return consumedByCover;
    }

    public void release(int button) {
        if (button == 0) {
            primaryHeld = false;
            blocked = false;
        }
    }

    public void cancelForReinit() {
        if (primaryHeld) blocked = true;
    }

    public boolean hoverMasked(boolean covered) { return blocked || covered; }
}
