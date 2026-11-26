package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.config.GraphicsQuality;
import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.IRect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

import java.util.LinkedHashMap;
import java.util.Map;

/** Panel/dialog Gaussian shadows and popup material for the jump button. Render-thread confined. */
public final class SoftShadow {
    private static final float ULTRA_DENSITY = 3F;
    private static final float BALANCED_DENSITY = 2F;
    private static final float LARGE_BALANCED_DENSITY = 1.5F;
    private static final int MAX_PATCHES = 12;
    private static final int MAX_SETTLED_SHADOW_PIXELS = 1024 * 1024;
    private static final int LARGE_SHADOW_DEVICE_PIXELS = MAX_SETTLED_SHADOW_PIXELS / 4;
    private static final float MAX_SETTLED_SHADOW_DENSITY = 2F;
    private static final Map<PanelPatchKey, PanelPatch> PANEL_PATCHES = new LinkedHashMap<>(16, .75F, true);
    private static DeviceShadowKey previousShadowKey;
    private static DeviceShadow settledShadow;

    record PanelPatchKey(float radius, float density) { }
    private record PanelPatch(Image image, IRect center, float density) { }
    private record DeviceShadowKey(RRect face, float width, float height, Matrix33 matrix, float density) { }
    private record DeviceShadow(DeviceShadowKey key, Image image) { }

    private SoftShadow() { }

    public static boolean enabled() {
        return !AtomChatConfig.performanceModeActive();
    }

    static int shadowColor(int argb) {
        if (AtomChatConfig.activeGraphicsQuality() != GraphicsQuality.BALANCED) return argb;
        return (argb & 0xFFFFFF) | (Math.round((argb >>> 24) * .8F) << 24);
    }

    public static void drawRounded(Canvas canvas, float x, float y, float width, float height,
                                   float radius, float blur, int color) {
        if (!enabled() || width <= 0F || height <= 0F || blur <= 0F || (color >>> 24) == 0) return;
        if (CornerGeometry.continuousReach(radius, width, height) == 0F) {
            SkiaDraw.drawRoundedShadow(canvas, x, y, width, height, radius, blur, shadowColor(color));
        } else {
            HudShadow.drawMaterialShade(canvas, x, y, width, height, radius, blur, color);
        }
    }

    /** Already scaled drawing units, including the near-transparent resampling fringe. */
    public static float panelPad() {
        return edgePad(ShadowTokens.PANEL);
    }

    public static void drawCard(Canvas canvas, float x, float y, float width, float height, float radius) {
        HudShadow.drawSurface(canvas, x, y, width, height, radius, ShadowTokens.CARD, 1F);
    }

    /** Includes the FAB's press-release overshoot, since its fade layer encloses the scale. */
    public static float jumpPad() {
        return ShadowTokens.FAB_PAD;
    }

    /** Padded layer reach for a floating surface's cached two-tier shadow. */
    public static float floatPad() {
        return ShadowTokens.FLOAT.pad();
    }

    /** Call before the background, outside its clip. Screen clipping leaves only exposed docked edges. */
    public static void drawPanel(Canvas canvas, float x, float y, float width, float height, float radius) {
        drawPanel(canvas, x, y, width, height, radius, false);
    }

    /** The settled live panel can reuse a native-resolution shadow without reapplying its clip. */
    public static void drawPanel(Canvas canvas, float x, float y, float width, float height, float radius, boolean settled) {
        float r = Math.max(0F, Math.min(radius, Math.min(width, height) / 2F));
        drawPanel(canvas, RRect.makeXYWH(x, y, width, height, r), settled);
    }

    public static void drawPanel(Canvas canvas, RRect face, boolean settled) {
        float width = face.getWidth(), height = face.getHeight();
        if (!enabled() || width <= 0 || height <= 0) return;
        float r = face._radii[0];
        boolean balanced = AtomChatConfig.activeGraphicsQuality() == GraphicsQuality.BALANCED;
        Matrix33 matrix = balanced || (settled && r > 0F) ? canvas.getLocalToDeviceAsMatrix33() : null;
        float density = balanced ? balancedDensity(matrix, width, height) : ULTRA_DENSITY;
        var key = new PanelPatchKey(r, density);
        PanelPatch patch = PANEL_PATCHES.get(key);
        if (patch == null) {
            patch = makePanelPatch(r, density);
            if (PANEL_PATCHES.size() >= MAX_PATCHES) {
                var oldest = PANEL_PATCHES.entrySet().iterator();
                oldest.next().getValue().image().close();
                oldest.remove();
            }
            PANEL_PATCHES.put(key, patch);
        }
        if (settled && r > 0F && drawSettledShadow(canvas, face, width, height, patch, matrix)) return;
        drawPanelPatch(canvas, face, width, height, patch);
    }

    private static float balancedDensity(Matrix33 matrix, float width, float height) {
        float[] m = matrix._mat;
        float scale = m[0];
        // The nine-slice stores only the soft tail. Keep the full patch for small
        // shadows and transforms outside the tested, axis-aligned 1–2× range.
        if (scale >= 1F && scale <= MAX_SETTLED_SHADOW_DENSITY && axisAlignedUniform(m)
                && (double) width * height * scale * scale >= LARGE_SHADOW_DEVICE_PIXELS) {
            return LARGE_BALANCED_DENSITY;
        }
        return BALANCED_DENSITY;
    }

    private static boolean axisAlignedUniform(float[] matrix) {
        return matrix[4] == matrix[0] && matrix[1] == 0F && matrix[3] == 0F
                && matrix[6] == 0F && matrix[7] == 0F && matrix[8] == 1F;
    }

