package com.atom.chat.render;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;

/** Device pixel area for the measured continuous and single-tip shapes. Render thread only. */
final class RoundedCoverage {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("atomchat.localEdgeCoverage", "true"));
    private static final int MAX_SHADERS = 128;
    private static final float MIN_CORNER_PIXELS = 3F;
    private static final String SKSL = """
            uniform float2 size;
            uniform float2 pixel;
            // TL, TR, BR, BL. Positive reaches use the continuous curve.
            uniform float4 radii;
            uniform float stroke;
            layout(color) uniform float3 tint;

            const float cut = %s;
            const float diagonal = 1.0 - cut;
            const float first = 1.0 - %s;
            const float second = 1.0 - 2.0 * cut;
            const float a = 3.0 * first;
            const float b = 3.0 * (second - 2.0 * first);
            const float c = diagonal - a - b;

            float pixelArea(float field, float2 slope) {
                float larger = max(slope.x, slope.y);
                float smaller = min(slope.x, slope.y);
                float tail = max(0.0, 0.5 * (larger + smaller) - abs(field));
                float area = tail < smaller
                    ? tail * tail / (2.0 * larger * smaller)
                    : (tail - 0.5 * smaller) / max(larger, 0.000001);
                return field > 0.0 ? area : 1.0 - area;
            }

            float rectangleArea(float2 edge, float inset) {
                float2 f = clamp(0.5 - (edge + inset) / pixel, 0.0, 1.0);
                return f.x * f.y;
            }

            half4 main(float2 p) {
                bool right = p.x > size.x * 0.5;
                float upper = right ? radii.y : radii.x;
                float lower = right ? radii.z : radii.w;
                bool top = p.y <= size.y * 0.5;
                float r = top ? upper : lower;
                float reach = abs(r);
                float2 edge = float2(right ? p.x - size.x : -p.x, top ? -p.y : p.y - size.y);
                float coverage;
                if (reach == 0.0 || min(edge.x, edge.y) <= -reach) {
                    coverage = rectangleArea(edge, -stroke);
                    if (stroke > 0.0) coverage -= rectangleArea(edge, stroke);
                } else {
                    float2 q = (edge + reach) / reach;
                    float2 gradient;
                    float field;
                    if (r > 0.0) {
                        // Invert the original half-cubic on its monotonic axis.
                        // Its other coordinate is 1 - cut * t^3.
                        float lesser = min(q.x, q.y);
                        float along = min(lesser, diagonal);
                        float t = along / diagonal;
                        for (int i = 0; i < 3; ++i) {
                            float value = t * (a + t * (b + t * c));
                            t -= (value - along) / (a + t * (2.0 * b + t * 3.0 * c));
                        }
                        float slope = 3.0 * cut * t * t / (a + t * (2.0 * b + t * 3.0 * c));
                        field = max(q.x, q.y) - (1.0 - cut * t * t * t) + (lesser - along) * slope;
                        gradient = (q.x < q.y ? float2(slope, 1.0) : float2(1.0, slope)) / reach;
                    } else {
                        float len = length(q);
                        field = (len - 1.0) * reach;
                        gradient = q / max(len, 0.000001);
                    }
                    float margin = length(gradient) * stroke;
                    coverage = pixelArea(field - margin, gradient * pixel);
                    if (stroke > 0.0) coverage -= pixelArea(field + margin, gradient * pixel);
                }
                return half4(tint * coverage, coverage);
            }
            """.formatted(CornerGeometry.DIAGONAL, CornerGeometry.STRAIGHT_HANDLE);
    private static final LinkedHashMap<Key, Shader> SHADERS = new LinkedHashMap<>(32, .75F, true);
    private static final ByteBuffer UNIFORMS = ByteBuffer.allocate(12 * Float.BYTES).order(ByteOrder.nativeOrder());
    private static final Shader[] NO_CHILDREN = {};
    private static final Paint SOLID = new Paint().setAntiAlias(false);
    private static final Paint MASK = new Paint().setAntiAlias(false).setBlendMode(BlendMode.DST_IN);
    private static RuntimeEffect effect;
    private static boolean unavailable;

    private RoundedCoverage() { }

    static boolean fill(Canvas canvas, RRect rect, int argb) {
        Prepared prepared = prepare(canvas, rect, 0F, argb & 0xFFFFFF, false, false);
        if (prepared == null) return false;
        int save = canvas.save();
        try {
            canvas.translate(rect.getLeft(), rect.getTop());
            canvas.drawRect(prepared.localBounds(), SOLID.setShader(prepared.shader()).setAlpha(argb >>> 24));
        } finally {
            SOLID.setShader(null);
            canvas.restoreToCount(save);
        }
        return true;
    }

    static boolean draw(Canvas canvas, RRect rect, Paint paint, boolean circularCoverage) {
        if (!ENABLED || unavailable || !paint.isAntiAlias() || paint.getBlendMode() != BlendMode.SRC_OVER) return false;
        PaintMode mode = paint.getMode();
        float halfStroke = mode == PaintMode.STROKE ? paint.getStrokeWidth() * .5F : 0F;
        if (mode != PaintMode.FILL && (mode != PaintMode.STROKE || halfStroke <= 0F)) return false;
        try (var mask = paint.getMaskFilter(); var image = paint.getImageFilter(); var path = paint.getPathEffect()) {
            if (mask != null || image != null || path != null) return false;
        }
        try (Shader material = paint.getShader()) {
            if (material == null && halfStroke == 0F) {
                try (var filter = paint.getColorFilter()) {
                    if (filter == null) return fill(canvas, rect, paint.getColor());
                }
            }
            var prepared = prepare(canvas, rect, halfStroke,
                    material == null ? paint.getColor() & 0xFFFFFF : 0xFFFFFF, false, circularCoverage);
            if (prepared == null) return false;
            float x = rect.getLeft(), y = rect.getTop();
            try (var positioned = prepared.shader().makeWithLocalMatrix(Matrix33.makeTranslate(x, y));
                 var ink = material == null ? null : Shader.makeBlend(BlendMode.DST_IN, material, positioned);
                 var antialiased = paint.makeClone()) {
                antialiased.setShader(ink == null ? positioned : ink).setMode(PaintMode.FILL).setAntiAlias(false);
                canvas.drawRect(prepared.localBounds().offset(x, y), antialiased);
            }
            return true;
        }
    }

    static boolean clip(Canvas canvas, RRect rect, Paint layerPaint, Runnable draw) {
        var prepared = prepare(canvas, rect, 0F, 0xFFFFFF, true, false);
        if (prepared == null) return false;
        float x = rect.getLeft(), y = rect.getTop();
        Rect layer = prepared.localBounds().offset(x, y);
        int save = canvas.save();
        try (var positioned = prepared.shader().makeWithLocalMatrix(Matrix33.makeTranslate(x, y))) {
            // Bound the temporary surface to the affected contour. The coverage
            // mask intersects the entire layer, including the four outer corners.
            canvas.clipRect(layer, false);
            canvas.saveLayer(layer, layerPaint);
            int contained = canvas.save();
            try {
                draw.run();
            } finally {
                canvas.restoreToCount(contained);
            }
            canvas.drawPaint(MASK.setShader(positioned));
        } finally {
            MASK.setShader(null);
            canvas.restoreToCount(save);
        }
        return true;
    }

    private static Prepared prepare(Canvas canvas, RRect rect, float halfStroke, int rgb,
                                    boolean rasterOnly, boolean circularCoverage) {
        if (!ENABLED || unavailable || !CornerGeometry.ANTIALIAS) return null;
        var corners = CornerGeometry.coverageCorners(rect);
        if (corners == null && circularCoverage) corners = CornerGeometry.circularCorners(rect);
        if (corners == null) return null;
        var grid = EdgePixelGrid.from(canvas, rasterOnly);
        if (grid == null || corners.minNonzeroRadius() * Math.min(grid.sx(), grid.sy()) < MIN_CORNER_PIXELS
                || halfStroke > corners.minNonzeroRadius() / 4F) return null;
        float sx = grid.sx(), sy = grid.sy();
        float pixelX = 1F / sx, pixelY = 1F / sy;
        var key = new Key(rect.getWidth(), rect.getHeight(), pixelX, pixelY, corners, halfStroke, rgb);
        Shader shader = SHADERS.get(key);
        if (shader == null) {
            try {
                shader = makeShader(key);
            } catch (RuntimeException | LinkageError failure) {
                unavailable = true;
                AtomChat.LOGGER.warn("Using Skija coverage for rounded edges", failure);
                return null;
            }
            if (SHADERS.size() == MAX_SHADERS) {
                var oldest = SHADERS.values().iterator();
                oldest.next().close();
                oldest.remove();
            }
            SHADERS.put(key, shader);
        }
        return new Prepared(shader, Rect.makeLTRB(-pixelX - halfStroke, -pixelY - halfStroke,
                key.width() + pixelX + halfStroke, key.height() + pixelY + halfStroke));
    }

    private static Shader makeShader(Key key) {
        if (effect == null) effect = RuntimeEffect.makeForShader(SKSL);
        var c = key.corners();
        int rgb = key.rgb();
        UNIFORMS.clear();
        UNIFORMS.putFloat(key.width()).putFloat(key.height()).putFloat(key.pixelX()).putFloat(key.pixelY());
        UNIFORMS.putFloat(c.tl()).putFloat(c.tr()).putFloat(c.br()).putFloat(c.bl());
        UNIFORMS.putFloat(key.halfStroke());
        UNIFORMS.putFloat(((rgb >>> 16) & 255) / 255F).putFloat(((rgb >>> 8) & 255) / 255F).putFloat((rgb & 255) / 255F);
        try (var data = Data.makeFromBytes(UNIFORMS.array())) {
            return effect.makeShader(data, NO_CHILDREN);
        }
    }

    static void release() {
        SHADERS.values().forEach(Shader::close);
        SHADERS.clear();
    }

    private record Prepared(Shader shader, Rect localBounds) {}
    private record Key(float width, float height, float pixelX, float pixelY, CornerGeometry.Radii corners,
                       float halfStroke, int rgb) {}
}
