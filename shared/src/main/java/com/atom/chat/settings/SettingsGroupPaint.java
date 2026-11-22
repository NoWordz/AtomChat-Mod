package com.atom.chat.settings;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.*;
import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.*;

/** The settings section's existing icon, dot trail, title, rule and fold glyph. */
public final class SettingsGroupPaint {
    private static final float TITLE_END_GAP=UiTokens.s(16); // bare-value: unchanged settings label title reserve
    private SettingsGroupPaint() {}
    public static void draw(Canvas canvas,UiLayout.Rect rect,String text,Path icon,boolean foldable,float expansion) {
        var config=com.atom.chat.config.AtomChatConfig.get();var font=FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        float pad=UiTokens.SETTINGS_ROW_PAD,size=UiTokens.COMPOSER_ICON_SIZE,cy=rect.y()+rect.h()/2;
        float x=rect.x()+pad+(foldable?size+UiTokens.SETTINGS_TILE_TEXT_GAP:0),right=rect.right()-pad;
        float iconX = rect.x() + pad + size / 2F;
        if (foldable) {
            DotTexture.drawTitleTrail(canvas, new UiLayout.Rect(x, cy - UiTokens.DOT_TRAIL_HEIGHT / 2F,
                    Math.max(0, right - x - UiTokens.SETTINGS_FOLD_CHEVRON_SIZE - pad), UiTokens.DOT_TRAIL_HEIGHT),
                    config.textSecondaryColor);
        }
        String title = SkiaFontRenderer.truncate(font, text,
                Math.max(0, right - x - UiTokens.SETTINGS_FOLD_CHEVRON_SIZE - TITLE_END_GAP));
        float baseline = SkiaFontRenderer.centerBaselineY(font, cy);
        if (foldable) {
            if (TextSurface.CAPSULE.shadowed()) {
                SettingsLabelShadow.draw(canvas, font, title, x, baseline, icon, iconX, cy, size);
            }
            DrawPrimitives.drawIconCentered(canvas, icon, iconX, cy, size, config.textPrimaryColor);
        }
        SkiaFontRenderer.drawText(canvas, font, title, x, baseline, config.textPrimaryColor);
        if (foldable) {
            chevron(canvas, right - UiTokens.SETTINGS_FOLD_CHEVRON_SIZE / 2F, cy,
                    UiTokens.SETTINGS_FOLD_CHEVRON_SIZE, expansion, config.textPrimaryColor);
        }
        float lineY=rect.bottom()-UiTokens.s(5); // bare-value: extracted SettingsSectionPage.drawLabel rule inset
        CornerGeometry.drawRoundedRect(canvas,x,lineY,Math.max(0,right-x),UiTokens.s(1),UiTokens.s(.5f),(config.textSecondaryColor&0xffffff)|(120<<24)); // bare-value: unchanged settings group rule stroke/radius/alpha
    }
    public static void chevron(Canvas canvas,float cx,float cy,float size,float expansion,int color) {
        try(var builder=new PathBuilder();var paint=new Paint().setColor(color).setAntiAlias(CornerGeometry.ANTIALIAS)){
            builder.moveTo(cx-size/2,cy-size).lineTo(cx-size/2,cy+size).lineTo(cx+size/2,cy);
            canvas.save();canvas.translate(cx,cy);canvas.rotate(90*expansion);canvas.translate(-cx,-cy);
            try(var path=builder.detach()){canvas.drawPath(path,paint);}finally{canvas.restore();}
        }
    }
}
