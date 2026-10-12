package com.atom.chat.profile;
import com.atom.chat.net.MediaIds;
import java.net.URI;
/** Bounds references without fetching remote URLs on the server. */
public final class ProfileReferences {
    public static final int MAX_REFERENCE = 2048;
    private ProfileReferences() {}
    public static String validate(String value) {
        if (value == null || value.isEmpty()) return value;
        if (value.length() > MAX_REFERENCE) throw new IllegalArgumentException("reference_too_long");
        String id = MediaIds.idOf(value);
        if (id != null) {
            if (!MediaIds.isSafeId(id)) throw new IllegalArgumentException("invalid_media_id");
            return value;
        }
        URI uri = URI.create(value);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("invalid_reference");
        return value;
    }
}
