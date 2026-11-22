package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
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
    private static final Paint POINTS = new Paint().setAntiAlias(true).setStrokeCap(PaintStrokeCap.ROUND);
    private static final MaskFilter BLUR = MaskFilter.makeBlur(FilterBlurMode.NORMAL, UiTokens.DOT_GLOW_BLUR);
    private static final Paint GLOW = new Paint().setAntiAlias(true).setMaskFilter(BLUR);
    private static final Paint COVERAGE = new Paint();
    private static final int[] ALPHAS = {19, 26, 32, 38, 45, 50, 54, 59, 64};
    // The paints and these point buffers are used only on the render thread.
    private static final float[] EDGE_POINTS = new float[512 * 2];
    private static float[] edgeDrawPoints = new float[0];

    private static final int KIND_EDGE = 0, KIND_TRAIL = 1, KIND_HALO = 2, KIND_POPUP_EDGE = 3;
    private static final int MAX_CACHED = 64;
    private static final int MAX_HALO_IMAGES = 8;
    private static final long MAX_HALO_BYTES = 32L * 1024L * 1024L;
    private static final int HALO_SUPERSAMPLE = 4;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    /** Keep the previous oversized-field fallback; a cold bake stays below 512k logical pixels. */
    private static final int MAX_IMAGE_PIXELS = 512 * 1024;
    private static final float FIELD_IMAGE_MIN_AREA = 40_000.0F;
    private static final int MAX_PANEL_EDGE_MASKS = 8;
    private static final int MAX_PANEL_EDGE_PIXELS = 128 * 1024;
    private static final PanelEdgeKey[] PREVIOUS_PANEL_EDGE = new PanelEdgeKey[2];
    private static TitleProjection titleProjection;
    /** Covers every possible dot coordinate so the recorder never drops a command. */
    private static final Rect CULL_RECT = Rect.makeXYWH(-1_000_000F, -1_000_000F, 2_000_000F, 2_000_000F);

    /**
     * Small edges/fields retain exact Picture replay. Large halo and trail fields
     * share the established 4x raster bake and bounded image cache (8 entries / 32MiB).
     * This avoids thousands of circle submissions without changing the sampling
     * strategy across GUI density, content scale or entrance transforms.
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
    private static final LinkedHashMap<PanelEdgeKey, Image> PANEL_EDGES =
            new LinkedHashMap<>(4, .75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PanelEdgeKey, Image> eldest) {
                    boolean drop = size() > MAX_PANEL_EDGE_MASKS;
                    if (drop) eldest.getValue().close();
                    return drop;
                }
            };

    private record HaloKey(float width, float height, int tint, float density, boolean trail, int resolution) {}
    private record PanelEdgeKey(UiLayout.Rect bar, float radius, boolean above, int tint, Matrix33 matrix) {}
    private record TitleProjection(Canvas recording, Matrix33 playback) {}

    private record DotGrid(float scale, float xPhase, float yPhase) {
        private static DotGrid of(Matrix33 matrix) {
            float[] m = matrix._mat;
            float scale = m[0];
            if (!(scale > 0F && scale <= 2F) || m[4] != scale || m[1] != 0F || m[3] != 0F
                    || m[6] != 0F || m[7] != 0F || m[8] != 1F) return null;
            return new DotGrid(scale, phase(m[2]), phase(m[5]));
        }

        private static float phase(float value) {
            return value - (float) Math.floor(value);
        }

        private float center(float coordinate, float radius, float phase) {
            float pixel = coordinate * scale + phase;
            // Focus dots around one pixel across. Larger circles keep their full vector phase.
            float weight = Math.max(0F, Math.min(1F, 2F - 2F * radius * scale));
            return coordinate + ((float) Math.floor(pixel) + .5F - pixel) * weight / scale;
        }
    }

    /**
     * cacheKey: kind, strip origin/width/height, radius, theme tint (the
     * theme's ARGB as passed by the caller), above, and UI density. The origin
     * is part of the key because the recorded commands use absolute canvas
     * coordinates; two same-sized strips at different positions must not share
     * a Picture. 9 alphas are baked into the recorded commands, not the key.
     */
    private record Key(int kind, float x, float y, float w, float h, float radius,
                       int themeTint, boolean above, float density, DotGrid grid) { }

    static boolean dotsCached() { return cached; }
    static void setDotsCached(boolean value) { cached = value; }
    static int cacheSize() { return CACHE.size() + HALO_IMAGES.size() + PANEL_EDGES.size(); }
    static long cacheBuildCount() { return buildCount; }
    static long cacheHitCount() { return hitCount; }
    static void clearCache() {
        for (Picture picture : CACHE.values()) picture.close();
        CACHE.clear();
        for (Image image : HALO_IMAGES.values()) image.close();
        HALO_IMAGES.clear();
        clearPanelEdges();
    }

    static void clearPanelEdges() {
        for (Image image : PANEL_EDGES.values()) image.close();
        PANEL_EDGES.clear();
        java.util.Arrays.fill(PREVIOUS_PANEL_EDGE, null);
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
        drawEdge(canvas, bar, radius, above, tint, true, false, true);
    }

    /** The live panel can reuse its native-pixel decoration once its bars have settled. */
    public static void drawPanelEdge(Canvas canvas, UiLayout.Rect bar, float radius, boolean above, int tint, boolean settled) {
        if (!settled) invalidatePanelEdgePose(above);
        drawEdge(canvas, bar, radius, above, tint, true, settled, true);
    }

    /** Motion or a hidden bar breaks the cold mask's repeated-pose check. */
    public static void invalidatePanelEdgePose(boolean above) {
        PREVIOUS_PANEL_EDGE[above ? 1 : 0] = null;
    }

    /** Changing bar geometry uses the identical primitives without allocating transient Pictures. */
    public static void drawMovingEdge(Canvas canvas, UiLayout.Rect bar, float radius, boolean above, int tint) {
        invalidatePanelEdgePose(above);
        drawEdge(canvas, bar, radius, above, tint, false, false, true);
    }

    private static void drawEdge(Canvas canvas, UiLayout.Rect bar, float radius, boolean above, int tint,
                                 boolean useCache, boolean settled, boolean circular) {
        if (!SoftShadow.enabled() || !visible(bar, tint)) return;
        long segmentId = com.atom.chat.diagnostics.FrameSegments.current(), started = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
        try {
            float r = Math.max(0F, Math.min(radius, Math.min(bar.w(), bar.h()) / 2F));
            RRect outline = circular ? CornerGeometry.circularRect(bar.x(), bar.y(), bar.w(), bar.h(), r)
                    : RRect.makeXYWH(bar.x(), bar.y(), bar.w(), bar.h(), r);
            if (cached && settled && drawPanelMask(canvas, bar, r, outline, above, tint)) return;
            if (cached && useCache) {
                int kind = circular ? KIND_EDGE : KIND_POPUP_EDGE;
                Key key = new Key(kind, bar.x(), bar.y(), bar.w(), bar.h(), radius, tint, above, UiTokens.SCALE, null);
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
        canvas.clipRect(Rect.makeXYWH(bar.x(), bandTop(bar, r, above),
                bar.w(), depth + r));
        CornerGeometry.clipRRect(canvas, outline, ClipMode.DIFFERENCE, true);
    }

    private static float bandTop(UiLayout.Rect bar, float r, boolean above) {
        return above ? bar.y() - UiTokens.MESSAGE_DOT_DEPTH : bar.bottom() - r;
    }

    private static boolean drawPanelMask(Canvas canvas, UiLayout.Rect bar, float r, RRect outline,
                                         boolean above, int tint) {
        Matrix33 matrix = canvas.getLocalToDeviceAsMatrix33();
        DotGrid grid = DotGrid.of(matrix);
        if (grid == null) return false;
        float[] m = matrix._mat;
        float scale = grid.scale();
        float top = bandTop(bar, r, above);
        int x = (int) Math.floor(bar.x() * scale + m[2]);
        int y = (int) Math.floor(top * scale + m[5]);
        int width = (int) Math.ceil(bar.right() * scale + m[2]) - x;
        int height = (int) Math.ceil((top + UiTokens.MESSAGE_DOT_DEPTH + r) * scale + m[5]) - y;
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PANEL_EDGE_PIXELS) return false;
        var key = new PanelEdgeKey(bar, r, above, tint, matrix);
        Image image = PANEL_EDGES.get(key);
        int side = above ? 1 : 0;
        PanelEdgeKey previous = PREVIOUS_PANEL_EDGE[side];
        PREVIOUS_PANEL_EDGE[side] = key;
        if (image == null) {
            if (!key.equals(previous)) return false;
            try (Surface surface = Surface.makeRaster(ImageInfo.makeA8(width, height))) {
                Canvas bake = surface.getCanvas();
                bake.clear(0);
                bake.translate(-x, -y);
                bake.concat(matrix);
                edgeClips(bake, bar, r, outline, above);
                edgeGlow(bake, outline, tint);
                edgePoints(bake, bar, r, above, tint, grid);
                image = surface.makeImageSnapshot();
                PANEL_EDGES.put(key, image);
                buildCount++;
            }
        } else {
            hitCount++;
        }
        int save = canvas.save();
        try {
            canvas.resetMatrix();
            // The original alpha is already baked into the coverage; apply only the tint RGB.
            canvas.drawImage(image, x, y, COVERAGE.setColor(tint | 0xFF000000));
        } finally {
            canvas.restoreToCount(save);
        }
        return true;
    }

    private static void edgeGlow(Canvas canvas, RRect outline, int tint) {
        CornerGeometry.drawRRect(canvas, outline, GLOW.setMode(PaintMode.STROKE)
                .setStrokeWidth(UiTokens.DOT_GLOW_STROKE).setColor(tinted(tint, UiTokens.DOT_GLOW_ALPHA)));
    }

    private static void edgeBody(Canvas canvas, UiLayout.Rect bar, float r, RRect outline, boolean above, int tint) {
        float depth = UiTokens.MESSAGE_DOT_DEPTH;
        edgeGlow(canvas, outline, tint);
        if (cached) {
            edgePoints(canvas, bar, r, above, tint, null);
            return;
        }
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

    /** Round points use the same circle primitive; one submission per full-opacity row. */
    private static void edgePoints(Canvas canvas, UiLayout.Rect bar, float r, boolean above, int tint, DotGrid grid) {
        int columns=Math.min(512,(int)Math.ceil(bar.w()/UiTokens.MESSAGE_DOT_GAP));
        float[] points = EDGE_POINTS;
        for(int row=0;row<ALPHAS.length;row++) {
            int count=0;
            float radius=UiTokens.MESSAGE_DOT_RADII[row];
            float distance=UiTokens.MESSAGE_DOT_DEPTH-row*UiTokens.MESSAGE_DOT_GAP;
            for(int col=0;col<columns;col++) {
                float cx=UiTokens.MESSAGE_DOT_INSET+col*UiTokens.MESSAGE_DOT_GAP;
                float dx=Math.max(0F,Math.max(r-cx,cx-(bar.w()-r)));
                float corner=r-(float)Math.sqrt(Math.max(0F,r*r-dx*dx));
                float edge=above ? bar.y()+corner : bar.bottom()-corner;
                float fade=Math.max(0F,Math.min(1F,Math.min(cx,bar.w()-cx)/UiTokens.MESSAGE_DOT_EDGE_FADE));
                float x=bar.x()+cx,y=edge+(above ? -distance : distance);
                if (grid != null) {
                    x = grid.center(x, radius, grid.xPhase());
                    y = grid.center(y, radius, grid.yPhase());
                }
                if(fade==1F) {points[count++]=x;points[count++]=y;}
                else circle(canvas,x,y,radius,tint,ALPHAS[row]*fade);
            }
            if (count > 0) {
                if (edgeDrawPoints.length != count) edgeDrawPoints = new float[count];
                System.arraycopy(points, 0, edgeDrawPoints, 0, count);
                canvas.drawPoints(edgeDrawPoints,
                        POINTS.setStrokeWidth(radius*2F).setColor(tinted(tint,ALPHAS[row])));
            }
        }
    }

    public static void drawHalo(Canvas canvas, UiLayout.Rect box, int tint) {
        drawField(canvas, box, tint, false, null);
    }

    public static void drawTrail(Canvas canvas, UiLayout.Rect box, int tint) {
        drawField(canvas, box, tint, true, null);
    }

    /** A picture records in local units. Its settled replay supplies the real device phase. */
    public static void recordTitles(Canvas recording, Canvas playback, Runnable draw) {
        TitleProjection previous = titleProjection;
        titleProjection = new TitleProjection(recording, playback.getLocalToDeviceAsMatrix33());
        try {
            draw.run();
        } finally {
            titleProjection = previous;
        }
    }

    public static void drawTitleTrail(Canvas canvas, UiLayout.Rect box, int tint) {
        if (!visible(box, tint)) return;
        DotGrid grid = titleProjection != null && titleProjection.recording() == canvas
                && box.w() * box.h() < FIELD_IMAGE_MIN_AREA
                ? DotGrid.of(titleProjection.playback().makeConcat(canvas.getLocalToDeviceAsMatrix33())) : null;
        // Settings rows scroll vertically. Keep that axis continuous while aligning the columns.
        if (grid != null && grid.scale() == UiPixelSnap.density() && grid.xPhase() == 0F)
            grid = new DotGrid(grid.scale(), 0F, 0F);
        else grid = null;
        drawField(canvas, box, tint, true, grid);
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
            drawEdge(canvas, popup, radius, false, tint, true, false, false);
        } finally { canvas.restore(); }
    }

    private static void drawField(Canvas canvas, UiLayout.Rect box, int tint, boolean trail, DotGrid grid) {
        if (!SoftShadow.enabled() || !visible(box, tint)) return;
        long segmentId = com.atom.chat.diagnostics.FrameSegments.current(), started = com.atom.chat.diagnostics.FrameSegments.start(segmentId);
        try {
            if (cached) {
                boolean largeField = grid == null && box.w() * box.h() >= FIELD_IMAGE_MIN_AREA;
                if (largeField) {
                    Image image = fieldImage(box, tint, trail, HALO_SUPERSAMPLE);
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
                Key key = new Key(kind, box.x(), box.y(), box.w(), box.h(), 0F, tint, false, UiTokens.SCALE, grid);
                Picture picture = picture(key, c -> fieldBody(c, box, tint, trail, grid));
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
                fieldBody(canvas, box, tint, trail, grid);
            } finally {
                canvas.restore();
            }
        } finally {
            com.atom.chat.diagnostics.FrameSegments.end(segmentId, com.atom.chat.diagnostics.FrameSegments.Stage.DOTS, started);
        }
    }

    private static Image fieldImage(UiLayout.Rect box, int tint, boolean trail, int resolution) {
        HaloKey key = new HaloKey(box.w(), box.h(), tint, UiTokens.SCALE, trail, resolution);
        Image existing = HALO_IMAGES.get(key);
        if (existing != null) {
            hitCount++;
            return existing;
        }
        if ((double) box.w() * box.h() > MAX_IMAGE_PIXELS) return null;
        double pixelWidth = Math.ceil((double) box.w() * resolution);
        double pixelHeight = Math.ceil((double) box.h() * resolution);
        if (pixelWidth > MAX_IMAGE_DIMENSION || pixelHeight > MAX_IMAGE_DIMENSION) return null;
        int width = Math.max(1, (int) pixelWidth);
        int height = Math.max(1, (int) pixelHeight);
        long bytes = (long) width * height * 4L;
        if (bytes > MAX_HALO_BYTES) return null;
        Surface surface = null;
        try {
            surface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));
            Canvas raster = surface.getCanvas();
            raster.clear(0);
            raster.scale(resolution, resolution);
            fieldBody(raster, new UiLayout.Rect(0, 0, box.w(), box.h()), tint, trail, null);
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

    private static void fieldBody(Canvas canvas, UiLayout.Rect box, int tint, boolean trail, DotGrid grid) {
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
                float radius = UiTokens.MESSAGE_DOT_RADII[8] * (0.35F + 0.65F * falloff);
                float x = box.x() + cx;
                if (grid != null) x = grid.center(x, radius, grid.xPhase());
                circle(canvas, x, box.y() + cy, radius, tint, UiTokens.DOT_FIELD_ALPHA * falloff);
            }
        }
    }

    private static boolean visible(UiLayout.Rect rect, int tint) {
        return rect != null && Float.isFinite(rect.x()) && Float.isFinite(rect.y())
                && Float.isFinite(rect.w()) && Float.isFinite(rect.h())
                && rect.w() > 0F && rect.h() > 0F && (tint >>> 24) > 0;
    }

    private static int tinted(int tint, float alpha) {
        return (tint & 0xFFFFFF) | (Math.round(alpha * (tint >>> 24) / 255F) << 24);
    }

    private static void circle(Canvas canvas, float x, float y, float radius, int tint, float alpha) {
        canvas.drawCircle(x, y, radius, DOT.setColor(tinted(tint, alpha)));
    }
}
