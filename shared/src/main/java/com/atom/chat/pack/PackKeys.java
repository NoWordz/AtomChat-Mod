package com.atom.chat.pack;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Directory naming for downloaded packs: one folder per world/server under
 * {@code atomchat/cache/packs/}.
 *
 * <p>The folder name is the hash of the address the client dialled (or the
 * single-player level name), never anything that came off the wire, so it can
 * never carry a path segment and can never collide between two servers that
 * happen to share a name.
 */
public final class PackKeys {
    /** Keys are exactly this many lowercase hex characters. */
    public static final int KEY_CHARS = 16;
    private static final String SINGLEPLAYER_PREFIX = "sp:";

    private PackKeys() {
    }

    /** Multiplayer key: the address the client dialled, e.g. {@code play.example.com:25565}. */
    public static String serverKey(String address) {
        String normalized = address == null ? "" : address.strip().toLowerCase(Locale.ROOT);
        return ServerPack.sha256Hex(normalized.getBytes(StandardCharsets.UTF_8)).substring(0, KEY_CHARS);
    }

    /**
     * Single-player key. Kept in its own namespace so a level called
     * "127.0.0.1:25565" cannot share a folder with that server.
     */
    public static String levelKey(String levelName) {
        return serverKey(SINGLEPLAYER_PREFIX + (levelName == null ? "" : levelName.strip()));
    }

    /** True for keys this class could have produced; checked before touching disk. */
    public static boolean isKey(String key) {
        if (key == null || key.length() != KEY_CHARS) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
