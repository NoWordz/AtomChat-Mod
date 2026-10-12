package com.atom.chat.profile;
public enum ProfileField {
    SIGNATURE, AVATAR, BANNER;
    public ProfilePatch patch(String value) {
        return switch (this) {
            case SIGNATURE -> new ProfilePatch(value, null, null);
            case AVATAR -> new ProfilePatch(null, value, null);
            case BANNER -> new ProfilePatch(null, null, value);
        };
    }
}
