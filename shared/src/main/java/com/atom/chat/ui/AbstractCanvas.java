package com.atom.chat.ui;

import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable clipping container, modeled after Tuui's AbstractCanvas.
 */
public class AbstractCanvas extends UiComponent {
    private final List<UiComponent> children = new ArrayList<>();
    private float scrollY;
    private float maxScroll;
    private float radius = 0.0F;

    public AbstractCanvas addChild(UiComponent child) {
        children.add(child);
        return this;
    }

    public AbstractCanvas radius(float radius) {
        this.radius = radius;
        return this;
    }

    public void setScrollY(float scrollY) {
        this.scrollY = scrollY;
        clampScroll();
    }

    public float getScrollY() {
        return scrollY;
    }

    public void recomputeMaxScroll() {
        float contentHeight = 0.0F;
        for (UiComponent child : children) {
            contentHeight = Math.max(contentHeight, child.getY() + child.getHeight());
        }
        maxScroll = Math.max(0.0F, contentHeight - height);
    }

    private void clampScroll() {
        recomputeMaxScroll();
        scrollY = Math.max(0.0F, Math.min(scrollY, maxScroll));
    }

    @Override
    public void render(Canvas canvas) {
        if (!visible) {
            return;
        }
        canvas.save();
        try {
            if (radius > 0) {
                SkiaDraw.clip(canvas, x, y, width, height, radius);
            } else {
                canvas.clipRect(io.github.humbleui.types.Rect.makeXYWH(x, y, width, height));
            }
            canvas.translate(0.0F, -scrollY);
            for (UiComponent child : children) {
                if (child.isVisible()) {
                    child.render(canvas);
                }
            }
        } finally {
            canvas.restore();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        setScrollY(scrollY - (float) amount * 20.0F);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        for (UiComponent child : children) {
            double localY = mouseY + scrollY;
            if (child.isMouseOver(mouseX, localY) && child.mouseClicked(mouseX, localY, button)) {
                return true;
            }
        }
        return false;
    }
}
