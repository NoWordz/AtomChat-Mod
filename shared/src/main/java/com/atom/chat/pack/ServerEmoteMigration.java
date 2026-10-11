package com.atom.chat.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * One-time move of an admin's emotes out of the folder that used to be shared
 * with the player's own stickers.
 *
 * <p>Until now a server built its pack from {@code config/atomchat/emotes/},
 * which is also where the client stores the emotes the player added with the
 * panel's {@code +}. On a dedicated server nobody notices - the two roles live
 * on different machines - but a single game dir (single player, or a LAN host)
 * makes the player's own stickers part of the pack it hands out, and LAN guests
 * receive them.
 *
 * <p>The pack source is now {@code config/atomchat/server-emotes/}. A dedicated
 * server that has emote files in the old folder clearly meant them as admin
 * content (there is no local player to own them), so they are copied over once.
 * Anything else is left alone: on a client those files are the player's own
 * emotes, and moving them would hand the player's collection to every guest.
 *
 * <p>Files are copied, never deleted - if a server rolls back to a build that
 * still reads the old folder, nothing is lost.
 */
public final class ServerEmoteMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger("AtomChat");

    private ServerEmoteMigration() {
    }

    /**
     * Copies emote files from {@code legacyDir} into {@code targetDir}.
     *
     * @param dedicated whether this JVM is a dedicated server; anything else
     *                  (client, single player, LAN host) is skipped entirely
     * @return how many files were copied
     */
    public static int migrate(Path legacyDir, Path targetDir, boolean dedicated) {
        if (!dedicated || legacyDir == null || targetDir == null) {
            return 0;
        }
        if (!Files.isDirectory(legacyDir) || hasAnyFile(targetDir)) {
            return 0;
        }
        List<Path> sources;
        try (Stream<Path> stream = Files.list(legacyDir)) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .toList();
        } catch (IOException e) {
            LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Could not read the old server emote folder {}"), legacyDir, e);
            return 0;
        }
        if (sources.isEmpty()) {
            return 0;
        }
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Could not create the server emote folder {}"), targetDir, e);
            return 0;
        }
        int copied = 0;
        for (Path source : sources) {
            Path target = targetDir.resolve(source.getFileName().toString());
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException e) {
                LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Could not move server emote {} to {}"), source, target, e);
            }
        }
        if (copied > 0) {
            LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("pack", "Moved {} server emote file(s) from {} to {} - servers now keep the emotes "
                    + "they hand out separate from a player's own stickers"), copied, legacyDir, targetDir);
        }
        return copied;
    }

    private static boolean hasAnyFile(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            // Unreadable target: treated as populated so nothing is copied over it.
            return true;
        }
    }
}
