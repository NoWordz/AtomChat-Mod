package com.atom.chat.profile;
import java.util.UUID;
/** Image presence is independent of the document revision: empty may mean clear or never published. */
public record ProfileRecord(UUID uuid, String signature, String avatar, String banner, long revision, int imageFields) {
    public ProfileRecord {
        if ((imageFields & ~3) != 0) throw new IllegalArgumentException("invalid_image_presence");
        if (avatar != null && !avatar.isEmpty()) imageFields |= 1;
        if (banner != null && !banner.isEmpty()) imageFields |= 2;
    }
    public ProfileRecord(UUID uuid, String signature, String avatar, String banner, long revision) {
        this(uuid, signature, avatar, banner, revision, 0);
    }
    public static ProfileRecord empty(UUID uuid) { return new ProfileRecord(uuid, "", "", "", 0, 0); }
}
