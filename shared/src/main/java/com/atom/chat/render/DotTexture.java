package com.atom.chat.render;

import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/** Small, static decorations; paints and the fixed blur filter live on the render thread. */
public final class DotTexture {
    private static final Paint DOT = new Paint().setAntiAlias(true);
    private static final MaskFilter BLUR = MaskFilter.makeBlur(FilterBlurMode.NORMAL, UiTokens.DOT_GLOW_BLUR);
    private static final Paint GLOW = new Paint().setAntiAlias(true).setMaskFilter(BLUR);
    private static final int[] ALPHAS = {12, 16, 20, 24, 28, 31, 34, 37, 40};

    private DotTexture() { }

    /** The bar's own rounded outline is also the origin of every dot column. */
    public static void drawEdge(Canvas canvas, UiLayout.Rect bar, float radius, boolean above, int tint) {
        if (!visible(bar, tint)) return;
        float r = Math.max(0F, Math.min(radius, Math.min(bar.w(), bar.h()) / 2F));
        float depth = UiTokens.MESSAGE_DOT_DEPTH;
        RRect outline = RRect.makeXYWH(bar.x(), bar.y(), bar.w(), bar.h(), r);
        canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(bar.x(), above ? bar.y() - depth : bar.bottom() - r,
                    bar.w(), depth + r));
            canvas.clipRRect(outline, ClipMode.DIFFERENCE, true);
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
        } finally {
            canvas.restore();
        }
    }

    public static void drawHalo(Canvas canvas, UiLayout.Rect box, int tint) {
        drawField(canvas, box, tint, false);
    }

    public static void drawTrail(Canvas canvas, UiLayout.Rect box, int tint) {
        drawField(canvas, box, tint, true);
    }

    private static void drawField(Canvas canvas, UiLayout.Rect box, int tint, boolean trail) {
        if (!visible(box, tint)) return;
        canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(box.x(), box.y(), box.w(), box.h()));
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
        } finally {
            canvas.restore();
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
