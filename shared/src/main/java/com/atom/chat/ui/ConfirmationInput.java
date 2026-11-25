package com.atom.chat.ui;

import com.atom.chat.ui.input.ClickTarget;
import com.atom.chat.ui.input.InputHandler;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/** A confirmation owns all input, including events over the world outside the panel. */
public final class ConfirmationInput implements InputHandler {
    private final Supplier<ConfirmationDialog> dialog;
    private final DoubleFunction<Float> x, y;
    public ConfirmationInput(Supplier<ConfirmationDialog> dialog, DoubleFunction<Float> x, DoubleFunction<Float> y) {
        this.dialog = dialog; this.x = x; this.y = y;
    }
    @Override public ClickTarget clickTarget(double mx, double my, int button) {
        return dialog.get().clickTarget(x.apply(mx), y.apply(my), button);
    }
    @Override public boolean onKey(int key, int scan, int modifiers) {
        if (!dialog.get().isActive()) return false;
        dialog.get().key(key); return true;
    }
    @Override public boolean onChar(char chr, int modifiers) { return dialog.get().isActive(); }
    @Override public boolean onScroll(double mx, double my, double h, double v) { return dialog.get().isActive(); }
    @Override public boolean onDrag(double mx, double my, int button, double dx, double dy) { return dialog.get().isActive(); }
    @Override public boolean onRelease(double mx, double my, int button) { return dialog.get().isActive(); }
}
