package com.atom.chat.notification;

import com.atom.lib.design.atomchat.SkiaDraw;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SaveLayerRec;
import io.github.humbleui.types.Rect;

final class NotificationMaterial {
    private NotificationMaterial() {}

    static float panelSigma(float cardStrength) {
        // At 200%, keep the established notification blur. A light floor keeps
        // text covered by a banner legible even when ordinary cards are clear.
        return UiTokens.NOTIFICATION_BLUR * Math.max(.45F, cardStrength / 2F);
    }

    static void drawPanelBlur(Canvas canvas, float x, float y, float width, float height,
                              float radius, float alpha, float cardStrength) {
        float sigma = panelSigma(cardStrength);
        int save = canvas.save();
        try (ImageFilter filter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP);
             Paint paint = new Paint().setAlphaf(alpha)) {
            SkiaDraw.clip(canvas, x, y, width, height, radius);
            canvas.saveLayer(new SaveLayerRec(Rect.makeXYWH(x, y, width, height), paint, filter));
            canvas.restore();
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
