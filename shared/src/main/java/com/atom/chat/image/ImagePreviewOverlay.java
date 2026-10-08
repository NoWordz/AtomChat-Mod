package com.atom.chat.image;

import com.atom.chat.render.SkiaDraw;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;

/**
 * Full-panel picture preview: the chat image at the largest size the panel
 * allows, over a dimmed backdrop, with a round close key centred under it.
 *
 * <p>It is a modal in the same sense as the cropper and the colour picker: while
 * open it owns every input event (the screen's {@code ModalInput} hands them
 * over and returns "consumed"), it fades in and out on
 * {@link UiMotion#POPUP_MS}, and its close key is the same round key those two
 * draw. Deliberately nothing else: no zoom, no panning, no saving in this
 * version.
 *
 * <p><b>The click rule is the whole interaction.</b> A click on the picture
 * itself does nothing — the preview is something you read, and a mis-click on a
 * photo should not dismiss it. A click anywhere else (the dim backdrop, and the
 * close key, which is just a marked part of that backdrop) closes it.
 *
 * <p><b>Where the bitmap comes from.</b> {@link #open(Image)} takes the bitmap
 * the message list already resolved through {@link ImageLoader}'s cache, so
 * opening a preview never downloads anything a second time. The overlay holds
 * that reference until it finishes closing; that is safe by the loader's own
 * rule — cached bitmaps are never eagerly closed, evicted entries rely on
 * Skija's finalisation, so a bitmap handed out for drawing stays valid (see
 * {@code ImageLoader}'s cache comments).
 */
public final class ImagePreviewOverlay {

    /** How far the content starts out shrunk while it fades in. */
    private static final float ENTRANCE_SCALE = 0.94F;
    /** Breathing room between the panel's edge and everything drawn here. */
    private static final float PAD = 18.0F;
    /** Radius of the round close key. */
    private static final float CLOSE_RADIUS = 20.0F;
    /** Gap between the picture's box and the close key. */
    private static final float CLOSE_GAP = 14.0F;

    private Image image;
    private int imageW;
    private int imageH;

    private boolean active;
    private boolean closing;
    private float anim;
    private long lastFrameMs = System.currentTimeMillis();

