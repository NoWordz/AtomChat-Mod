package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.OutlineIconBounds;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathFillMode;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.ImageFilter;
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
    private ChatShortcutHint() { }

    public record Frame(float alpha, float offsetX) { }

    public static Frame frame(float visibility) {
        float alpha = Math.max(0F, Math.min(1F, visibility));
        return new Frame(alpha, -LayoutTokens.HUD_SLIDE_GUI * (1F - alpha));
    }

    public static void renderGuiScaled(Canvas canvas,float width,float height,float canvasDensity,
                                       float guiScale,String key,float visibility) {
        float ratio=guiScale/canvasDensity;
        int save=canvas.save();
        try {
            canvas.scale(ratio,ratio);
            render(canvas,width/ratio,height/ratio,key,visibility);
        } finally { canvas.restoreToCount(save); }
    }

    public static void render(Canvas canvas, float width, float height, String key, float visibility) {
        Frame frame = frame(visibility);
        if (frame.alpha() <= 0F || key == null || key.isBlank()) return;
        float margin = LayoutTokens.HUD_MARGIN_GUI, size = LayoutTokens.HUD_ICON_GUI;
        float x = margin + frame.offsetX(), cy = height - margin - size / 2F;
        var font = FontManager.font(LayoutTokens.HUD_FONT_GUI);
        float textX=x+size+LayoutTokens.HUD_GAP_GUI;
        int save = canvas.save();
        try (Paint alpha = new Paint().setAlphaf(frame.alpha())) {
            if(frame.alpha()<1F) {
                float pad=LayoutTokens.HUD_SHADOW_PAD_GUI;
                canvas.saveLayer(Rect.makeLTRB(Math.max(0,x-pad),Math.max(0,cy-size/2F-pad),
                        Math.min(width,textX+SkiaFontRenderer.getStringWidth(font,key)+pad),height),alpha);
            }
            Rect bounds=OutlineIconBounds.of(ICON);
            float scale=size/Math.max(bounds.getWidth(),bounds.getHeight());
            int iconSave=canvas.save();
            try(var ink=new Paint().setColor(0xFFFFFFFF).setAntiAlias(true)) {
                canvas.translate(x+size/2F-(bounds.getLeft()+bounds.getRight())/2F*scale,
                        cy-(bounds.getTop()+bounds.getBottom())/2F*scale);
                canvas.scale(scale,scale);
                int shadowSave=canvas.save();
                try(var shadow=new Paint().setColor(0xFF000000).setAntiAlias(true).setImageFilter(ICON_SHADOW)) {
                    // Keep the complete bubble interior, including the three holes, free of its own shadow.
                    canvas.clipPath(SILHOUETTE,ClipMode.DIFFERENCE,true);
                    canvas.drawPath(SILHOUETTE,shadow);
                } finally { canvas.restoreToCount(shadowSave); }
                canvas.drawPath(ICON,ink);
            } finally { canvas.restoreToCount(iconSave); }
            SkiaFontRenderer.drawTextWithShadow(canvas, font, key, textX,
                    SkiaFontRenderer.centerBaselineY(font, cy), 0xFFFFFFFF,TEXT_SHADOW);
        } finally { canvas.restoreToCount(save); }
    }
}
