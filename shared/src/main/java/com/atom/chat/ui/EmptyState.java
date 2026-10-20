package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.DotTexture;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.types.Rect;

/** The existing empty-list glyph and caption, with one local static backdrop. */
public final class EmptyState {
    private static final Paint ICON = new Paint().setAntiAlias(true).setMode(PaintMode.STROKE)
            .setStrokeCap(PaintStrokeCap.ROUND).setStrokeJoin(PaintStrokeJoin.ROUND);
    private EmptyState() { }

    public static void draw(Canvas canvas, UiLayout.Rect area, String caption) {
        if (area == null || area.w() <= 0F || area.h() <= 0F) return;
        var cfg = AtomChatConfig.get();
        var font = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float icon = UiTokens.SETTINGS_EMPTY_ICON;
        float textH = SkiaFontRenderer.textHeight(font);
        float gap = UiTokens.EMPTY_STATE_GAP;
        float top = area.y() + Math.max(0F, (area.h() - icon - gap - textH) / 2F);
        float cx = area.x() + area.w() / 2F;
        float w = Math.min(area.w(), UiTokens.DOT_EMPTY_W);
        float h = Math.min(area.h(), UiTokens.DOT_EMPTY_H);
        canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(area.x(), area.y(), area.w(), area.h()));
            DotTexture.drawHalo(canvas, new UiLayout.Rect(cx - w/2F, top + icon/2F - h/2F, w, h),
                    UiTokens.withAlpha(cfg.accentColor, UiTokens.DOT_SECONDARY_TINT_ALPHA));
            int muted = UiTokens.withAlpha(cfg.textSecondaryColor, 150);
            var path = AppIcons.ICON_NO_PLAYERS_PATH;
            var b = path.getBounds();
            float scale = icon / Math.max(b.getWidth(), b.getHeight());
            canvas.save();
            try {
                canvas.translate(cx - (b.getLeft()+b.getRight())/2F*scale,
                        top + icon/2F - (b.getTop()+b.getBottom())/2F*scale);
                canvas.scale(scale, scale);
                canvas.drawPath(path, ICON.setColor(muted).setStrokeWidth(UiTokens.iconStroke(icon)/scale));
            } finally { canvas.restore(); }
            SkiaFontRenderer.drawTextCentered(canvas, font, caption, cx,
                    top + icon + gap + textH/2F, muted);
        } finally { canvas.restore(); }
    }
}