    private float closeHover;
    /** The close key breathes on hover; the click request itself is instant. */
    private final PressScale closeScale = PressScale.bounce();

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /**
     * Shows the picture. A null bitmap is not a preview — a dim plate with a
     * close key and nothing behind it — so it is refused instead of displayed;
     * the click path only ever opens a bitmap the frame actually drew.
     */
    public void open(Image image) {
        if (image == null) {
            return;
        }
        this.image = image;
        this.imageW = image.getWidth();
        this.imageH = image.getHeight();
        this.closing = false;
        this.anim = 0.0F;
        this.active = true;
        this.lastFrameMs = System.currentTimeMillis();
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Whether the fade-out has been requested. The overlay stays {@link #isActive()
     * active} through it — that is what keeps it owning input until it is gone —
     * so "closing" is the state a click lands in, and this is it. Package-private:
     * nothing outside this class needs to read it.
     */
    boolean isClosing() {
        return closing;
    }

    /** Requests the fade-out (Esc, the close key, or a click on the backdrop). */
    public void close() {
        if (active && !closing) {
            closing = true;
        }
    }

    /**
     * A click while open. The picture itself is inert; anything else — the dim
     * backdrop around it, and the close key, which is drawn as part of that
     * backdrop — dismisses the preview.
     */
    public void onClick(float vmx, float vmy, UiLayout.Rect panel) {
        if (!active || closing) {
            return;
        }
        if (image != null && imageRect(panel, imageW, imageH).contains(vmx, vmy)) {
            return;
        }
        close();
    }

    // ------------------------------------------------------------- geometry

    /**
     * The rect the picture is drawn into, which is also the rect a click on the
     * picture is tested against: the source's own aspect, centred in the panel's
     * content box (the panel inset by {@link #PAD} on every side, minus the
     * strip the close key owns at the bottom), and never enlarged past the
     * bitmap's own pixels — the same rule the message bubble follows, so a small
     * picture is shown sharp instead of blown up into mush.
     */
    public static UiLayout.Rect imageRect(UiLayout.Rect panel, int imageW, int imageH) {
        float boxX = panel.x() + s(PAD);
        float boxY = panel.y() + s(PAD);
        float boxW = Math.max(1.0F, panel.w() - s(PAD) * 2.0F);
        float boxH = Math.max(1.0F, panel.h() - s(PAD) * 2.0F - closeStrip());
        if (imageW <= 0 || imageH <= 0) {
            return new UiLayout.Rect(boxX, boxY, 0.0F, 0.0F);
        }
        float scale = Math.min(1.0F, Math.min(boxW / imageW, boxH / imageH));
        float w = imageW * scale;
        float h = imageH * scale;
        return new UiLayout.Rect(boxX + (boxW - w) / 2.0F, boxY + (boxH - h) / 2.0F, w, h);
    }

    /** The close key's square, centred on the panel's bottom strip. */
    public static UiLayout.Rect closeRect(UiLayout.Rect panel) {
        float r = s(CLOSE_RADIUS);
        return new UiLayout.Rect(panel.x() + panel.w() / 2.0F - r,
                panel.y() + panel.h() - s(PAD) - r * 2.0F, r * 2.0F, r * 2.0F);
    }

    /**
     * Height the close key owns below the picture's box. Reserving it (rather
     * than hanging the key off the picture's own bottom edge) is what keeps the
     * key inside the panel for every aspect ratio: the fit can be as tall as the
     * box, so a key placed below the picture itself would fall outside the panel
     * exactly on portrait pictures.
     */
    private static float closeStrip() {
        return s(CLOSE_RADIUS) * 2.0F + s(CLOSE_GAP);
    }

    // ---------------------------------------------------------------- render

    public void render(Canvas canvas, UiLayout.Rect panel, float vmx, float vmy) {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        // Symmetric fade: the close request reverses the open curve, and the
        // overlay stays active so it keeps swallowing input until the fade has
        // fully played (same lifecycle as the cropper and the colour picker).
        anim = UiMotion.approach(anim, closing ? 0.0F : 1.0F, dt, Animations.ms(UiMotion.POPUP_MS));
        if (closing && anim <= 0.02F) {
            active = false;
            closing = false;
            image = null;
            imageW = 0;
            imageH = 0;
            return;
        }
        if (anim < 0.01F) {
            return;
        }
        int alpha = (int) (255.0F * anim);

        canvas.save();
        try {
            // The dim backdrop: the panel-wide plate the other two modals use, so
            // a modal always reads as the same layer.
            SkiaDraw.drawRoundedRect(canvas, panel.x(), panel.y(), panel.w(), panel.h(),
                    UiTokens.radius(12), Color.makeARGB((int) (200.0F * anim), 8, 9, 14));

            Image current = image;
            if (current != null) {
                UiLayout.Rect rect = imageRect(panel, imageW, imageH);
                float cx = rect.x() + rect.w() / 2.0F;
                float cy = rect.y() + rect.h() / 2.0F;
                // The entrance scales the picture up into place around its own
                // centre. Draw-only, like every other scale in this UI: hit
                // geometry stays the settled rect, so a click during the ~110ms
                // entrance already behaves the way the settled frame looks.
                float grow = ENTRANCE_SCALE + (1.0F - ENTRANCE_SCALE) * anim;
                canvas.save();
                try {
                    canvas.translate(cx, cy);
                    canvas.scale(grow, grow);
                    canvas.translate(-cx, -cy);
                    SkiaDraw.drawRoundedImage(canvas, current, rect.x(), rect.y(),
                            rect.w(), rect.h(), UiTokens.radius(8));
                } finally {
                    canvas.restore();
                }
            }

            UiLayout.Rect key = closeRect(panel);
            boolean overKey = key.contains(vmx, vmy);
            float kcx = key.x() + key.w() / 2.0F;
            float kcy = key.y() + key.h() / 2.0F;
            closeScale.update(overKey, false, dt, Animations.enabled(), key.w());
            closeScale.begin(canvas, kcx, kcy);
            try {
                drawCloseKey(canvas, kcx, kcy, alpha);
            } finally {
                canvas.restore();
            }
        } finally {
            canvas.restore();
        }

        closeHover = UiMotion.approach(closeHover, closeRect(panel).contains(vmx, vmy) ? 1.0F : 0.0F,
                dt, UiMotion.HOVER_MS);
    }

    /** The round close key: the same dark plate and white glyph as the modals'. */
    private void drawCloseKey(Canvas canvas, float cx, float cy, int alpha) {
        float r = s(CLOSE_RADIUS);
        SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r,
                Color.makeARGB((int) (0.92F * alpha), 35, 39, 47));
        if (closeHover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, r,
                    Color.makeARGB((int) (60.0F * closeHover), 255, 255, 255));
        }
        Path icon = AppIcons.ICON_CLOSE_PATH;
        Rect bounds = icon.getBounds();
        if (bounds == null || bounds.isEmpty()) {
            return;
        }
        float sc = s(16.0F) / Math.max(bounds.getWidth(), bounds.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (bounds.getLeft() + bounds.getRight()) / 2.0F * sc,
                    cy - (bounds.getTop() + bounds.getBottom()) / 2.0F * sc);
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
}
