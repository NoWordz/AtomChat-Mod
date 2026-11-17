package com.atom.chat.settings;

import com.atom.chat.ui.UiLayout;
import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.design.atomchat.Design;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.RRect;

/** The existing settings input surface, shared by client and server drafts. */
public final class SettingsFieldPaint {
    private SettingsFieldPaint() {}
    private static final float STROKE = UiTokens.s(1.5F); // bare-value: extracted unchanged from the existing settings input border
    public static void surface(Canvas canvas, UiLayout.Rect field, boolean editing, int accent) {
        surface(canvas,field,0,editing,false,true,accent);
    }
    public static void surface(Canvas canvas,UiLayout.Rect field,float hover,boolean focused,boolean error,boolean enabled,int accent) {
        float radius = UiTokens.radius(8);
        int base=UiTokens.cardCutout();boolean light=Design.isLight(base);
        int fill=light?0x0C000000:0x0EFFFFFF;
        SkiaDraw.drawRoundedRect(canvas, field.x(), field.y(), field.w(), field.h(), radius,fill);
        if(enabled&&hover>0)SkiaDraw.drawRoundedRect(canvas,field.x(),field.y(),field.w(),field.h(),radius,((Math.round(18*hover))<<24)|(accent&0xffffff));
        try (Paint border = new Paint().setMode(PaintMode.STROKE).setAntiAlias(true)
                .setStrokeWidth(focused||error?STROKE:UiTokens.s(.75f)).setColor(error?UiTokens.dangerColor():focused?accent:enabled?(light?0x55000000:0x55FFFFFF):(light?0x22000000:0x22FFFFFF))) {
            canvas.drawRRect(RRect.makeXYWH(field.x(), field.y(), field.w(), field.h(), radius), border);
        }
    }
}
