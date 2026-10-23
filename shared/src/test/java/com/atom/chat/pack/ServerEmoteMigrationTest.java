package com.atom.chat.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack source and the player's own emote folder must never be the same
 * place: on a single game dir (single player, or a LAN host) that made the
 * player's stickers part of what the server handed out.
 */
class ServerEmoteMigrationTest {
    @TempDir
    Path root;

    private Path emotesDir() throws IOException {
        Path dir = Files.createDirectories(root.resolve("legacy-config/atomchat/server-emotes"));
        Files.write(dir.resolve("mine.png"), "player sticker".getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private Path serverEmotesDir() {
        return root.resolve("atomchat/server-emotes");
    }

    @Test void evenADedicatedServerNeverPromotesAPersonalEmoteDirectory() throws IOException {
        for (String relative : List.of("atomchat/emotes", "config/atomchat/emotes")) {
            Path personal = Files.createDirectories(root.resolve(relative));
            Files.writeString(personal.resolve("private.png"), "private");
            assertEquals(0, ServerEmoteMigration.migrate(personal, serverEmotesDir(), true));
            assertTrue(!Files.exists(serverEmotesDir()));
        }
    }

    private static List<String> listing(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            List<String> out = new ArrayList<>();
            stream.forEach(p -> out.add(p.getFileName().toString()));
            out.sort(String::compareTo);
            return out;
        }
    }

    @Test
    void aDedicatedServerCarriesItsOldEmotesOver() throws IOException {
        Path legacy = emotesDir();
        Files.write(legacy.resolve("admin.gif"), "admin emote".getBytes(StandardCharsets.UTF_8));

        assertEquals(2, ServerEmoteMigration.migrate(legacy, serverEmotesDir(), true));
        assertEquals(List.of("admin.gif", "mine.png"), listing(serverEmotesDir()));
        // The old folder is left untouched: rolling back must not lose files.
        assertEquals(List.of("admin.gif", "mine.png"), listing(legacy));
    }

    @Test
    void aClientKeepsItsEmotesToItself() throws IOException {
        Path legacy = emotesDir();

        assertEquals(0, ServerEmoteMigration.migrate(legacy, serverEmotesDir(), false));
        assertTrue(!Files.exists(serverEmotesDir()), "a single player's emotes must not become a pack");
    }

    @Test
    void anAlreadyUsedServerFolderIsNeverOverwritten() throws IOException {
        Path legacy = emotesDir();
        Path target = Files.createDirectories(serverEmotesDir());
        Files.write(target.resolve("current.png"), "already here".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, ServerEmoteMigration.migrate(legacy, target, true));
        assertEquals(List.of("current.png"), listing(target));
    }

    @Test
    void onlyRegularEmoteFilesAreConsidered() throws IOException {
        Path legacy = emotesDir();
        Files.createDirectories(legacy.resolve("nested"));
        Files.write(legacy.resolve(".hidden"), "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(1, ServerEmoteMigration.migrate(legacy, serverEmotesDir(), true));
        assertEquals(List.of("mine.png"), listing(serverEmotesDir()));
    }

    @Test
    void aMissingLegacyFolderIsNotAnError() {
        assertEquals(0, ServerEmoteMigration.migrate(root.resolve("nowhere"), serverEmotesDir(), true));
    }

    @Test
    void aPackBuiltFromTheServerFolderIgnoresThePlayersEmotes() throws IOException {
        Path legacy = Files.createDirectories(root.resolve("config/atomchat/emotes"));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        Files.write(legacy.resolve("mine.png"), png);
        Path serverDir = Files.createDirectories(serverEmotesDir());
        Files.write(serverDir.resolve("admin.png"), png);

        ServerPack pack = PackBuilder.build(serverDir, List.of(), null, null, null, 32, 1024L * 1024L);

        List<String> names = new ArrayList<>();
        pack.files().forEach(file -> names.add(file.name()));
        assertEquals(List.of("admin.png"), names, "only the admin's folder may reach the manifest");
    }
}
