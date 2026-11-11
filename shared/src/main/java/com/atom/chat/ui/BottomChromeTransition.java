package com.atom.chat.ui;

import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Picture;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.types.Rect;
import java.util.function.Consumer;

/** One real chrome surface; two normalized content pictures, never a chain of old layers. */
public final class BottomChromeTransition implements AutoCloseable {
    public enum Mode { CHAT, NAV, HIDDEN }
    private final BottomChromeMotion geometry = new BottomChromeMotion();
    private final BottomChromeMotion blend = new BottomChromeMotion();
    private Picture chat, nav;
    private Mode mode;
    private UiLayout.Rect rest;
    private long frameTime;
    private final Paint alpha = new Paint();

    public void beginFrame(Mode mode, UiLayout.Rect chatRect, UiLayout.Rect navRect, long now, boolean enabled) {
        frameTime = now;
        boolean pageChanged = this.mode != mode;
        this.mode = mode;
        rest = mode == Mode.CHAT ? chatRect : navRect;
        BottomChromeMotion.Shape target = mode == Mode.HIDDEN
                ? new BottomChromeMotion.Shape(navRect.x() + navRect.w() / 2, navRect.y(), 0, navRect.h(), 0)
                : new BottomChromeMotion.Shape(rest.x(), rest.y(), rest.w(), rest.h(), 1);
        if (pageChanged) geometry.target(target, now, enabled);
        else geometry.follow(target, now, enabled);
        if (mode != Mode.HIDDEN) blend.target(new BottomChromeMotion.Shape(mode == Mode.NAV ? 1 : 0, 0, 0, 0, 1), now, enabled);
    }

    public BottomChromeMotion.Shape paintedShape() { return geometry.sample(frameTime); }
    public UiLayout.Rect paintedRect() {
        var shape = paintedShape();
        return new UiLayout.Rect(shape.x(), shape.y(), shape.w(), shape.h());
    }
    public float inverseX(float x) {
        var s = paintedShape();
        return rest == null || s.w() <= 0 ? x : rest.x() + (x - s.x()) * rest.w() / s.w();
    }
    public float inverseY(float y) {
        var s = paintedShape();
        return rest == null || s.h() <= 0 ? y : rest.y() + (y - s.y()) * rest.h() / s.h();
    }

    /** Original dot band stays behind page cards/messages, in physical UI coordinates. */
    public void drawBackdrop(Canvas canvas) {
        var shape = paintedShape();
        if (shape.w() < .01f || shape.h() < .01f || shape.alpha() <= 0) return;
        int save = beginShapeLayer(canvas, shape);
        try {
            float radius = Math.min(UiTokens.shellChromeRadius(), Math.min(shape.w(), shape.h()) / 2);
            // HEAD chat drew its shadow before the message list; nav drew it after the body.
            if (mode == Mode.CHAT)
                SkiaDraw.drawChromeShadow(canvas, shape.x(), shape.y(), shape.w(), shape.h(), radius);
            if (geometry.settled(frameTime))
                com.atom.chat.render.DotTexture.drawEdge(canvas, paintedRect(), radius, true,
                        com.atom.chat.config.AtomChatConfig.get().textSecondaryColor);
            else
                com.atom.chat.render.DotTexture.drawMovingEdge(canvas, paintedRect(), radius, true,
                        com.atom.chat.config.AtomChatConfig.get().textSecondaryColor);
        } finally { canvas.restoreToCount(save); }
    }

    private int beginShapeLayer(Canvas canvas, BottomChromeMotion.Shape shape) {
        int save = canvas.save();
        if (shape.alpha() < 1) {
            float pad = Math.max(UiTokens.floatSurfaceShadowPad(), UiTokens.MESSAGE_DOT_DEPTH);
            alpha.setAlphaf(shape.alpha());
            canvas.saveLayer(Rect.makeXYWH(shape.x() - pad, shape.y() - pad,
                    shape.w() + pad * 2, shape.h() + pad * 2), alpha);
        }
        return save;
    }

    public void draw(Canvas canvas, Consumer<Canvas> contents) {
        if (mode != Mode.HIDDEN && rest != null && rest.w() > 0 && rest.h() > 0) {
            Picture picture;
            try (PictureRecorder recorder = new PictureRecorder()) {
                Canvas recording = recorder.beginRecording(Rect.makeXYWH(-.5f, -.5f, 2, 2));
                recording.scale(1 / rest.w(), 1 / rest.h());
                recording.translate(-rest.x(), -rest.y());
                contents.accept(recording);
                picture = recorder.finishRecordingAsPicture();
            }
            if (mode == Mode.CHAT) { if (chat != null) chat.close(); chat = picture; }
            else { if (nav != null) nav.close(); nav = picture; }
        }
        var shape = paintedShape();
        if (shape.w() < .01f || shape.h() < .01f || shape.alpha() <= 0) return;
        int save = beginShapeLayer(canvas, shape);
        try {
            float radius = Math.min(UiTokens.shellChromeRadius(), Math.min(shape.w(), shape.h()) / 2);
            if (mode != Mode.CHAT)
                SkiaDraw.drawChromeShadow(canvas, shape.x(), shape.y(), shape.w(), shape.h(), radius);
            SkiaDraw.drawRoundedRect(canvas, shape.x(), shape.y(), shape.w(), shape.h(), radius, UiTokens.cardFill());
            com.atom.chat.render.ChromeMaterial.drawDetails(canvas, paintedRect(), radius);
            canvas.translate(shape.x(), shape.y());
            canvas.scale(shape.w(), shape.h());
            float mix = Math.max(0, Math.min(1, blend.sample(frameTime).x()));
            drawContent(canvas, chat, 1 - mix);
            drawContent(canvas, nav, mix);
        } finally { canvas.restoreToCount(save); }
    }
    private void drawContent(Canvas canvas, Picture picture, float opacity) {
        if (picture == null || opacity <= 0) return;
        int save = canvas.save();
        try {
            if (opacity < 1) {
                alpha.setAlphaf(opacity);
                canvas.saveLayer(Rect.makeXYWH(-.5f, -.5f, 2, 2), alpha);
            }
            picture.playback(canvas);
        } finally { canvas.restoreToCount(save); }
    }
    public void reset() {
        if (chat != null) { chat.close(); chat = null; }
        if (nav != null) { nav.close(); nav = null; }
        mode = null; rest = null; geometry.reset(); blend.reset();
    }
    @Override public void close() { reset(); alpha.close(); }
}
