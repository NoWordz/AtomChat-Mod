package com.atom.chat.settings;

import com.atom.chat.render.CornerGeometry;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.DrawPrimitives;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Path;

/** SettingsSectionPage's inline action dimensions/material, with QuickPhrasePanel's icon cells.
 * Deliberately separate from confirmation-dialog buttons. The caller registers transformed ripples. */
public final class SettingsListAction {
    public static final float HEIGHT=UiTokens.s(28); // bare-value: SettingsSectionPage.drawBlocked action height
    private static final float PAD=UiTokens.s(18); // bare-value: drawBlocked text-width padding
    public static final float RADIUS=UiTokens.radius(8, AtomChatAppearance.theme()); // bare-value: drawBlocked action radius
    private final PressScale scale=PressScale.bounce();
    private float hover,pulse;
    public static float width(String label){return SkiaFontRenderer.getStringWidth(FontManager.font(UiTokens.FONT_QUOTE),label)+PAD;}
    public static int textColor(boolean selected,boolean danger,boolean enabled){
        var c=AtomChatConfig.get();return !enabled?c.textSecondaryColor:danger?UiTokens.dangerColor():selected?UiTokens.onAccent(c.accentColor):c.textPrimaryColor;
    }
    public void press(){pulse=UiMotion.POPUP_MS;com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);}
    public void draw(Canvas canvas,UiLayout.Rect rect,String label,Path icon,float rotation,boolean selected,boolean danger,boolean enabled,boolean hovered,float dt){
        hover=UiMotion.approach(hover,enabled&&hovered?1:0,dt,UiMotion.HOVER_MS);
        scale.update(enabled&&hovered,pulse>0,dt,Animations.enabled(),rect.w());pulse=Math.max(0,pulse-Math.max(0,dt));
        var c=AtomChatConfig.get();float opacity=enabled?1:.4f;
        scale.begin(canvas,rect.x()+rect.w()/2,rect.y()+rect.h()/2);
        try{
            CornerGeometry.drawRoundedRect(canvas,rect.x(),rect.y(),rect.w(),rect.h(),RADIUS,
                    UiTokens.withAlpha(selected?c.accentColor:UiTokens.cardCutout(AtomChatAppearance.theme()),255*opacity));
            if(hover>.01f)CornerGeometry.drawRoundedRect(canvas,rect.x(),rect.y(),rect.w(),rect.h(),RADIUS,UiTokens.cardHover(hover, AtomChatAppearance.theme()));
            int color=UiTokens.withAlpha(textColor(selected,danger,enabled),255*opacity);
            if(icon==null&&rotation!=0){
                // Sorting uses the existing filled settings fold chevron, not a rotated back/navigation icon.
                SettingsGroupPaint.chevron(canvas,rect.x()+rect.w()/2,rect.y()+rect.h()/2,UiTokens.SETTINGS_FOLD_CHEVRON_SIZE,rotation/90,color);
            }else if(icon!=null){
                float cx=rect.x()+rect.w()/2,cy=rect.y()+rect.h()/2;
                canvas.save();try{canvas.translate(cx,cy);canvas.rotate(rotation);DrawPrimitives.drawIconCentered(canvas,icon,0,0,UiTokens.CONTEXT_ICON_SIZE,color);}finally{canvas.restore();}
            }else{
                var font=FontManager.font(UiTokens.FONT_QUOTE);
                SkiaFontRenderer.drawTextCentered(canvas,font,SkiaFontRenderer.truncate(font,label,rect.w()-UiTokens.BUTTON_GAP),rect.x()+rect.w()/2,rect.y()+rect.h()/2,color);
            }
        }finally{canvas.restore();}
    }
}
