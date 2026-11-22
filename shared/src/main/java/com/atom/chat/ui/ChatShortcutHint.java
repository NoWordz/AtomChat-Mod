package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.CornerGeometry;
import com.atom.chat.render.OutlineIconBounds;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SoftShadow;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathFillMode;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;

/** The HUD hint and the panel use opposite ends of the same open progress. */
public final class ChatShortcutHint {
    private static final String SHELL=
            "M6 3H18C20.21 3 22 4.79 22 7V15C22 17.21 20.21 19 18 19H10L5 23V19"
                    +"C3.34 19 2 17.66 2 16V7C2 4.79 3.79 3 6 3Z";
    private static final Path SILHOUETTE=Path.makeFromSVGString(SHELL);
    private static final Path ICON=Path.makeFromSVGString(SHELL
                    +"M9.25 11A1.25 1.25 0 1 0 6.75 11A1.25 1.25 0 1 0 9.25 11Z"
                    +"M13.25 11A1.25 1.25 0 1 0 10.75 11A1.25 1.25 0 1 0 13.25 11Z"
                    +"M17.25 11A1.25 1.25 0 1 0 14.75 11A1.25 1.25 0 1 0 17.25 11Z")
            .setFillMode(PathFillMode.EVEN_ODD);
    private static final float ICON_FIT=LayoutTokens.HUD_ICON_GUI/OutlineIconBounds.of(ICON).getWidth();
    private static final ImageFilter ICON_SHADOW=ImageFilter.makeDropShadowOnly(0,
            LayoutTokens.HUD_SHADOW_DROP_GUI/ICON_FIT,LayoutTokens.HUD_SHADOW_BLUR_GUI/ICON_FIT,
            LayoutTokens.HUD_SHADOW_BLUR_GUI/ICON_FIT,LayoutTokens.HUD_SHADOW_COLOR);
    private static final ImageFilter TEXT_SHADOW=ImageFilter.makeDropShadowOnly(0,
            LayoutTokens.HUD_SHADOW_DROP_GUI,LayoutTokens.HUD_SHADOW_BLUR_GUI,
            LayoutTokens.HUD_SHADOW_BLUR_GUI,LayoutTokens.HUD_SHADOW_COLOR);
    private static Sprite sprite;
    private ChatShortcutHint() { }

    private record Sprite(Font font, String key, float density, boolean shadow, Image image) { }
    public record Frame(float alpha, float offsetX) { }

    public static Frame frame(float visibility) {
        float alpha = Math.max(0F, Math.min(1F, visibility));
        return new Frame(alpha, -LayoutTokens.HUD_SLIDE_GUI * (1F - alpha));
    }

    public static void renderGuiScaled(Canvas canvas,float width,float height,float canvasDensity,
                                       float guiScale,String key,float visibility) {
        float ratio=guiScale/canvasDensity;
        renderOnHud(canvas, height / ratio, ratio, guiScale, HudTransform.IDENTITY, key, visibility);
    }

    public static void renderOnHud(Canvas canvas, float guiHeight, float canvasUnitsPerGui, float guiScale,
                                   HudTransform transform, String key, float visibility) {
        if (transform == null || visibility <= 0F) return;
        int save=canvas.save();
        try {
            canvas.scale(canvasUnitsPerGui, canvasUnitsPerGui);
            transform.apply(canvas, 1F);
            paintSprite(canvas, guiHeight, guiScale, key, visibility);
        } finally { canvas.restoreToCount(save); }
    }

    public static void render(Canvas canvas, float width, float height, String key, float visibility) {
        paintSprite(canvas, height, 1F, key, visibility);
    }

    private static void paintSprite(Canvas canvas, float height, float density, String key, float visibility) {
        Frame frame = frame(visibility);
        if (frame.alpha() <= 0F || key == null || key.isBlank()) return;
        float margin = LayoutTokens.HUD_MARGIN_GUI, size = LayoutTokens.HUD_ICON_GUI;
        var font = FontManager.font(LayoutTokens.HUD_FONT_GUI);
        boolean shadow = SoftShadow.enabled();
        if (sprite == null || sprite.font() != font || sprite.density() != density
                || sprite.shadow() != shadow || !sprite.key().equals(key)) {
            release();
            sprite = bake(font, key, density, shadow);
        }
        Image image = sprite.image();
        float pad = LayoutTokens.HUD_SHADOW_PAD_GUI;
        Rect src = Rect.makeWH(image.getWidth(), image.getHeight());
        Rect dst = Rect.makeXYWH(margin + frame.offsetX() - pad, height - margin - size - pad,
                image.getWidth() / density, image.getHeight() / density);
        try (Paint alpha = frame.alpha() < 1F ? new Paint().setAlphaf(frame.alpha()) : null) {
            canvas.drawImageRect(image, src, dst, SamplingMode.LINEAR, alpha, false);
        }
    }

    private static Sprite bake(Font font, String key, float density, boolean softShadow) {
        float size = LayoutTokens.HUD_ICON_GUI;
        float pad = LayoutTokens.HUD_SHADOW_PAD_GUI;
        float x = pad, cy = pad + size / 2F;
        float textX = x + size + LayoutTokens.HUD_GAP_GUI;
        int width = (int) Math.ceil((textX + SkiaFontRenderer.getStringWidth(font, key) + pad) * density);
        int height = (int) Math.ceil((size + 2F * pad) * density);
        try (Surface surface = Surface.makeRasterN32Premul(width, height)) {
            var canvas = surface.getCanvas();
            canvas.clear(0);
            canvas.scale(density, density);
            Rect bounds=OutlineIconBounds.of(ICON);
            float scale=size/Math.max(bounds.getWidth(),bounds.getHeight());
            int iconSave=canvas.save();
            try(var ink=new Paint().setColor(0xFFFFFFFF).setAntiAlias(CornerGeometry.ANTIALIAS)) {
                canvas.translate(x+size/2F-(bounds.getLeft()+bounds.getRight())/2F*scale,
                        cy-(bounds.getTop()+bounds.getBottom())/2F*scale);
                canvas.scale(scale,scale);
                if (softShadow) {
                    int shadowSave=canvas.save();
                    try(var shadow=new Paint().setColor(0xFF000000).setAntiAlias(true).setImageFilter(ICON_SHADOW)) {
                        // Keep the complete bubble interior, including the three holes, free of its own shadow.
                        canvas.clipPath(SILHOUETTE,ClipMode.DIFFERENCE,true);
                        canvas.drawPath(SILHOUETTE,shadow);
                    } finally { canvas.restoreToCount(shadowSave); }
                }
                canvas.drawPath(ICON,ink);
            } finally { canvas.restoreToCount(iconSave); }
            SkiaFontRenderer.drawTextWithShadow(canvas, font, key, textX,
                    SkiaFontRenderer.centerBaselineY(font, cy), 0xFFFFFFFF,TEXT_SHADOW);
            return new Sprite(font, key, density, softShadow, surface.makeImageSnapshot());
        }
    }

    public static void release() {
        if (sprite != null) sprite.image().close();
        sprite = null;
    }
}
