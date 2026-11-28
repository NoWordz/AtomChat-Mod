package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Rect;

/** Clips scroll content and softens the edges that lead to more content. */
public final class ScrollEdgeFade implements AutoCloseable {
    private static final float EDGE_REACH = 14F;
    private static final ThreadLocal<Window> ACTIVE = new ThreadLocal<>();

    public record Window(UiLayout.Rect bounds, float top, float bottom) {
        /** Late click feedback uses the same viewport as the card that owns it. */
        public void drawFeedback(Canvas canvas, UiLayout.Rect control, Runnable draw) {
            int save = canvas.save();
            try {
                CornerGeometry.clip(canvas, bounds.x(), bounds.y(), bounds.w(), bounds.h(), 0F);
                boolean faded = top > 0F && control.y() < bounds.y() + top
                        || bottom > 0F && control.bottom() > bounds.bottom() - bottom;
                if (faded) {
                    canvas.saveLayer(Rect.makeXYWH(control.x(), control.y(), control.w(), control.h()), null);
                }
                draw.run();
                if (faded) eraseEdges(canvas, bounds, top, bottom);
            } finally {
                canvas.restoreToCount(save);
            }
        }
    }

    public static Window currentWindow() {
        return ACTIVE.get();
    }

    private final Canvas canvas;
    private final int save;
    private final int contentSave;
    private final Window previous;
    private final Window window;
    private final Runnable finishBackdrop;

    public static ScrollEdgeFade clip(Canvas canvas, float x, float y, float width, float height,
                                      float scroll, float maxScroll) {
        return new ScrollEdgeFade(canvas, x, y, width, height, scroll, maxScroll);
    }

    private ScrollEdgeFade(Canvas canvas, float x, float y, float width, float height,
                           float scroll, float maxScroll) {
        this.canvas = canvas;
        save = canvas.save();
        try {
            Rect bounds = Rect.makeXYWH(x, y, Math.max(0F, width), Math.max(0F, height));
            CornerGeometry.clip(canvas, x, y, bounds.getWidth(), bounds.getHeight(), 0F);
            float reach = Math.min(UiTokens.s(EDGE_REACH), bounds.getHeight() / 2F);
            float top = Math.max(0F, Math.min(reach, scroll));
            float bottom = Math.max(0F, Math.min(reach, maxScroll - scroll));
            previous = ACTIVE.get();
            window = new Window(new UiLayout.Rect(x, y, bounds.getWidth(), bounds.getHeight()), top, bottom);
            if (width <= 0F || (top == 0F && bottom == 0F)) {
                contentSave = 0;
                finishBackdrop = null;
            } else {
                finishBackdrop = CardBackdrop.groupMasks(canvas, this::eraseEdges);
                canvas.saveLayer(bounds, null);
                contentSave = canvas.save();
            }
            ACTIVE.set(window);
        } catch (RuntimeException | Error failure) {
            canvas.restoreToCount(save);
            throw failure;
        }
    }

    @Override public void close() {
        try {
            if (contentSave != 0) {
                canvas.restoreToCount(contentSave);
                eraseEdges(canvas);
                if (finishBackdrop != null) finishBackdrop.run();
            }
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
            canvas.restoreToCount(save);
        }
    }

    private void eraseEdges(Canvas target) {
        eraseEdges(target, window.bounds(), window.top(), window.bottom());
    }

    private static void eraseEdges(Canvas target, UiLayout.Rect bounds, float top, float bottom) {
        if (top > 0F) erase(target, bounds, bounds.y(), top);
        if (bottom > 0F) erase(target, bounds, bounds.bottom(), -bottom);
    }

    private static void erase(Canvas target, UiLayout.Rect bounds, float edge, float depth) {
        int level = target.save();
        try {
            target.translate(bounds.x(), edge);
            target.scale(bounds.w(), depth);
            target.drawRect(Mask.UNIT, Mask.PAINT);
        } finally {
            target.restoreToCount(level);
        }
    }

    private static final class Mask {
        private static final Rect UNIT = Rect.makeWH(1, 1);
        private static final Paint PAINT;
        static {
            try (var gradient = Shader.makeLinearGradient(0, 0, 0, 1,
                    new int[]{0xFF000000, 0x00000000})) {
                PAINT = new Paint().setBlendMode(BlendMode.DST_OUT).setShader(gradient);
            }
        }
    }
}
