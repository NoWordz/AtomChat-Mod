package com.atom.chat.avatar;

/**
 * Pure math for the image cropper: a fixed frame in the middle (circle for the
 * avatar, rounded rectangle for the wallpaper), the image pans underneath it
 * (drag) and zooms around the frame centre (wheel). The one hard rule is that
 * the image must always fully cover the frame — no transparent edges may peek
 * inside.
 *
 * <p>The frame is described by half-extents {@code rx}/{@code ry}; a circle is
 * the special case {@code rx == ry}.
 */
public final class CropMath {
    private CropMath() {
    }

    /** Smallest allowed scale: the image exactly covers the frame. */
    public static float baseScale(float imgW, float imgH, float frameW, float frameH) {
        return com.atom.lib.geometry.CropMath.baseScale(imgW, imgH, frameW, frameH);
    }

    /** Zoom is clamped to [base, base * 4]. */
    public static float clampScale(float scale, float base) {
        return com.atom.lib.geometry.CropMath.clampScale(scale, base);
    }

    /**
     * Clamps the image-centre offset so the scaled image keeps covering the
     * frame at ({@code cx},{@code cy}) with half-extents {@code rx}/{@code ry}.
     *
     * @return {@code {ox, oy}}
     */
    public static float[] clampCover(float imgW, float imgH, float scale,
                                     float cx, float cy, float rx, float ry,
                                     float ox, float oy) {
        return com.atom.lib.geometry.CropMath.clampCover(imgW, imgH, scale, cx, cy, rx, ry, ox, oy);
    }

    /**
     * Zoom keeping the image point currently under the frame centre pinned
     * there, then re-clamps the offset. Returns {@code {scale, ox, oy}}.
     */
    public static float[] zoomAtCentre(float imgW, float imgH, float scale,
                                       float cx, float cy, float rx, float ry,
                                       float ox, float oy, float factor, float base) {
        return com.atom.lib.geometry.CropMath.zoomAtCentre(imgW, imgH, scale, cx, cy, rx, ry, ox, oy, factor, base);
    }

    /**
     * Source-pixel rectangle that the frame sees. The image's left edge sits at
     * {@code ox - imgW*scale/2}, so the frame's left edge maps to
     * {@code (cx - rx - ox)/scale + imgW/2} in image pixels. Clamped to the
     * image bounds.
     *
     * @return {@code {sx, sy, sw, sh}}
     */
    public static float[] visibleRect(float imgW, float imgH, float scale,
                                      float cx, float cy, float rx, float ry,
                                      float ox, float oy) {
        return com.atom.lib.geometry.CropMath.visibleRect(imgW, imgH, scale, cx, cy, rx, ry, ox, oy);
    }
}
