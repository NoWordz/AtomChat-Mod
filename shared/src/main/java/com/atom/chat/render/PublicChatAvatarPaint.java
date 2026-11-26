package com.atom.chat.render;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.pack.ServerPackStore;
import com.atom.chat.ui.AtomChatAppearance;
import com.atom.lib.design.atomchat.AppIcons;
import com.atom.lib.design.atomchat.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;

/** Shared drawing for the public chat row in both client mappings. */
public final class PublicChatAvatarPaint {
    private static final float BASE_RADIUS = 12F;
    private static final float ICON_SIZE = 26F;
    private static ServerPackStore.PublicAvatar source;
    private static Image image;

    private PublicChatAvatarPaint() {}

    public static float radius(float size) {
        return Math.min(size / 2F, UiTokens.radius(BASE_RADIUS, AtomChatAppearance.theme()));
    }

    public static void clear() {
        source = null;
        if (image != null) {
            image.close();
            image = null;
        }
    }

    public static void draw(Canvas canvas, float x, float y, float size, int background, int ink) {
        if (AtomChatConfig.get().serverPacksEnabled) {
            var current = ServerPackStore.publicAvatar();
            if (source != current) {
                clear();
                source = current;
                if (current != null) {
                    try {
                        image = Image.makeFromEncoded(current.png());
                    } catch (RuntimeException ignored) {
                        // A corrupt image shows the built-in globe.
                    }
                }
            }
        } else {
            clear();
        }
        float radius = radius(size);
        CornerGeometry.drawRoundedRect(canvas, x, y, size, size, radius, background);
        if (image == null) {
            DrawPrimitives.drawIconCentered(canvas, AppIcons.ICON_GLOBE_PATH,
                    x + size / 2F, y + size / 2F, UiTokens.s(ICON_SIZE), ink);
        } else {
            SkiaImages.drawRoundedImage(canvas, image, x, y, size, size, radius);
        }
    }
}
