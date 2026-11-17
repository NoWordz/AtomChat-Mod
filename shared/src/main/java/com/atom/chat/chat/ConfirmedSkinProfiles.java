package com.atom.chat.chat;

import java.util.*;

/** Texture properties observed on this server, keyed by actual UUID rather than nickname. */
public final class ConfirmedSkinProfiles {
    public record Profile(UUID uuid, String account, String value, String signature) {}
    private static final Map<UUID, Profile> profiles = new LinkedHashMap<>(16, .75F, true);
    private ConfirmedSkinProfiles() {}
    public static synchronized void remember(UUID uuid, String account, String value, String signature) {
        if (uuid == null || uuid.equals(new UUID(0,0)) || value == null || value.isBlank()
                || value.length() > 16384 || signature != null && signature.length() > 4096) return;
        profiles.put(uuid, new Profile(uuid, account, value, signature));
        while (profiles.size() > 256) profiles.remove(profiles.keySet().iterator().next());
    }
    public static synchronized Profile find(UUID uuid) { return uuid == null ? null : profiles.get(uuid); }
    public static synchronized List<Profile> snapshot() { return List.copyOf(profiles.values()); }
    public static synchronized void restore(List<Profile> saved) {
        if (saved != null) for (Profile profile : saved) if (profile != null && !profiles.containsKey(profile.uuid()))
            remember(profile.uuid(), profile.account(), profile.value(), profile.signature());
    }
    public static synchronized void clear() { profiles.clear(); }
}
