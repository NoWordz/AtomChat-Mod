package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Picture;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Small, static decorations; paints and the fixed blur filter live on the render thread. */
public final class DotTexture {
    private static final Paint DOT = new Paint().setAntiAlias(true);
    private static final MaskFilter BLUR = MaskFilter.makeBlur(FilterBlurMode.NORMAL, UiTokens.DOT_GLOW_BLUR);
    private static final Paint GLOW = new Paint().setAntiAlias(true).setMaskFilter(BLUR);
    private static final int[] ALPHAS = {12, 16, 20, 24, 28, 31, 34, 37, 40};

    private static final int KIND_EDGE = 0, KIND_TRAIL = 1, KIND_HALO = 2;
    private static final int MAX_CACHED = 64;
    private static final int MAX_HALO_IMAGES = 8;
    private static final long MAX_HALO_BYTES = 32L * 1024L * 1024L;
    private static final int HALO_SUPERSAMPLE = 4;
    private static final float HALO_IMAGE_MIN_AREA = 40_000.0F;
    /** Covers every possible dot coordinate so the recorder never drops a command. */
    private static final Rect CULL_RECT = Rect.makeXYWH(-1_000_000F, -1_000_000F, 2_000_000F, 2_000_000F);

    /**
     * Enabled by default. Edges and trails record their exact commands into a
     * Picture; the About hero halo is a large, position-independent static
     * field, so it is rasterised once into a bounded 2x Image and emitted as
     * one texture draw. Only fields of at least 40k logical square pixels use
     * the Image path (bounded at eight LSB per channel); smaller fields
     * keep byte-identical Picture/direct paths. Setting the
     * system property false keeps the original per-circle fallback.
     */
    private static volatile boolean cached = !"false".equalsIgnoreCase(
            System.getProperty("atomchat.dots.cached", "true"));
    private static long buildCount, hitCount;
    private static final LinkedHashMap<Key, Picture> CACHE = new LinkedHashMap<Key, Picture>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Picture> eldest) {
            boolean drop = size() > MAX_CACHED;
            if (drop) eldest.getValue().close();
            return drop;
        }
    };
    private static final LinkedHashMap<HaloKey, Image> HALO_IMAGES =
            new LinkedHashMap<>(4, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<HaloKey, Image> eldest) {
                    boolean drop = size() > MAX_HALO_IMAGES;
                    if (drop) eldest.getValue().close();
                    return drop;
                }
            };

    private record HaloKey(float width, float height, int tint, float density) {}

    /**
     * cacheKey: kind, strip origin/width/height, radius, theme tint (the
     * theme's ARGB as passed by the caller), above, and UI density. The origin
     * is part of the key because the recorded commands use absolute canvas
     * coordinates; two same-sized strips at different positions must not share
     * a Picture. 9 alphas are baked into the recorded commands, not the key.
     */
    private record Key(int kind, float x, float y, float w, float h, float radius,
                       int themeTint, boolean above, float density) { }

    static boolean dotsCached() { return cached; }
    static void setDotsCached(boolean value) { cached = value; }
    static int cacheSize() { return CACHE.size() + HALO_IMAGES.size(); }
    static long cacheBuildCount() { return buildCount; }
    static long cacheHitCount() { return hitCount; }
    static void clearCache() {
        for (Picture picture : CACHE.values()) picture.close();
        CACHE.clear();
        for (Image image : HALO_IMAGES.values()) image.close();
        HALO_IMAGES.clear();
    }

    private static Picture picture(Key key, Consumer<Canvas> draw) {
        Picture existing = CACHE.get(key);
        if (existing != null) {
            hitCount++;
            return existing;
        }
        PictureRecorder recorder = new PictureRecorder();
        try {
            Canvas recording = recorder.beginRecording(CULL_RECT);
            draw.accept(recording);
            Picture created = recorder.finishRecordingAsPicture();
            CACHE.put(key, created);
            buildCount++;
            return created;
        } finally {
            recorder.close();
        }
    }

    private DotTexture() { }

    /** The bar's own rounded outline is also the origin of every dot column. */
    public static void drawEdge(Canvas canvas, UiLayout.Rect bar, float radius, boolean above, int tint) {
        drawEdge(canvas, bar, radius, above, tint, true);
    }

    /** Changing bar geometry uses the identical primitives without allocating transient Pictures. */
    public static void drawMovingEdge(Canvas canvas, UiLayout.Rect bar, float radius, boolean above, int tint) {
        drawEdge(canvas, bar, radius, above, tint, false);
    }

    private static void drawEdge(Canvas canvas, UiLayout.Rect bar, float radius, boolean above, int tint, boolean useCache) {
        if (!visible(bar, tint)) return;
        long segmentId = com.atom.chat.diagnostics.FrameSegments.current(), started = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
        try {
            float r = Math.max(0F, Math.min(radius, Math.min(bar.w(), bar.h()) / 2F));
            RRect outline = RRect.makeXYWH(bar.x(), bar.y(), bar.w(), bar.h(), r);
            if (cached && useCache) {
                Key key = new Key(KIND_EDGE, bar.x(), bar.y(), bar.w(), bar.h(), radius, tint, above, UiTokens.SCALE);
                Picture picture = picture(key, c -> edgeBody(c, bar, r, outline, above, tint));
                canvas.save();
                try {
                    edgeClips(canvas, bar, r, outline, above);
                    canvas.drawPicture(picture);
                } finally {
                    canvas.restore();
                }
                return;
            }
            canvas.save();
            try {
                edgeClips(canvas, bar, r, outline, above);
                edgeBody(canvas, bar, r, outline, above, tint);
            } finally {
                canvas.restore();
            }
        } finally {
            com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.DOTS, started);
        }
    }

    private static void edgeClips(Canvas canvas, UiLayout.Rect bar, float r, RRect outline, boolean above) {
        float depth = UiTokens.MESSAGE_DOT_DEPTH;
        canvas.clipRect(Rect.makeXYWH(bar.x(), above ? bar.y() - depth : bar.bottom() - r,
                bar.w(), depth + r));
        canvas.clipRRect(outline, ClipMode.DIFFERENCE, true);
    }

    private static void edgeBody(Canvas canvas, UiLayout.Rect bar, float r, RRect outline, boolean above, int tint) {
        float depth = UiTokens.MESSAGE_DOT_DEPTH;
        canvas.drawRRect(outline, GLOW.setMode(PaintMode.STROKE)
                .setStrokeWidth(UiTokens.DOT_GLOW_STROKE).setColor(tinted(tint, UiTokens.DOT_GLOW_ALPHA)));
        int columns = Math.min(512, (int) Math.ceil(bar.w() / UiTokens.MESSAGE_DOT_GAP));
        for (int i = 0; i < columns; i++) {
            float cx = UiTokens.MESSAGE_DOT_INSET + i * UiTokens.MESSAGE_DOT_GAP;
            float dx = Math.max(0F, Math.max(r - cx, cx - (bar.w() - r)));
            float corner = r - (float) Math.sqrt(Math.max(0F, r * r - dx * dx));
            float edge = above ? bar.y() + corner : bar.bottom() - corner;
            float sideFade = Math.max(0F, Math.min(1F,
                    Math.min(cx, bar.w() - cx) / UiTokens.MESSAGE_DOT_EDGE_FADE));
            for (int row = 0; row < ALPHAS.length; row++) {
                float distance = depth - row * UiTokens.MESSAGE_DOT_GAP;
                circle(canvas, bar.x() + cx, edge + (above ? -distance : distance),
                        UiTokens.MESSAGE_DOT_RADII[row], tint, ALPHAS[row] * sideFade);
            }
        }
    }

    public static void drawHalo(Canvas canvas, UiLayout.Rect box, int tint) {
        drawField(canvas, box, tint, false);
    }

    public static void drawTrail(Canvas canvas, UiLayout.Rect box, int tint) {
        drawField(canvas, box, tint, true);
    }

    /** A short bottom-edge trail towards the key that opened a floating panel. */
    public static void drawConnector(Canvas canvas, UiLayout.Rect popup, float radius,
                                     UiLayout.Rect composer, UiLayout.Rect key, int tint) {
        float left = Math.max(popup.x(), key.x() - UiTokens.DOT_CONNECTOR_PAD);
        float right = Math.min(popup.right(), key.right() + UiTokens.DOT_CONNECTOR_PAD);
        float bottom = composer.y();
        if (right <= left || bottom <= popup.bottom()) return;
        canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(left, popup.bottom(), right-left, bottom-popup.bottom()));
            drawEdge(canvas, popup, radius, false, tint);
        } finally { canvas.restore(); }
    }

    private static void drawField(Canvas canvas, UiLayout.Rect box, int tint, boolean trail) {
        if (!visible(box, tint)) return;
        long segmentId = com.atom.chat.diagnostics.FrameSegments.current(), started = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
        try {
            if (cached) {
                if (!trail && box.w() * box.h() >= HALO_IMAGE_MIN_AREA) {
                    Image image = haloImage(box, tint);
                    if (image != null) {
                        canvas.save();
                        try {
                            canvas.clipRect(Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()));
                            canvas.drawImageRect(image,
                                    Rect.makeWH(image.getWidth(), image.getHeight()),
                                    Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()),
                                    SamplingMode.LINEAR, null, false);
                        } finally {
                            canvas.restore();
                        }
                        return;
                    }
                }
                int kind = trail ? KIND_TRAIL : KIND_HALO;
                Key key = new Key(kind, box.x(), box.y(), box.w(), box.h(), 0F, tint, false, UiTokens.SCALE);
                Picture picture = picture(key, c -> fieldBody(c, box, tint, trail));
                canvas.save();
                try {
                    canvas.clipRect(Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()));
                    canvas.drawPicture(picture);
                } finally {
                    canvas.restore();
                }
                return;
            }
            canvas.save();
            try {
                canvas.clipRect(Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()));
                fieldBody(canvas, box, tint, trail);
            } finally {
                canvas.restore();
            }
        } finally {
            com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.DOTS, started);
        }
    }

    private static Image haloImage(UiLayout.Rect box, int tint) {
        HaloKey key = new HaloKey(box.w(), box.h(), tint, UiTokens.SCALE);
        Image existing = HALO_IMAGES.get(key);
        if (existing != null) {
            hitCount++;
            return existing;
        }
        int width = Math.max(1, (int) Math.ceil(box.w() * HALO_SUPERSAMPLE));
        int height = Math.max(1, (int) Math.ceil(box.h() * HALO_SUPERSAMPLE));
        long bytes = (long) width * height * 4L;
        if (bytes > MAX_HALO_BYTES) return null;
        Surface surface = null;
        try {
            surface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));
            Canvas raster = surface.getCanvas();
            raster.clear(0);
            raster.scale(HALO_SUPERSAMPLE, HALO_SUPERSAMPLE);
            raster.translate(-box.x(), -box.y());
            fieldBody(raster, box, tint, false);
            Image created = surface.makeImageSnapshot();
            java.util.Iterator<Map.Entry<HaloKey, Image>> eldest = HALO_IMAGES.entrySet().iterator();
            while (eldest.hasNext() && haloBytes() + bytes > MAX_HALO_BYTES) {
                eldest.next().getValue().close();
                eldest.remove();
            }
            HALO_IMAGES.put(key, created);
            buildCount++;
            return created;
        } catch (RuntimeException failure) {
            return null;
        } finally {
            if (surface != null) surface.close();
        }
    }

    private static long haloBytes() {
        long bytes = 0L;
        for (Image image : HALO_IMAGES.values()) {
            bytes += (long) image.getWidth() * image.getHeight() * 4L;
        }
        return bytes;
    }

    private static void fieldBody(Canvas canvas, UiLayout.Rect box, int tint, boolean trail) {
        GLOW.setMode(PaintMode.FILL).setColor(tinted(tint, UiTokens.DOT_FIELD_GLOW_ALPHA));
        float glowW = box.w() * (trail ? 0.65F : 0.7F);
        canvas.drawOval(Rect.makeXYWH(box.x() + (trail ? 0F : (box.w() - glowW) / 2F),
                box.y() + box.h() * 0.3F, glowW, box.h() * 0.4F), GLOW);
        int columns = Math.min(512, (int) Math.ceil(box.w() / UiTokens.MESSAGE_DOT_GAP));
        int rows = Math.min(128, (int) Math.ceil(box.h() / UiTokens.MESSAGE_DOT_GAP));
        for (int i = 0; i < columns; i++) {
            float cx = UiTokens.MESSAGE_DOT_INSET + i * UiTokens.MESSAGE_DOT_GAP;
            float u = cx / box.w();
            for (int j = 0; j < rows; j++) {
                float cy = UiTokens.MESSAGE_DOT_INSET + j * UiTokens.MESSAGE_DOT_GAP;
                float v = (cy / box.h() - 0.5F) * 2F;
                float falloff = trail
                        ? (1F - u) * Math.max(0F, 1F - v * v / Math.max(0.01F, 1F - u * 0.75F))
                        : 1F - (u * 2F - 1F) * (u * 2F - 1F) - v * v;
                if (falloff <= 0F) continue;
                circle(canvas, box.x() + cx, box.y() + cy,
                        UiTokens.MESSAGE_DOT_RADII[8] * (0.35F + 0.65F * falloff),
                        tint, UiTokens.DOT_FIELD_ALPHA * falloff);
            }
        }
    }

    private static boolean visible(UiLayout.Rect rect, int tint) {
        return rect != null && rect.w() > 0F && rect.h() > 0F && (tint >>> 24) > 0;
    }

    private static int tinted(int tint, float alpha) {
        return (tint & 0xFFFFFF) | (Math.round(alpha * (tint >>> 24) / 255F) << 24);
    }

    private static void circle(Canvas canvas, float x, float y, float radius, int tint, float alpha) {
        canvas.drawCircle(x, y, radius, DOT.setColor(tinted(tint, alpha)));
    }
}
