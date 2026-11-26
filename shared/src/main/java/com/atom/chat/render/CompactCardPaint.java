package com.atom.chat.render;

import com.atom.chat.api.card.CardRenderRegistry;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.SpecialCardPresentation;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

/** Measured HUD presentation, painted in the same physical-pixel pass as other HUD messages. */
final class CompactCardPaint {
    record Content(ShareCard card, CardState state, CardRenderRegistry.CompactCardRenderer renderer) {
        Content generic() { return new Content(card, state, null); }
    }
    record Size(float width, float height, boolean custom) {}
    private CompactCardPaint() {}

    static Content of(ShareCard card) {
        return new Content(card, SpecialCardPresentation.state(card), CardRenderRegistry.compactRendererOf(card));
    }

    static String label(Content content) {
        var state = content.state();
        if (state != null && state.statusText() != null && !state.statusText().isBlank()) return state.statusText();
        var card = content.card();
        return card.hudSummary().isBlank() ? card.title() : card.hudSummary();
    }

    static float textInset(float width) {
        return width >= UiTokens.COMPOSER_ICON_SIZE + UiTokens.BUBBLE_PAD * 3
                ? UiTokens.COMPOSER_ICON_SIZE + UiTokens.BUBBLE_PAD * 2 : UiTokens.BUBBLE_PAD;
    }

    static Size measure(Content content, float maxWidth, float fallbackWidth, float fallbackHeight) {
        if (content.renderer() != null) {
            try {
                float[] size = content.renderer().measure(content.card(), content.state(), maxWidth);
                if (size != null && size.length >= 2 && Float.isFinite(size[0]) && Float.isFinite(size[1])
                        && size[0] > 0 && size[1] > 0) {
                    return new Size(Math.min(size[0], maxWidth), Math.min(size[1], fallbackHeight * 2), true);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A third-party renderer can fail independently of the HUD.
            }
        }
        return new Size(fallbackWidth, fallbackHeight, false);
    }

    static boolean drawCustom(Canvas canvas, Content content, float x, float y, float width, float height) {
        if (content.renderer() == null) return false;
        int saved = canvas.save();
        try {
            canvas.clipRect(Rect.makeXYWH(x, y, width, height));
            content.renderer().draw(canvas, content.card(), content.state(), x, y, width, height);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    static void drawBackground(Canvas canvas, Content content, float x, float y, float width, float height,
                               float radius, int background, int iconInk, boolean shadow) {
        if (shadow) HudShadow.draw(canvas, RRect.makeXYWH(x, y, width, height, radius));
        CornerGeometry.drawRoundedRect(canvas, x, y, width, height, radius, background);
        if (textInset(width) == UiTokens.BUBBLE_PAD) return;
        var icon = switch (SpecialCardPresentation.kind(content.card())) {
            case LOCATION -> AppIcons.ICON_GLOBE_PATH;
            case TELEPORT_REQUEST -> DrawPrimitives.ICON_TP_PATH;
            case ITEM, RED_PACKET, GENERAL, CUSTOM -> DrawPrimitives.ICON_PHRASE_PATH;
        };
        int accent = content.card().accentColor();
        int ink = content.state() != null && content.state().dimmed() || accent >>> 24 == 0 ? iconInk : accent;
        DrawPrimitives.drawIconCentered(canvas, icon, x + UiTokens.BUBBLE_PAD + UiTokens.COMPOSER_ICON_SIZE / 2,
                y + height / 2, UiTokens.COMPOSER_ICON_SIZE, ink);
    }
}
