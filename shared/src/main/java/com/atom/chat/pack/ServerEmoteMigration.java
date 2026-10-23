package com.atom.chat.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/** Copies only explicitly designated legacy server-emotes; personal emotes are never promoted. */
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
        if (!dedicated || legacyDir == null || targetDir == null
                || !"server-emotes".equals(legacyDir.getFileName().toString())) {
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
