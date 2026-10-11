package com.atom.chat.fun;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
/** Decorative overlay, clipped to the existing panel; never handles input. */
public final class ChatEffectRenderer {
    private ChatEffectRenderer() {}
    public static void draw(Canvas canvas, UiLayout layout, ChatEffectSession session,long nowMs) {
        if(!session.active(nowMs)) return;
        var type=session.type();
        var particles=ChatEffectParticles.frame(type,session.progress(nowMs),session.seed());
        var font=FontManager.font(UiTokens.CHAT_EFFECT_EMOJI_SIZE);
        canvas.save();
        try {
            SkiaDraw.clip(canvas,layout.panelX,layout.panelY,layout.panelW,layout.panelH,UiTokens.panelRadius());
            for(var p:particles) {
                canvas.save();
                try {
                    canvas.translate(layout.panelX+p.x()*layout.panelW,layout.panelY+p.y()*layout.panelH);
                    canvas.rotate(p.rotation());
                    canvas.scale(p.size()/UiTokens.CHAT_EFFECT_EMOJI_BASE_SIZE,p.size()/UiTokens.CHAT_EFFECT_EMOJI_BASE_SIZE);
                    SkiaFontRenderer.drawTextCentered(canvas,font,type.emoji(),0,0,((int)(p.alpha()*255)<<24)|0xFFFFFF);
                } finally { canvas.restore(); }
            }
        } finally { canvas.restore(); }
    }
}
