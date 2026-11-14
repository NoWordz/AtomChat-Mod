package com.atom.chat.ui;

import com.atom.chat.ui.input.InputHandler;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/** Editor gestures use the screen's normal input gate and yield to modal dialogs. */
public final class PanelEditorInput implements InputHandler {
    private final Supplier<PanelEditor> editor;
    private final Consumer<Boolean> finish;
    private final DoubleFunction<Float> x, y;
    private final Supplier<Float> width, height;
    private final BooleanSupplier blocked;

    public PanelEditorInput(Supplier<PanelEditor> editor, Consumer<Boolean> finish,
                            DoubleFunction<Float> x, DoubleFunction<Float> y,
                            Supplier<Float> width, Supplier<Float> height, BooleanSupplier blocked) {
        this.editor = editor;
        this.finish = finish;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.blocked = blocked;
    }

    private PanelEditor active() { return blocked.getAsBoolean() ? null : editor.get(); }

    @Override public boolean onClick(double mouseX, double mouseY, int button) {
        var current = active();
        if (current == null) return false;
        var result = current.click(x.apply(mouseX), y.apply(mouseY), button, width.get(), height.get());
        if (result == PanelEditor.Result.PASS) return false;
        if (result == PanelEditor.Result.SAVE) {
            finish.accept(true);
        }
        return true;
    }

    @Override public boolean onDrag(double mouseX, double mouseY, int button, double dx, double dy) {
        var current = active();
        return current != null && button == 0 && current.drag(x.apply(mouseX), y.apply(mouseY), width.get(), height.get());
    }

    @Override public boolean onRelease(double mouseX, double mouseY, int button) {
        var current = active();
        return current != null && button == 0 && current.release(width.get(), height.get());
    }

    @Override public boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical) {
        var current = active();
        return current != null && current.overToolbar(x.apply(mouseX), y.apply(mouseY), width.get(), height.get());
    }

    @Override public boolean onKey(int key, int scan, int modifiers) {
        if (active() == null || key != 256) return false;
        finish.accept(false);
        return true;
    }
}
