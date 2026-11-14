package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;

/** Shared confirmation material and interactive action face for dialogs, guide cards and HUD tools. */
public final class ConfirmationSurface {
    private ConfirmationSurface() { }
    public static void surface(Canvas canvas,UiLayout.Rect box,float progress){PickerModalPaint.surface(canvas,box,progress);}
    public static int actionColor(boolean primary,boolean dangerous) {
        return primary?(dangerous?UiTokens.dangerColor():AtomChatConfig.get().accentColor):PickerModalPaint.secondaryButtonColor();
    }
    public static final class Button {
        private float hover,pulse;
        private final PressScale scale=PressScale.bounce();
        public void press(){pulse=UiMotion.POPUP_MS;}
        public void draw(Canvas canvas,UiLayout.Rect rect,String label,boolean primary,boolean dangerous,
                         boolean focused,boolean hovered,float dt,float opacity) {
            hover=UiMotion.approach(hover,hovered||focused?1:0,dt,UiMotion.HOVER_MS);
            scale.update(hovered,pulse>0,dt,Animations.enabled(),rect.w());pulse=Math.max(0,pulse-Math.max(0,dt));
            int color=actionColor(primary,dangerous);
            scale.begin(canvas,rect.x()+rect.w()/2,rect.y()+rect.h()/2);
            try {
                SkiaDraw.drawRoundedRect(canvas,rect.x(),rect.y(),rect.w(),rect.h(),PickerModalPaint.buttonRadius(rect),UiTokens.withAlpha(color,255*opacity));
                if(hover>.01f)SkiaDraw.drawRoundedRect(canvas,rect.x(),rect.y(),rect.w(),rect.h(),PickerModalPaint.buttonRadius(rect),io.github.humbleui.skija.Color.makeARGB((int)(60*hover*opacity),255,255,255));
                ClickRipples.region(rect.x(),rect.y(),rect.w(),rect.h(),PickerModalPaint.buttonRadius(rect),color);
                var font=FontManager.font(UiTokens.FONT_BUTTON);
                String fitted=SkiaFontRenderer.truncate(font,label,Math.max(0,rect.w()-UiTokens.BUTTON_GAP));
                float w=SkiaFontRenderer.getStringWidth(font,fitted);
                SkiaFontRenderer.drawText(canvas,font,fitted,rect.x()+(rect.w()-w)/2,
                    SkiaFontRenderer.centerBaselineY(font,rect.y()+rect.h()/2),UiTokens.withAlpha(primary?UiTokens.onAccent(color):0xFFFFFFFF,255*opacity));
            } finally {canvas.restore();}
        }
    }
}