    private static void drawPanelPatch(Canvas canvas, RRect face, float width, float height, PanelPatch patch) {
        canvas.save();
        try {
            // A translucent panel must show its wallpaper, never its own black shadow mask.
            canvas.clipRRect(face, ClipMode.DIFFERENCE, true);
            float pad = panelPad();
            canvas.translate(face.getLeft() - pad, face.getTop() - pad);
            float density = patch.density();
            canvas.scale(1F / density, 1F / density);
            canvas.drawImageNine(patch.image(), patch.center(),
                    Rect.makeWH((width + pad * 2F) * density, (height + pad * 2F) * density),
                    FilterMode.LINEAR, null);
        } finally {
            canvas.restore();
        }
    }

    private static boolean drawSettledShadow(Canvas canvas, RRect face, float faceWidth, float faceHeight,
                                             PanelPatch patch, Matrix33 matrix) {
        float[] m = matrix._mat;
        float scale = m[0];
        // Transforms and high-density coverage stay on the live clip.
        if (!(scale > 0F && scale <= MAX_SETTLED_SHADOW_DENSITY) || !axisAlignedUniform(m)) return false;
        float left = face.getLeft() * scale + m[2], top = face.getTop() * scale + m[5];
        float right = face.getRight() * scale + m[2], bottom = face.getBottom() * scale + m[5];
        if (!onPixelGrid(left) || !onPixelGrid(top) || !onPixelGrid(right) || !onPixelGrid(bottom)) return false;
        int pad = (int) Math.ceil(panelPad() * scale) + 1;
        int x = Math.round(left) - pad, y = Math.round(top) - pad;
        int width = Math.round(right) + pad - x, height = Math.round(bottom) + pad - y;
        if ((long) width * height > MAX_SETTLED_SHADOW_PIXELS) return false;

        var key = new DeviceShadowKey(face, faceWidth, faceHeight, matrix, patch.density());
        if (settledShadow == null || !settledShadow.key().equals(key)) {
            // A size or position in flight is cheaper to paint live than to upload repeatedly.
            if (!key.equals(previousShadowKey)) {
                previousShadowKey = key;
                return false;
            }
            // The panel layers are black. Cache only their coverage, at one texel per output pixel.
            try (Surface surface = Surface.makeRaster(ImageInfo.makeA8(width, height))) {
                Canvas bake = surface.getCanvas();
                bake.clear(0);
                bake.translate(-x, -y);
                bake.concat(matrix);
                drawPanelPatch(bake, face, faceWidth, faceHeight, patch);
                Image image = surface.makeImageSnapshot();
                if (settledShadow != null) settledShadow.image().close();
                settledShadow = new DeviceShadow(key, image);
            }
        }
        previousShadowKey = key;
        canvas.save();
        try {
            canvas.resetMatrix();
            canvas.drawImage(settledShadow.image(), x, y);
        } finally {
            canvas.restore();
        }
        return true;
    }

    private static boolean onPixelGrid(float coordinate) {
        return Math.abs(coordinate - Math.round(coordinate)) <= .001F;
    }

    /** Shares the accepted popup material, including its lit rim and shaded interior. */
    public static void drawJumpSurface(Canvas canvas, float x, float y, float size, float radius, int fillArgb) {
        if (size <= 0) return;
        float r = Math.max(0F, Math.min(radius, size / 2F));
        com.atom.chat.ui.MenuPopup.drawMaterial(canvas, x, y, size, size, r, fillArgb);
    }

    /** Compact floating cards share the dimension-aware outline and leave the face clear. */
    public static void drawFloating(Canvas canvas, float x, float y, float width, float height, float radius) {
        HudShadow.drawSurface(canvas, x, y, width, height, radius, ShadowTokens.FLOAT, 1F);
    }

    private static float edgePad(ShadowTokens.Face profile) {
        return (float) Math.ceil(profile.wideSigma() * 4F + profile.ambient().drop() + 2F);
    }

    private static PanelPatch makePanelPatch(float radius, float density) {
        var profile = ShadowTokens.PANEL;
        float pad = panelPad();
        // Three sigma between a corner and the stretch seam makes each edge effectively straight.
        int side = (int) Math.ceil(2F * (radius + profile.wideSigma() * 3F) + 2F);
        int pixels = (int) ((side + pad * 2F) * density);
        try (Surface surface = Surface.makeRasterN32Premul(pixels, pixels);
             Paint paint = new Paint().setAntiAlias(true)) {
            Canvas canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(density, density);
            var shape = RRect.makeXYWH(pad, pad, side, side, radius);
            HudShadow.paintLayer(canvas, shape, paint, profile.ambient(), true);
            HudShadow.paintLayer(canvas, shape, paint, profile.contact(), true);
            int seam = pixels / 2;
            return new PanelPatch(surface.makeImageSnapshot(), IRect.makeXYWH(seam, seam, 1, 1), density);
        }
    }

    /** Call alongside the renderer's other image-cache teardown, on its render thread. */
    public static void release() {
        if (settledShadow != null) {
            settledShadow.image().close();
            settledShadow = null;
        }
        previousShadowKey = null;
        DotTexture.clearPanelEdges();
        CornerGeometry.release();
        HudShadow.release();
        com.atom.chat.ui.ChatShortcutHint.release();
        PANEL_PATCHES.values().forEach(p -> p.image().close());
        PANEL_PATCHES.clear();
    }
}
