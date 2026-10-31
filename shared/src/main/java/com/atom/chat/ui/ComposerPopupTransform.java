package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;

/** One affine pose for composer-popup painting and pointer conversion. */
public record ComposerPopupTransform(float scale, float cx, float cy, float drop) {
    public record Point(float x, float y) { }
    public static ComposerPopupTransform of(UiLayout.Rect box, float progress) {
        return new ComposerPopupTransform(UiTokens.COMPOSER_POPUP_START_SCALE
                + (1 - UiTokens.COMPOSER_POPUP_START_SCALE) * progress,
                box.x() + box.w() / 2, box.y() + box.h() / 2,
                (1 - progress) * UiTokens.COMPOSER_POPUP_DROP);
    }
    public void apply(Canvas canvas) {
        canvas.translate(cx, cy); canvas.scale(scale, scale); canvas.translate(-cx, -cy); canvas.translate(0, drop);
    }
    public Point toLocal(float x, float y) { return new Point(cx + (x - cx) / scale, cy + (y - cy) / scale - drop); }
    public Point toScreen(float x, float y) { return new Point(cx + scale * (x - cx), cy + scale * (y - cy + drop)); }
    public UiLayout.Rect paintedBounds(UiLayout.Rect box) {
        var p = toScreen(box.x(), box.y()); return new UiLayout.Rect(p.x(), p.y(), box.w() * scale, box.h() * scale);
    }
}
