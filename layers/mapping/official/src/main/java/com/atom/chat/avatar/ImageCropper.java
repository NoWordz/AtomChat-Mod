package com.atom.chat.avatar;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathFillMode;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QQ-style image crop overlay: a modal inside the panel. A fixed frame sits in
 * the middle — a circle (avatar) or a rounded rectangle with a given aspect
 * (wallpaper) — and the image pans underneath it (drag), zooms around the
 * frame centre (wheel), resets on double-click, and must always cover the
 * frame. Confirming encodes the visible region to PNG and hands the bytes to
 * the callback together with the caller's target id.
 */
public final class ImageCropper {
    private static final int OUT_MAX = 1024;
    private static final int AVATAR_OUT = 256;
    private static final long DOUBLE_CLICK_MS = 300L;

    /** Receives the cropped PNG; runs on the render thread. */
    public interface Callback {
        void onConfirm(String targetId, byte[] pngBytes);

        void onCancel(String targetId);
    }

    private final Callback callback;

    private final AtomicInteger GENERATION = new AtomicInteger();
    private volatile Image decoded;
    private volatile int decodeW;
    private volatile int decodeH;

    private boolean active;
    private boolean circle;
    private String targetId = "";
    /** Frame size (circle diameter) or aspect pair (rect mode). */
    private float frameW;
    private float frameH;
    private float anim;
    private boolean closing;
    private boolean transformReady;
    private long lastFrameMs = System.currentTimeMillis();

    private float scale;
    private float offX;
    private float offY;
    private boolean dragging;
    private float dragStartX;
    private float dragStartY;
    private float offStartX;
    private float offStartY;
    private long lastClickMs;

    private float btnHoverCheck;
    private float btnHoverClose;
    /** One bounce per round key. The cropper holds no press state to feed them. */
    private final PressScale cancelScale = PressScale.bounce();
    private final PressScale confirmScale = PressScale.bounce();

