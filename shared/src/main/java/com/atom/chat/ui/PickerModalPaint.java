package com.atom.chat.ui;


import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.design.atomchat.SkiaDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;

/** The existing colour-picker material, shared by modal surfaces and their action faces. */
public final class PickerModalPaint {
    private static final float RADIUS = 14, SHADOW_BLUR = 8;
    private PickerModalPaint() { }
    public static float radius() { return UiTokens.radius(RADIUS); }
    public static void backdrop(Canvas canvas, UiLayout.Rect panel, float progress) {
        SkiaDraw.drawRoundedRect(canvas,panel.x(),panel.y(),panel.w(),panel.h(),UiTokens.modalBackdropRadius(),
                Color.makeARGB((int)(200*progress),8,9,14));
    }
    public static void surface(Canvas canvas, UiLayout.Rect box, float progress) {
        int alpha=(int)(255*progress);
        SkiaDraw.drawRoundedShadow(canvas,box.x(),box.y(),box.w(),box.h(),radius(),UiTokens.s(SHADOW_BLUR),
                UiTokens.withAlpha(UiTokens.CHROME_SHADOW,100*progress));
        SkiaDraw.drawRoundedRect(canvas,box.x(),box.y(),box.w(),box.h(),radius(),
                Color.makeARGB((int)(.96F*alpha),32,36,46));
    }
    public static float buttonRadius(UiLayout.Rect rect) { return UiTokens.radius(rect.h()/(2*UiTokens.SCALE)); }
    public static void buttonFace(Canvas canvas,UiLayout.Rect rect,float hover,int alpha,boolean destructive) {
        int fill=destructive?UiTokens.dangerColor():Color.makeARGB(255,35,39,47);
        SkiaDraw.drawRoundedRect(canvas,rect.x(),rect.y(),rect.w(),rect.h(),buttonRadius(rect),
                Color.makeARGB((int)(.92F*alpha),(fill>>>16)&255,(fill>>>8)&255,fill&255));
        if(hover>.01F) SkiaDraw.drawRoundedRect(canvas,rect.x(),rect.y(),rect.w(),rect.h(),buttonRadius(rect),
                Color.makeARGB((int)(60*hover),255,255,255));
    }
}
