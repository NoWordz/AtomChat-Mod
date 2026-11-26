package com.atom.chat.render;

import com.atom.chat.api.card.CardRenderRegistry;
import com.atom.chat.api.card.CardState;
import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.SpecialCardPresentation;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.PictureRecorder;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

/** Measured HUD presentation, painted in the same physical-pixel pass as other HUD messages. */
final class CompactCardPaint {
    private static final float ITEM_ICON_SCALE = 1.30F;

    record Content(ShareCard card, CardState state, CardRenderRegistry.CompactCardRenderer renderer) {
        Content generic() { return new Content(card, state, null); }
    }
    record Size(float width, float height, boolean custom) {}
    private CompactCardPaint() {}

    static Content of(ShareCard card, boolean imagesEnabled) {
        return new Content(card, SpecialCardPresentation.state(card),
                imagesEnabled ? CardRenderRegistry.compactRendererOf(card) : null);
    }

    static String label(Content content) {
        var state = content.state();
        if (state != null && state.statusText() != null && !state.statusText().isBlank()) return state.statusText();
        var card = content.card();
        return card.hudSummary().isBlank() ? card.title() : card.hudSummary();
    }

    static boolean showsNativeItem(Content content, float width) {
        return hasNativeItem(content) && iconExtent(content, width) > 0F;
    }

    private static boolean hasNativeItem(Content content) {
        return content != null && content.card().template() == ShareCard.Template.ITEM && content.card().item() != null;
    }

    private static float iconExtent(Content content, float width) {
        if (content == null || width < UiTokens.COMPOSER_ICON_SIZE + UiTokens.BUBBLE_PAD * 3) return 0F;
        if (!hasNativeItem(content)) return UiTokens.COMPOSER_ICON_SIZE;
        return Math.min(UiTokens.COMPOSER_ICON_SIZE * ITEM_ICON_SCALE, width - UiTokens.BUBBLE_PAD * 3);
    }

    static float textInset(Content content, float width) {
        float extent = iconExtent(content, width);
        return extent > 0F ? extent + UiTokens.BUBBLE_PAD * 2 : UiTokens.BUBBLE_PAD;
    }

    static float fallbackHeight(Content content, float width, float lineBubbleHeight) {
        float extent = Math.max(UiTokens.COMPOSER_ICON_SIZE, iconExtent(content, width));
        return Math.max(lineBubbleHeight, extent + UiTokens.BUBBLE_PAD * 2);
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
        Rect bounds = Rect.makeXYWH(x, y, width, height);
        try (var recorder = new PictureRecorder()) {
            Canvas recording = recorder.beginRecording(bounds);
            int saved = recording.save();
            recording.clipRect(bounds);
            content.renderer().draw(recording, content.card(), content.state(), x, y, width, height);
            if (recording.getSaveCount() != saved + 1) return false;
            recording.restoreToCount(saved);
            try (var picture = recorder.finishRecordingAsPicture()) {
                int hostSave = canvas.save();
                try {
                    canvas.clipRect(bounds);
                    picture.playback(canvas);
                } finally {
                    canvas.restoreToCount(hostSave);
                }
            }
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static void drawBackground(Canvas canvas, Content content, float x, float y, float width, float height,
                               float radius, int background, int iconInk, boolean shadow, boolean nativeItems) {
        if (shadow) HudShadow.draw(canvas, RRect.makeXYWH(x, y, width, height, radius));
        CornerGeometry.drawRoundedRect(canvas, x, y, width, height, radius, background);
        float extent = iconExtent(content, width);
        float iconSize = Math.min(extent, Math.max(0F, height - UiTokens.BUBBLE_PAD * 2));
        if (iconSize == 0F) return;
        float iconCenterX = x + UiTokens.BUBBLE_PAD + extent / 2F;
        if (nativeItems && showsNativeItem(content, width)
                && ItemIconRenderer.drawCardItem(canvas, content.card(), iconCenterX - iconSize / 2F,
                        y + (height - iconSize) / 2, iconSize)) return;
        var kind = SpecialCardPresentation.kind(content.card());
        var icon = switch (kind) {
            case LOCATION -> AppIcons.ICON_GLOBE_PATH;
            case TELEPORT_REQUEST -> DrawPrimitives.ICON_TP_PATH;
            case ITEM, RED_PACKET, GENERAL, CUSTOM -> DrawPrimitives.ICON_PHRASE_PATH;
        };
        int accent = content.card().accentColor();
        int ink = content.state() != null && content.state().dimmed() || accent >>> 24 == 0 ? iconInk : accent;
        if (kind == SpecialCardPresentation.Kind.LOCATION || kind == SpecialCardPresentation.Kind.TELEPORT_REQUEST) {
            ink = iconInk;
            if (shadow && (ink >>> 24) != 0) {
                SettingsLabelShadow.drawIcon(canvas, icon, iconCenterX, y + height / 2F, iconSize);
            }
        }
        DrawPrimitives.drawIconCentered(canvas, icon, iconCenterX, y + height / 2F, iconSize, ink);
    }
}
