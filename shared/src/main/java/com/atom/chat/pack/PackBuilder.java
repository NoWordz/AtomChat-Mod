package com.atom.chat.pack;

import com.atom.chat.AtomChat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side packaging: turns the admin's emote directory, phrase list and
 * server identity into one {@link ServerPack}.
 *
 * <p>Deliberately free of Minecraft and config types — the caller passes the
 * caps in, so the whole rule set (name whitelist, caps, truncation, MOTD
 * cleaning) is unit-testable offline.
 *
 * <p>Ordering is by lowercased name and content is hashed, so the same
 * directory always yields the same {@code packHash}: adding, removing or
 * editing one emote is the only thing that invalidates a client's cache.
 */
public final class PackBuilder {
    private PackBuilder() {
    }

    /**
     * Builds the pack a server hands out.
     *
     * @param emotesDir    the admin's emote directory; missing/null means none
     * @param rawPhrases   raw phrases from the server config (may be null)
     * @param nameOverride server name from the config; blank falls back to the MOTD
     * @param fallbackName usually the MOTD, already raw (formatting is stripped here)
     * @param iconPng      {@code server-icon.png} bytes, or null
     * @param maxFiles     file-count cap from the config
     * @param maxBytes     total-size cap from the config
     */
    public static ServerPack build(Path emotesDir, List<String> rawPhrases, String nameOverride,
                                   String fallbackName, byte[] iconPng,
                                   int maxFiles, long maxBytes) {
        List<ServerPack.FileEntry> files = collectFiles(emotesDir, maxFiles, maxBytes);
        List<String> phrases = cleanPhrases(rawPhrases);
        String serverName = ServerPack.cleanServerName(
                nameOverride == null || nameOverride.isBlank() ? fallbackName : nameOverride);
        byte[] icon = cleanIcon(iconPng);
        String hash = ServerPack.hashOf(ServerPack.canonical(files, phrases, serverName, icon));
        ServerPack pack = new ServerPack(hash, files, phrases, serverName, icon);
        // Decision 28: the console has no /atomchat info command, so this line
        // is the admin's only way to see what the server is actually offering.
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Server pack {} built: {} file(s), {} KB, {} phrase(s), name '{}', icon {}"),
                hash.substring(0, 8), files.size(), pack.totalBytes() / 1024, phrases.size(),
                serverName.isEmpty() ? "-" : serverName,
                icon == null ? "none" : icon.length + " B");
        return pack;
    }

    private static List<ServerPack.FileEntry> collectFiles(Path dir, int maxFiles, long maxBytes) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        int fileCap = Math.max(0, maxFiles);
        long byteCap = Math.max(0L, maxBytes);
        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(path)) {
                    candidates.add(path);
                }
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Failed to list the server emote directory {}"), dir, e);
            return List.of();
        }
        // Lowercased name order keeps the manifest stable across filesystems and
        // decides which files survive the caps.
        candidates.sort(Comparator.comparing(path ->
                path.getFileName().toString().toLowerCase(Locale.ROOT)));

        List<ServerPack.FileEntry> files = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        long total = 0L;
        int skipped = 0;
        for (Path path : candidates) {
            String name = ServerPack.normalizeName(path.getFileName().toString());
            // A name outside the whitelist (wrong extension, a path fragment, a
            // leading dot) is dropped rather than sanitised: the client writes
            // exactly the names in the manifest, so only safe ones may pass.
            if (!ServerPack.isSafeName(name) || !taken.add(name)) {
                skipped++;
                continue;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
            } catch (IOException e) {
                skipped++;
                continue;
            }
            if (files.size() >= fileCap || total + bytes.length > byteCap) {
                skipped++;
                continue;
            }
            total += bytes.length;
            files.add(new ServerPack.FileEntry(name, ServerPack.sha256Hex(bytes), bytes.length));
        }
        if (skipped > 0) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Server pack skipped {} file(s): over the {} file / {} KB cap, "
                    + "duplicate after lowercasing, or outside the name whitelist"),
                    skipped, fileCap, byteCap / 1024);
        }
        return files;
    }

    private static List<String> cleanPhrases(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> phrases = new ArrayList<>();
        boolean overCap = false;
        for (String phrase : raw) {
            if (phrase == null) {
                continue;
            }
            String trimmed = phrase.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (phrases.size() >= ServerPack.MAX_PHRASES) {
                overCap = true;
                continue;
            }
            phrases.add(trimmed.length() > ServerPack.MAX_PHRASE_CHARS
                    ? trimmed.substring(0, ServerPack.MAX_PHRASE_CHARS)
                    : trimmed);
        }
        if (overCap) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Server pack kept the first {} phrases; the rest were ignored"),
                    ServerPack.MAX_PHRASES);
        }
        return List.copyOf(phrases);
    }

    private static byte[] cleanIcon(byte[] png) {
        if (png == null || png.length == 0) {
            return null;
        }
        if (png.length > ServerPack.MAX_ICON_BYTES) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Ignoring server-icon.png: {} B is over the {} B cap"),
                    png.length, ServerPack.MAX_ICON_BYTES);
            return null;
        }
        if (!isPng(png)) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Ignoring server-icon.png: not a PNG"));
            return null;
        }
        return png;
    }

    /** Magic-byte check; mirrors {@code AvatarCompanionServer.isPng} for icons. */
    private static boolean isPng(byte[] data) {
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
