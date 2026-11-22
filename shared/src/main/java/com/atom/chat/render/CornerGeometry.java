package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;

import java.util.LinkedHashMap;

/** AtomChat surface contours. Only vector geometry is cached, on the render thread. */
public final class CornerGeometry {
    /** Optional native-resolution comparison of hard geometry edges. */
    public static final boolean ANTIALIAS = Boolean.parseBoolean(System.getProperty("atomchat.shapeAntialias", "true"));
    private static final int MAX_PATHS = 256;
    private static final float REACH_SCALE = 1.25F;
    static final float DIAGONAL = .224F;
    static final float STRAIGHT_HANDLE = .727F;
    private static final LinkedHashMap<RRect, Path> PATHS = new LinkedHashMap<>(64, .75F, true);
    private static final Paint FILL = new Paint().setAntiAlias(ANTIALIAS);
    private static final Paint EDGE = new Paint().setAntiAlias(ANTIALIAS).setMode(PaintMode.STROKE);

    private CornerGeometry() { }

    public static RRect circularRect(float x, float y, float width, float height, float radius) {
        float fitted = Math.max(0F, Math.min(radius, Math.min(width, height) / 2F));
        return new CircularRect(x, y, x + width, y + height, new float[]{fitted});
    }

    static boolean usesCircularArcs(RRect rect) {
        return rect instanceof CircularRect;
    }

    /** Keep explicitly circular contours circular when an annotation moves or scales them. */
    private static final class CircularRect extends RRect {
        private CircularRect(float left, float top, float right, float bottom, float[] radii) {
            super(left, top, right, bottom, radii);
        }

        @Override public RRect offset(float dx, float dy) {
            return new CircularRect(getLeft() + dx, getTop() + dy, getRight() + dx, getBottom() + dy, _radii);
        }

        @Override public RRect scale(float sx, float sy) {
            float ry = _radii.length == 1 ? _radii[0] : _radii[1];
            return new CircularRect(getLeft() * sx, getTop() * sy, getRight() * sx, getBottom() * sy,
                    new float[]{_radii[0] * Math.abs(sx), ry * Math.abs(sy)});
        }
    }

    /** Zero selects Skia's native square, ellipse or capsule. Also used for the GL blur mask. */
    public static float continuousReach(float radius, float width, float height) {
        float half = Math.min(width, height) / 2F;
        return radius < 1F || radius >= half - .001F ? 0F : Math.min(maxReach(radius), half);
    }

    static float maxReach(float radius) {
        return radius * REACH_SCALE;
    }

    /** Fit the entire slider range to a short bar before deriving the continuous corner. */
    static float fitRadius(float requested, float maximum, float width, float height) {
        if (maximum <= 0F) return 0F;
        float halfSide = Math.max(0F, Math.min(width, height) / 2F);
        return requested * Math.min(1F, halfSide / maxReach(maximum));
    }

    /** Negative radii encode a continuous corner for the framebuffer shader. */
    public static float maskRadius(float radius, float width, float height) {
        float reach = continuousReach(radius, width, height);
        return reach > 0F ? -reach : Math.min(radius, Math.min(width, height) / 2F);
    }

    public static void drawRRect(Canvas canvas, RRect rect, Paint paint) {
        drawRRect(canvas, rect, paint, false);
    }

    static void drawRRect(Canvas canvas, RRect rect, Paint paint, boolean circularCoverage) {
        if (RoundedCoverage.draw(canvas, rect, paint, circularCoverage)) return;
        drawNative(canvas, rect, paint);
    }

    private static void drawNative(Canvas canvas, RRect rect, Paint paint) {
        Path path = contour(rect);
        if (path == null) canvas.drawRRect(rect, paint);
        else canvas.drawPath(path, paint);
    }

    public static void clipRRect(Canvas canvas, RRect rect, ClipMode mode, boolean aa) {
        Path path = contour(rect);
        if (path == null) canvas.clipRRect(rect, mode, aa && ANTIALIAS);
        else canvas.clipPath(path, mode, aa && ANTIALIAS);
    }

    public static void drawClipped(Canvas canvas, RRect rect, Runnable draw) {
        if (RoundedCoverage.clip(canvas, rect, null, draw)) return;
        int save = canvas.save();
        try {
            clipRRect(canvas, rect, true);
            draw.run();
        } finally {
            canvas.restoreToCount(save);
        }
    }

    public static void clipRRect(Canvas canvas, RRect rect, boolean aa) {
        clipRRect(canvas, rect, ClipMode.INTERSECT, aa);
    }

    public static void clipRRect(Canvas canvas, RRect rect) {
        clipRRect(canvas, rect, true);
    }

    public static void clip(Canvas canvas, float x, float y, float width, float height, float radius) {
        clipRRect(canvas, RRect.makeXYWH(x, y, width, height, radius), true);
    }

    public static void drawRoundedRect(Canvas canvas, float x, float y, float width, float height, float radius, int color) {
        fill(canvas, RRect.makeXYWH(x, y, width, height, radius), color);
    }

    public static void fill(Canvas canvas, RRect outline, int color) {
        if (!RoundedCoverage.fill(canvas, outline, color)) fillNative(canvas, outline, color);
    }

    public static void fillNative(Canvas canvas, RRect outline, int color) {
        drawNative(canvas, outline, FILL.setColor(color));
    }

