package com.atom.chat.image;

import io.github.humbleui.skija.Image;

import java.util.UUID;

/**
 * UI-facing avatar facade. v0.1.5 always uses the real skin source, but all
 * renderers depend on this class rather than AvatarRenderer/SkinResolver
 * directly so a custom-avatar source can be inserted later without touching
 * page code.
 */
public final class PlayerAvatar {
    private static volatile PlayerAvatarSource source = SkinPlayerAvatarSource.INSTANCE;

    private PlayerAvatar() {
    }

    public static Image face(UUID uuid, String name) {
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

    /** Reserved for future custom-avatar providers; not used in v0.1.5. */
    public static void setSource(PlayerAvatarSource newSource) {
        if (newSource != null) {
            source = newSource;
        }
    }
}
