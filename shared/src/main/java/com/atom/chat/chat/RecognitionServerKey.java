package com.atom.chat.chat;

import java.nio.file.Path;
import java.util.Locale;

/** Saved-list titles are presentation, not a server namespace. */
public final class RecognitionServerKey {
    private RecognitionServerKey() {}
    public static String multiplayer(String address) {
        return address == null || address.isBlank() ? null : "MP:" + address.strip().toLowerCase(Locale.ROOT);
    }
    public static String singleplayer(Path world) {
        return world == null ? null : "SP:" + world.toAbsolutePath().normalize();
    }
}