    public static void drawEdgeHighlight(Canvas canvas, float x, float y, float width, float height,
                                         float radius, float stroke, int color) {
        float inset = stroke / 2F;
        drawRRect(canvas, RRect.makeXYWH(x + inset, y + inset, Math.max(0F, width - stroke),
                Math.max(0F, height - stroke), Math.max(0F, radius - inset)), EDGE.setColor(color).setStrokeWidth(stroke));
    }

    public static void drawVerticalGradient(Canvas canvas, float x, float y, float width, float height,
                                            float radius, int topColor, int bottomColor) {
        if (width <= 0F || height <= 0F) return;
        try (Shader shader = Shader.makeLinearGradient(x, y, x, y + height, new int[]{topColor, bottomColor}, new float[]{0F, 1F});
             Paint paint = new Paint().setAntiAlias(ANTIALIAS).setShader(shader)) {
            drawRRect(canvas, RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    /** A mirrored single-tip bubble has three equal circular corners and one square top corner. */
    static boolean isSingleTip(float tl, float tr, float br, float bl) {
        return br > 0F && br == bl && ((tl == 0F && tr == br) || (tr == 0F && tl == br));
    }

    record Radii(float tl, float tr, float br, float bl) {
        float minNonzeroRadius() {
            return Math.min(Math.min(nonzero(tl), nonzero(tr)), Math.min(nonzero(br), nonzero(bl)));
        }

        private static float nonzero(float radius) {
            return radius == 0F ? Float.POSITIVE_INFINITY : Math.abs(radius);
        }
    }

    // Positive reaches select the original continuous path. Negative radii
    // identify the three native circular arcs of a single-tip bubble.
    static Radii coverageCorners(RRect rect) {
        if (usesCircularArcs(rect)) return circularCorners(rect);
        float[] radii = rect._radii;
        float half = Math.min(rect.getWidth(), rect.getHeight()) / 2F;
        if (half <= 0F) return null;
        float tl = radii[0], tr = tl, br = tl, bl = tl;
        if (radii.length == 1 || radii.length == 2 && radii[0] == radii[1]) {
            float reach = continuousReach(tl, rect.getWidth(), rect.getHeight());
            if (reach == 0F) return null;
            tl = tr = br = bl = reach;
        } else if (radii.length == 8) {
            for (int i = 0; i < 8; i += 2) if (radii[i] != radii[i + 1]) return null;
            tr = radii[2]; br = radii[4]; bl = radii[6];
            if (isSingleTip(tl, tr, br, bl)) return new Radii(-tl, -tr, -br, -bl);
            if (Math.max(Math.max(tl, tr), Math.max(br, bl)) < 1F
                    || Math.min(Math.min(tl, tr), Math.min(br, bl)) >= half - .001F) return null;
            tl = Math.min(tl * REACH_SCALE, half);
            tr = Math.min(tr * REACH_SCALE, half);
            br = Math.min(br * REACH_SCALE, half);
            bl = Math.min(bl * REACH_SCALE, half);
        } else {
            return null;
        }
        return new Radii(tl, tr, br, bl);
    }

    static Radii circularCorners(RRect rect) {
        var radii = rect._radii;
        float radius = radii[0];
        if (radius <= 0F || Math.min(rect.getWidth(), rect.getHeight()) <= 0F) return null;
        for (float value : radii) if (value != radius) return null;
        float fitted = Math.min(radius, Math.min(rect.getWidth(), rect.getHeight()) / 2F);
        return new Radii(-fitted, -fitted, -fitted, -fitted);
    }

    private static Path contour(RRect rect) {
        if (usesCircularArcs(rect)) return null;
        Radii corners = coverageCorners(rect);
        if (corners == null || corners.br() < 0F) return null;
        Path path = PATHS.get(rect);
        if (path != null) return path;
        float left = rect.getLeft(), top = rect.getTop(), right = rect.getRight(), bottom = rect.getBottom();
        try (PathBuilder builder = new PathBuilder()) {
            builder.moveTo(left + corners.tl(), top);
            corner(builder, right, top, 1, 0, 0, 1, corners.tr());
            corner(builder, right, bottom, 0, 1, -1, 0, corners.br());
            corner(builder, left, bottom, -1, 0, 0, -1, corners.bl());
            corner(builder, left, top, 0, -1, 1, 0, corners.tl());
            path = builder.closePath().detach();
        }
        if (PATHS.size() == MAX_PATHS) {
            var iterator = PATHS.values().iterator();
            iterator.next().close();
            iterator.remove();
        }
        PATHS.put(rect, path);
        return path;
    }

    private static void corner(PathBuilder builder, float x, float y,
                              int inX, int inY, int outX, int outY, float reach) {
        float ux = inX * reach, uy = inY * reach, vx = outX * reach, vy = outY * reach;
        builder.lineTo(x - ux, y - uy);
        if (reach <= 0F) return;
        // Both halves meet on the diagonal with the same tangent and curvature.
        // Two handles on each straight edge keep the join curvature at zero.
        float joinHandle = 2F * DIAGONAL;
        builder.cubicTo(x - STRAIGHT_HANDLE * ux, y - STRAIGHT_HANDLE * uy, x - joinHandle * ux, y - joinHandle * uy,
                x + DIAGONAL * (vx - ux), y + DIAGONAL * (vy - uy));
        builder.cubicTo(x + joinHandle * vx, y + joinHandle * vy, x + STRAIGHT_HANDLE * vx, y + STRAIGHT_HANDLE * vy,
                x + vx, y + vy);
    }

    static void release() {
        PATHS.values().forEach(Path::close);
        PATHS.clear();
        RoundedCoverage.release();
        OutlineIcons.release();
    }
}