    public ImageCropper(Callback callback) {
        this.callback = callback;
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /**
     * Opens the cropper for a freshly picked file; decodes off-thread.
     *
     * @param circle true → circular frame sized {@code frameW} px
     * @param frameW circle mode: frame diameter; rect mode: aspect width
     * @param frameH rect mode: aspect height (ignored in circle mode)
     */
    public void open(java.nio.file.Path source, boolean circle,
                     float frameW, float frameH, String targetId) {
        active = true;
        this.circle = circle;
        this.targetId = targetId != null ? targetId : "";
        this.frameW = frameW;
        this.frameH = circle ? frameW : frameH;
        dragging = false;
        anim = 0.0F;
        closing = false;
        decoded = null;
        transformReady = false;
        if (source == null || !Files.isRegularFile(source)) {
            return;
        }
        final java.nio.file.Path path = source;
        final int generation = GENERATION.incrementAndGet();
        Thread worker = new Thread(() -> {
            Image result = null;
            try {
                result = downscale(Image.makeFromEncoded(Files.readAllBytes(path)));
            } catch (Throwable ignored) {
                result = null;
            }
            if (generation == GENERATION.get()) {
                decoded = result;
                decodeW = result != null ? result.getWidth() : 0;
                decodeH = result != null ? result.getHeight() : 0;
                transformReady = false;
            } else if (result != null) {
                result.close();
            }
        }, "AtomChat-CropDecode");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isActive() {
        return active;
    }

    public void cancel() {
        if (closing) {
            return;
        }
        String id = targetId;
        callback.onCancel(id);
        // The decoded bitmap stays alive through the fade-out; the resources
        // are released in finishClose() once the animation has played.
        beginClose();
    }

    private void beginClose() {
        closing = true;
        dragging = false;
    }

    private void finishClose() {
        active = false;
        closing = false;
        GENERATION.incrementAndGet();
        if (decoded != null) {
            decoded.close();
            decoded = null;
        }
    }

    private void resetToBase(UiLayout.Rect panel) {
        scale = baseScaleAt(panel);
        offX = frameCx(panel);
        offY = frameCy(panel);
    }

    /**
     * Base (minimum cover) scale computed from the frame as actually rendered
     * (circle diameter / panel-fitted rect), never from the raw stored aspect —
     * the two diverge for the avatar (circle ignores the stored size) and the
     * wallpaper (the stored 440x780 is fitted into 62% of the panel), and a
     * mismatch either hides the image entirely (scale 0) or pins the zoom to a
     * level that only shows a small slice of it.
     */
    private float baseScaleAt(UiLayout.Rect panel) {
        return CropMath.baseScale(decodeW, decodeH, 2.0F * frameRx(panel), 2.0F * frameRy(panel));
    }

    private float frameCx(UiLayout.Rect panel) {
        return panel.x() + panel.w() / 2.0F;
    }

    private float frameCy(UiLayout.Rect panel) {
        return panel.y() + panel.h() / 2.0F;
    }

    /** Circle radius, or the rect frame's half width. */
    private float frameRx(UiLayout.Rect panel) {
        if (circle) {
            return Math.min(panel.w(), panel.h()) * 0.30F;
        }
        float maxW = panel.w() * 0.62F;
        float maxH = panel.h() * 0.62F;
        float w = Math.min(maxW, maxH * frameW / frameH);
        return w / 2.0F;
    }

    /** Circle radius, or the rect frame's half height. */
    private float frameRy(UiLayout.Rect panel) {
        if (circle) {
            return frameRx(panel);
        }
        return frameRx(panel) * frameH / frameW;
    }

    private float btnCy(UiLayout.Rect panel) {
        return panel.y() + panel.h() - s(56);
    }

    private float confirmCx(UiLayout.Rect panel) {
        return panel.x() + panel.w() / 2.0F + s(36);
    }

    private float cancelCx(UiLayout.Rect panel) {
        return panel.x() + panel.w() / 2.0F - s(36);
    }

    private boolean inButton(float vmx, float vmy, float bxCx, UiLayout.Rect panel) {
        float r = s(24);
        float cy = btnCy(panel);
        float dx = vmx - bxCx;
        float dy = vmy - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** A click: buttons, double-click reset, otherwise starts a pan. Consumes. */
    public void onClick(float vmx, float vmy, UiLayout.Rect panel) {
        if (closing) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean doubleClick = now - lastClickMs < DOUBLE_CLICK_MS;
        lastClickMs = now;
        if (inButton(vmx, vmy, confirmCx(panel), panel)) {
            confirm(panel);
            return;
        }
        if (inButton(vmx, vmy, cancelCx(panel), panel)) {
            cancel();
            return;
        }
        if (decoded == null || !transformReady) {
            return;
        }
        if (doubleClick) {
            resetToBase(panel);
            return;
        }
        dragging = true;
        dragStartX = vmx;
        dragStartY = vmy;
        offStartX = offX;
        offStartY = offY;
    }

    /** Continues a pan; coordinates are virtual UI space. */
    public void onDrag(float vmx, float vmy, UiLayout.Rect panel) {
        if (closing || !dragging || decoded == null || !transformReady) {
            return;
        }
        float[] p = CropMath.clampCover(decodeW, decodeH, scale,
                frameCx(panel), frameCy(panel), frameRx(panel), frameRy(panel),
                offStartX + (vmx - dragStartX), offStartY + (vmy - dragStartY));
        offX = p[0];
        offY = p[1];
    }

    public void endDrag() {
        dragging = false;
    }

    /** Wheel zoom anchored at the frame centre; consumes the event. */
    public void onScroll(UiLayout.Rect panel, double verticalAmount) {
        if (closing || decoded == null || !transformReady) {
            return;
        }
        float factor = verticalAmount > 0 ? 1.12F : 1.0F / 1.12F;
        float base = baseScaleAt(panel);
        float[] p = CropMath.zoomAtCentre(decodeW, decodeH, scale,
                frameCx(panel), frameCy(panel), frameRx(panel), frameRy(panel),
                offX, offY, factor, base);
        scale = p[0];
        offX = p[1];
        offY = p[2];
    }

    private void confirm(UiLayout.Rect panel) {
        if (decoded == null || !transformReady) {
            return;
        }
        float rx = frameRx(panel);
        float ry = frameRy(panel);
        float[] sq = CropMath.visibleRect(decodeW, decodeH, scale,
                frameCx(panel), frameCy(panel), rx, ry, offX, offY);
        int outW;
        int outH;
        if (circle) {
            outW = AVATAR_OUT;
            outH = AVATAR_OUT;
        } else {
            float down = Math.min(1.0F, (float) OUT_MAX / Math.max(sq[2], sq[3]));
            outW = Math.max(1, Math.round(sq[2] * down));
            outH = Math.max(1, Math.round(sq[3] * down));
        }
        byte[] bytes;
        try (Surface surface = Surface.makeRasterN32Premul(outW, outH)) {
            Canvas canvas = surface.getCanvas();
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(decoded,
                        Rect.makeXYWH(sq[0], sq[1], sq[2], sq[3]),
                        Rect.makeXYWH(0, 0, outW, outH),
                        SamplingMode.LINEAR, paint, false);
            }
            try (io.github.humbleui.skija.Data data =
                         surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)) {
                bytes = data.getBytes();
            }
        }
        String id = targetId;
        callback.onConfirm(id, bytes);
        beginClose();
    }

    public void render(Canvas canvas, UiLayout.Rect panel, float vmx, float vmy) {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        // Symmetric fade-out (0.1.10 audit): the close request reverses the
        // open curve; the overlay stays active so it keeps swallowing input
        // until the fade has fully played.
        anim = UiMotion.approach(anim, closing ? 0.0F : 1.0F, dt, Animations.ms(UiMotion.POPUP_MS));
        if (closing && anim <= 0.02F) {
            finishClose();
            return;
        }
        if (anim < 0.01F) {
            return;
        }

        float panelW = panel.w();
        float panelH = panel.h();
        float cx = frameCx(panel);
        float cy = frameCy(panel);
        float rx = frameRx(panel);
        float ry = frameRy(panel);
        int alpha = (int) (255.0F * anim);

        canvas.save();
        try {
            SkiaDraw.drawRoundedRect(canvas, panel.x(), panel.y(), panelW, panelH,
                    s(12), Color.makeARGB((int) (200.0F * anim), 8, 9, 14));

            Image image = decoded;
            if (image != null && !transformReady) {
                resetToBase(panel);
                transformReady = true;
            }
            if (image != null) {
                // offX/offY is the image centre in absolute virtual coords.
                float left = offX - decodeW * scale / 2.0F;
                float top = offY - decodeH * scale / 2.0F;
                canvas.save();
                SkiaDraw.clip(canvas, panel.x(), panel.y(), panelW, panelH, s(12));
                try (Paint paint = new Paint().setAntiAlias(true)) {
                    canvas.drawImageRect(image,
                            Rect.makeXYWH(0, 0, decodeW, decodeH),
                            Rect.makeXYWH(left, top, decodeW * scale, decodeH * scale),
                            SamplingMode.LINEAR, paint, false);
                } finally {
                    canvas.restore();
                }

                // Darken everything outside the frame (even-odd panel + frame).
                try (Path mask = new Path()) {
                    mask.addRect(Rect.makeXYWH(panel.x(), panel.y(), panelW, panelH));
                    if (circle) {
                        mask.addOval(Rect.makeXYWH(cx - rx, cy - ry, 2.0F * rx, 2.0F * ry));
                    } else {
                        mask.addRRect(RRect.makeXYWH(cx - rx, cy - ry, 2.0F * rx, 2.0F * ry, s(12)));
                    }
                    mask.setFillMode(PathFillMode.EVEN_ODD);
                    try (Paint paint = new Paint().setColor(Color.makeARGB((int) (150.0F * anim), 0, 0, 0))) {
                        canvas.drawPath(mask, paint);
                    }
                }
            }

            try (Paint ring = new Paint().setColor(Color.makeARGB(alpha, 255, 255, 255))
                    .setMode(PaintMode.STROKE).setStrokeWidth(s(2)).setAntiAlias(true)) {
                if (circle) {
                    canvas.drawOval(Rect.makeXYWH(cx - rx, cy - ry, 2.0F * rx, 2.0F * ry), ring);
                } else {
                    canvas.drawRRect(RRect.makeXYWH(cx - rx, cy - ry, 2.0F * rx, 2.0F * ry, s(12)), ring);
                }
            }

            // Confirm and cancel are the only hover controls here. The frame ring
            // is not a drag handle and the pan has no discrete knob, so nothing
            // else scales; the cropper as a whole deliberately stays put.
            float cxCancel = cancelCx(panel);
            float cxConfirm = confirmCx(panel);
            float cyButtons = btnCy(panel);
            // The scaled shapes are the s(24)-radius round keys themselves.
            cancelScale.update(inButton(vmx, vmy, cxCancel, panel), false, dt, Animations.enabled(),
                    s(24) * 2.0F);
            confirmScale.update(inButton(vmx, vmy, cxConfirm, panel), false, dt, Animations.enabled(),
                    s(24) * 2.0F);
            cancelScale.begin(canvas, cxCancel, cyButtons);
            try {
                drawRoundButton(canvas, cxCancel, cyButtons,
                        AppIcons.ICON_CLOSE_PATH, btnHoverClose, alpha);
            } finally {
                canvas.restore();
            }
            confirmScale.begin(canvas, cxConfirm, cyButtons);
            try {
                drawRoundButton(canvas, cxConfirm, cyButtons,
                        AppIcons.ICON_CHECK_PATH, btnHoverCheck, alpha);
            } finally {
                canvas.restore();
            }

            if (image == null) {
                Font font = FontManager.font(UiTokens.PROFILE_ROW_FONT);
                SkiaFontRenderer.drawTextCentered(canvas, font,
                        Component.translatable("atomchat.profile.crop.loading").getString(),
                        cx, cy, Color.makeARGB(alpha, 255, 255, 255));
            }
        } finally {
            canvas.restore();
        }

        btnHoverCheck = UiMotion.approach(btnHoverCheck,
                inButton(vmx, vmy, confirmCx(panel), panel) ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        btnHoverClose = UiMotion.approach(btnHoverClose,
                inButton(vmx, vmy, cancelCx(panel), panel) ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
    }

    private void drawRoundButton(Canvas canvas, float cx, float cy, Path icon, float hover, int alpha) {
        float r = s(24);
        SkiaDraw.drawRoundedShadow(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r, s(6),
                Color.makeARGB((int) (0.4F * alpha), 0, 0, 0));
        SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r,
                Color.makeARGB((int) (0.92F * alpha), 35, 39, 47));
        if (hover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r,
                    Color.makeARGB((int) (60.0F * hover), 255, 255, 255));
        }
        float size = r;
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float sc = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * sc,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * sc);
            canvas.scale(sc, sc);
            try (Paint paint = new Paint().setColor(Color.makeARGB(alpha, 255, 255, 255))
                    .setAntiAlias(true).setMode(PaintMode.STROKE)
                    .setStrokeWidth(1.5F / sc)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    /** Longest side capped at 1024 — plenty for a 256px output at 4x zoom. */
    private static Image downscale(Image source) {
        if (source == null) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        float sc = Math.min(1.0F, Math.min(1024.0F / w, 1024.0F / h));
        if (sc >= 0.999F) {
            return source;
        }
        int tw = Math.max(1, Math.round(w * sc));
        int th = Math.max(1, Math.round(h * sc));
        try (Surface surface = Surface.makeRasterN32Premul(tw, th)) {
            Canvas canvas = surface.getCanvas();
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(source,
                        Rect.makeXYWH(0, 0, w, h),
                        Rect.makeXYWH(0, 0, tw, th),
                        SamplingMode.LINEAR, paint, false);
            }
            source.close();
            return surface.makeImageSnapshot();
        }
    }
}
