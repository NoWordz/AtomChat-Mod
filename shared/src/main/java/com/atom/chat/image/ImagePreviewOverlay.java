package com.atom.chat.image;

import com.atom.chat.render.SkiaDraw;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.PressScale;
import com.atom.chat.ui.PanelBackground;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import static com.atom.chat.ui.UiTokens.s;
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
 * <p>It owns input until its exit ends. The wheel zooms around the pointer,
 * left dragging pans an enlarged image, and the close strip stays outside the
 * picture viewport. Entrance, exit and wheel motion share the UI's exponential
 * decay; dragging follows the pointer directly.
 *
 * <p><b>The click rule is the whole interaction.</b> A click on the picture
 * itself starts a possible drag. A click anywhere else (the dim backdrop, and the
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
    private static final float MAX_ZOOM = 8.0F;
    private static final float ZOOM_TAU_MS = 45.0F;

    private Image image;
    private int imageW;
    private int imageH;

    private boolean active;
    private boolean closing;
    private float anim;
    private long lastFrameMs = System.currentTimeMillis();
    private float zoom = 1F, targetZoom = 1F;
    private float panX, panY, targetPanX, targetPanY;
    private boolean dragging;
    private float dragX, dragY, dragPanX, dragPanY;

    private float closeHover;
    /** The close key breathes on hover; the click request itself is instant. */
    private final PressScale closeScale = PressScale.bounce();

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
        zoom = targetZoom = 1F;
        panX = panY = targetPanX = targetPanY = 0F;
        dragging = false;
        closeHover = 0F;
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
            dragging = false;
        }
    }

    /**
     * A left press starts a drag only on the visible picture. The close key
     * wins before image hit testing, including while zoomed.
     */
    public void onClick(float vmx, float vmy, UiLayout.Rect panel) {
        if (!active || closing) {
            return;
        }
        if (closeRect(panel).contains(vmx, vmy)) {
            close();
            return;
        }
        if (image != null && viewport(panel).contains(vmx, vmy) && viewRect(panel).contains(vmx, vmy)) {
            targetZoom = zoom;
            targetPanX = panX;
            targetPanY = panY;
            dragging = true;
            dragX = vmx;
            dragY = vmy;
            dragPanX = panX;
            dragPanY = panY;
            return;
        }
        close();
    }

    public void onDrag(float vmx, float vmy, UiLayout.Rect panel) {
        if (!active || closing || !dragging) return;
        UiLayout.Rect base = imageRect(panel, imageW, imageH);
        UiLayout.Rect box = viewport(panel);
        panX = targetPanX = clampPan(dragPanX + vmx - dragX, base.w() * zoom, box.w());
        panY = targetPanY = clampPan(dragPanY + vmy - dragY, base.h() * zoom, box.h());
    }

    public void endDrag() { dragging = false; }

    public void onScroll(float vmx, float vmy, UiLayout.Rect panel, double amount) {
        if (!active || closing || dragging || !Double.isFinite(amount)
                || !Float.isFinite(vmx) || !Float.isFinite(vmy) || !viewport(panel).contains(vmx, vmy)) return;
        float next = (float)Math.max(1.0, Math.min(MAX_ZOOM, targetZoom * Math.pow(1.2, amount)));
        UiLayout.Rect base = imageRect(panel, imageW, imageH);
        UiLayout.Rect box = viewport(panel);
        float ratio = next / zoom;
        // Accumulate wheel steps toward the target size, but anchor the pixel
        // currently on screen. A prior glide may still be settling elsewhere.
        // Equal interpolation of scale and translation then keeps this anchor
        // throughout the next glide, until an edge must clamp.
        targetPanX = clampPan((vmx - base.x() - base.w()/2F) * (1F-ratio) + panX*ratio,
                base.w()*next, box.w());
        targetPanY = clampPan((vmy - base.y() - base.h()/2F) * (1F-ratio) + panY*ratio,
                base.h()*next, box.h());
        targetZoom = next;
    }

    private static float clampPan(float value, float imageSize, float viewportSize) {
        float limit = Math.max(0F, (imageSize - viewportSize) / 2F);
        return Math.max(-limit, Math.min(limit, value));
    }

    private static UiLayout.Rect viewport(UiLayout.Rect panel) {
        return new UiLayout.Rect(panel.x()+s(PAD), panel.y()+s(PAD),
                Math.max(1F, panel.w()-s(PAD)*2F),
                Math.max(1F, panel.h()-s(PAD)*2F-closeStrip()));
    }

    /** The same transformed rectangle used for drawing and hit testing. */
    UiLayout.Rect viewRect(UiLayout.Rect panel) {
        UiLayout.Rect base = imageRect(panel, imageW, imageH);
        UiLayout.Rect box = viewport(panel);
        float grow = Animations.enabled() ? ENTRANCE_SCALE + (1F-ENTRANCE_SCALE)*anim : 1F;
        float w = base.w()*zoom*grow, h = base.h()*zoom*grow;
        return new UiLayout.Rect(base.x()+base.w()/2F + clampPan(panX, base.w()*zoom, box.w())-w/2F,
                base.y()+base.h()/2F + clampPan(panY, base.h()*zoom, box.h())-h/2F, w, h);
    }

    // ------------------------------------------------------------- geometry

    /**
     * Base fitted rect before the interactive view transform: the source's own
     * aspect, centred in the panel's
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
        UiLayout.Rect base = imageRect(panel, imageW, imageH);
        UiLayout.Rect box = viewport(panel);
        targetPanX = clampPan(targetPanX, base.w()*targetZoom, box.w());
        targetPanY = clampPan(targetPanY, base.h()*targetZoom, box.h());
        zoom = UiMotion.expApproach(zoom, targetZoom, dt, ZOOM_TAU_MS);
        panX = clampPan(UiMotion.expApproach(panX, targetPanX, dt, ZOOM_TAU_MS), base.w()*zoom, box.w());
        panY = clampPan(UiMotion.expApproach(panY, targetPanY, dt, ZOOM_TAU_MS), base.h()*zoom, box.h());
        // A close request reverses the current progress with a faster exit; the
        // overlay stays active so it keeps swallowing input until the fade has
        // fully played (same lifecycle as the cropper and the colour picker).
        anim = UiMotion.expApproach(anim, closing ? 0.0F : 1.0F, dt,
                Animations.enabled() ? (closing ? UiMotion.PANEL_CLOSE_TAU_MS : UiMotion.PANEL_OPEN_TAU_MS) : 0F);
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
            PanelBackground.clipInterior(canvas, panel);
            // The dim backdrop: the panel-wide plate the other two modals use, so
            // a modal always reads as the same layer.
            SkiaDraw.drawRoundedRect(canvas, panel.x(), panel.y(), panel.w(), panel.h(),
                    UiTokens.modalBackdropRadius(), Color.makeARGB((int) (200.0F * anim), 8, 9, 14));

            Image current = image;
            if (current != null) {
                UiLayout.Rect rect = viewRect(panel);
                canvas.save();
                try (Paint opacity = anim < 1F ? new Paint().setAlphaf(anim) : null) {
                    SkiaDraw.clip(canvas, box.x(), box.y(), box.w(), box.h(), 0F);
                    if (opacity != null) canvas.saveLayer(Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()), opacity);
                    try {
                        SkiaDraw.drawRoundedImage(canvas, current, rect.x(), rect.y(),
                                rect.w(), rect.h(), UiTokens.radius(8));
                    } finally {
                        if (opacity != null) canvas.restore();
                    }
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
        float closeRadius = UiTokens.radius(CLOSE_RADIUS);
        SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, closeRadius,
                Color.makeARGB((int) (0.92F * alpha), 35, 39, 47));
        if (closeHover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, cx - r, cy - r, 2.0F * r, 2.0F * r, closeRadius,
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
