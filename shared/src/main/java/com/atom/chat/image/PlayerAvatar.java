package com.atom.chat.image;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SamplingMode;

import java.util.UUID;

/** UI-facing avatar facade; display style never mutates avatar providers or stored data. */
public final class PlayerAvatar {
    private static volatile PlayerAvatarSource source = SkinPlayerAvatarSource.INSTANCE;

    private PlayerAvatar() {
    }

    public static Image face(UUID uuid, String name) {
        if (isE33Chat()) {
            return name == null || name.isBlank() ? null
                    : AvatarRenderer.skinFace(SkinResolver.getSkin(uuid, name));
        }
        // Local custom avatar → server-companion avatar → skin. Every miss
        // falls through, so the skin always stays the final fallback.
        Image own = OwnPlayerAvatarSource.INSTANCE.face(uuid, name);
        if (own != null) {
            return own;
        }
        Image profile = com.atom.chat.profile.ProfileSync.image(uuid, com.atom.chat.profile.ProfileField.AVATAR);
        if (profile != null) return profile;
        // A current profile's empty avatar is authoritative; don't resurrect
        // a previously uploaded legacy avatar after an explicit clear.
        if ((com.atom.chat.profile.ProfileSync.record(uuid).imageFields() & 1) == 0) {
            Image companion = CompanionPlayerAvatarSource.INSTANCE.face(uuid, name);
            if (companion != null) return companion;
        }
        PlayerAvatarSource current = source;
        return current != null ? current.face(uuid, name) : null;
    }

    public static boolean isE33Chat() {
        return AtomChatConfig.get().effectiveAvatarStyle() == AtomChatConfig.AvatarStyle.E33CHAT;
    }

    /**
     * Draw the E33Chat skin layers and consume this draw even while the skin
     * is unavailable. False leaves the caller's original AtomChat path intact.
     * The ordinary style keeps E33Chat's native 20:22 face-to-hat proportion:
     * the hat outset is {@link UiTokens#E33CHAT_HAT_OUTSET_RATIO} of the face
     * size, so the hat layer rides the face size instead of a fixed pixel count.
     */
    public static boolean drawE33Chat(Canvas canvas, UUID uuid, String name,
                                      float x, float y, float size) {
        return drawE33Chat(canvas, uuid, name, x, y, size,
                size * UiTokens.E33CHAT_HAT_OUTSET_RATIO);
    }

    /** Draw with a per-side hat outset expressed in the same scaled units as size. */
    public static boolean drawE33Chat(Canvas canvas, UUID uuid, String name,
                                      float x, float y, float size, float hatOutset) {
        if (!isE33Chat()) {
            return false;
        }
        Image base = face(uuid, name);
        if (base == null) {
            SkiaDraw.drawRoundedRect(canvas, x, y, size, size, 0.0F,
                    Color.makeARGB(255, 120, 130, 145));
            return true;
        }
        SkiaDraw.drawRoundedImage(canvas, base, x, y, size, size, 0.0F, SamplingMode.DEFAULT);
        Image hat = AvatarRenderer.hat(SkinResolver.getSkin(uuid, name));
        if (hat != null) {
            float offset = hatOutset;
            float hatSize = size + 2.0F * offset;
            SkiaDraw.drawRoundedImage(canvas, hat, x - offset, y - offset,
                    hatSize, hatSize, 0.0F, SamplingMode.DEFAULT);
        }
        return true;
    }

    /** Override the AtomChat skin fallback provider. */
    public static void setSource(PlayerAvatarSource newSource) {
        if (newSource != null) {
            source = newSource;
        }
    }
}
