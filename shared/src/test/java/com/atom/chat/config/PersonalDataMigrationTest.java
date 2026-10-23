package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import com.atom.chat.util.CacheDirs;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PersonalDataMigrationTest {
    @TempDir Path game;
    Object previous;
    Object previousConfig;

    @BeforeEach void install() throws Exception {
        com.atom.chat.util.DataMigration.takeWarning();
        Field f = Platform.class.getDeclaredField("provider");
        f.setAccessible(true);
        previous = f.get(null);
        Field c = AtomChatConfig.class.getDeclaredField("instance");
        c.setAccessible(true);
        previousConfig = c.get(null);
        c.set(null, null);
        Platform.install(new Platform.Provider() {
            public Path configDir() { return game.resolve("config"); }
            public Path gameDir() { return game; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @AfterEach void restore() throws Exception {
        Field f = Platform.class.getDeclaredField("provider");
        f.setAccessible(true);
        f.set(null, previous);
        Field c = AtomChatConfig.class.getDeclaredField("instance");
        c.setAccessible(true);
        c.set(null, previousConfig);
        com.atom.chat.util.DataMigration.takeWarning();
    }

    Path write(String rel, String text) throws Exception {
        Path p = game.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
        return p;
    }

    private List<String> retiredTexts() throws Exception {
        Path backups = game.resolve("atomchat/backups/migration");
        if (!Files.isDirectory(backups)) return List.of();
        try (var files = Files.walk(backups)) {
            return files.filter(Files::isRegularFile).map(p -> {
                try { return Files.readString(p); }
                catch (Exception e) { throw new RuntimeException(e); }
            }).toList();
        }
    }

    @Test void personalFilesAndExistingRuntimeCacheLeaveConfigWithoutLoss() throws Exception {
        for (String dir : List.of("emotes", "wallpaper", "avatar", "history", "appearance-presets",
                "server-emotes", "debug")) {
            write("config/atomchat/" + dir + "/private.dat", "private-" + dir);
        }
        write("config/atomchat/banner.png", "banner");
        write("atomchat-data/packs/server/pack.json", "pack");
        write("config/atomchat/atomchat-server.json", "{}");
        CacheDirs.migrateFromOldConfigPaths();

        assertEquals(game.resolve("atomchat"), CacheDirs.dataRoot());
        for (String dir : List.of("emotes", "wallpaper", "avatar", "history")) {
            assertEquals("private-" + dir,
                    Files.readString(game.resolve("atomchat/client/" + dir + "/private.dat")));
            assertFalse(Files.exists(game.resolve("config/atomchat/" + dir)));
        }
        assertEquals("private-appearance-presets",
                Files.readString(game.resolve("atomchat/client/presets/private.dat")));
        assertEquals("private-debug", Files.readString(game.resolve("atomchat/cache/debug/private.dat")));
        assertEquals("private-server-emotes",
                Files.readString(game.resolve("atomchat/server/emotes/private.dat")));
        assertEquals("banner", Files.readString(game.resolve("atomchat/client/banner/banner.png")));
        assertEquals("pack", Files.readString(game.resolve("atomchat/cache/packs/server/pack.json")));
        assertTrue(Files.exists(game.resolve("config/atomchat/atomchat-server.json")));
        try (var files = Files.list(game.resolve("config/atomchat"))) {
            assertEquals(List.of("atomchat-server.json"),
                    files.map(p -> p.getFileName().toString()).sorted().toList());
        }
        assertFalse(Files.exists(game.resolve("atomchat-data")));
    }

    @Test void conflictKeepsCurrentFileAndArchivesLegacyBytesOutsideConfig() throws Exception {
        write("config/atomchat/wallpaper/a.png", "old-private");
        write("atomchat/client/wallpaper/a.png", "new-private");
        CacheDirs.migrateFromOldConfigPaths();
        CacheDirs.migrateFromOldConfigPaths();
        assertEquals("new-private", Files.readString(game.resolve("atomchat/client/wallpaper/a.png")));
        assertFalse(Files.exists(game.resolve("config/atomchat/wallpaper/a.png")));
        assertTrue(retiredTexts().contains("old-private"));
    }

    @Test void unavailableDestinationNeverDeletesLegacyData() throws Exception {
        write("config/atomchat/history/a.jsonl", "history");
        write("atomchat", "file-blocking-directory");
        CacheDirs.migrateFromOldConfigPaths();
        assertEquals("history", Files.readString(game.resolve("config/atomchat/history/a.jsonl")));
        assertTrue(com.atom.chat.util.DataMigration.takeWarning());
    }

    @Test void aNonDirectoryLegacyRuntimeFileIsNeverDeleted() throws Exception {
        write("atomchat-data", "unknown legacy file");
        CacheDirs.migrateFromOldConfigPaths();
        assertEquals("unknown legacy file", Files.readString(game.resolve("atomchat-data")));
        assertTrue(com.atom.chat.util.DataMigration.takeWarning());
    }

    @Test void personalJsonIsSplitAndRoundTripsWithoutShippingInClientSettings() throws Exception {
        write("config/atomchat/atomchat-client.json", "{\"quickPhrases\":[\"private phrase\"],"
                + "\"playerSignature\":\"private signature\",\"blockedPlayers\":[\"Alice\"],"
                + "\"animationEnabled\":false}");
        AtomChatConfig.reload();
        assertEquals(List.of("private phrase"), AtomChatConfig.get().quickPhrases.stream()
                .filter(s -> !s.equals(AtomChatConfig.GUI_TIP)).toList());
        assertEquals("private signature", AtomChatConfig.get().playerSignature);
        assertFalse(AtomChatConfig.get().animationEnabled);
        var settings = JsonParser.parseString(Files.readString(
                game.resolve("config/atomchat/atomchat-client.json"))).getAsJsonObject();
        for (String name : List.of("quickPhrases", "playerSignature", "blockedPlayers", "guiTipSeeded")) {
            assertFalse(settings.has(name), name);
        }
        assertTrue(Files.isRegularFile(game.resolve("atomchat/client/client-data.json")));
        AtomChatConfig.reload();
        assertEquals("private signature", AtomChatConfig.get().playerSignature);
        assertEquals(List.of("Alice"), AtomChatConfig.get().blockedPlayers);
    }

    @Test void corruptedPersonalJsonIsPreservedAndLegacySettingsAreNotStripped() throws Exception {
        String legacy = "{\"quickPhrases\":[\"keep\"]}";
        write("config/atomchat/atomchat-client.json", legacy);
        write("atomchat/client/client-data.json", "broken-json");
        AtomChatConfig.reload();
        assertEquals("broken-json", Files.readString(game.resolve("atomchat/client/client-data.json")));
        assertEquals(legacy, Files.readString(game.resolve("config/atomchat/atomchat-client.json")));
    }

    @Test void invalidPersonalFieldTypesCannotBeOverwrittenByASubsequentSave() throws Exception {
        String invalid = "{\"quickPhrases\":\"this must be an array\"}";
        write("atomchat/client/client-data.json", invalid);
        assertFalse(AtomChatConfig.saveChecked(new AtomChatConfig()));
        assertEquals(invalid, Files.readString(game.resolve("atomchat/client/client-data.json")));
    }

    @Test void migratedConfigBackupPreservesOriginalBytesOutsideConfig() throws Exception {
        String legacy = "{\"quickPhrases\":[\"keep\"]}";
        write("config/atomchat/atomchat-client.json", legacy);
        write("config/atomchat/atomchat-client.json.bak", "backup");
        write("config/atomchat/atomchat-config-leftover.tmp", "temporary-private-data");
        AtomChatConfig.reload();
        assertFalse(Files.exists(game.resolve("config/atomchat/atomchat-client.json.bak")));
        assertFalse(Files.exists(game.resolve("config/atomchat/atomchat-config-leftover.tmp")));
        assertTrue(retiredTexts().contains(legacy));
    }

    @Test void coincidentOrNestedRootsCannotDeleteTheSource() throws Exception {
        Path file = write("same/keep.dat", "keep");
        com.atom.chat.util.DataMigration.migrate(file.getParent(), file.getParent(), game.resolve("backup"));
        assertEquals("keep", Files.readString(file));
        com.atom.chat.util.DataMigration.migrate(file.getParent(),
                file.getParent().resolve("nested"), game.resolve("backup"));
        assertEquals("keep", Files.readString(file));
        assertFalse(Files.exists(file.getParent().resolve("nested")));
    }

    @Test void repeatedReloadsDoNotDuplicateAnUnchangedLegacyConfigBackup() throws Exception {
        Path legacy = write("config/atomchat/atomchat-client.json", "{\"quickPhrases\":[\"keep\"]}");
        PersonalDataStore.saveAndStrip(new AtomChatConfig(), legacy);
        PersonalDataStore.saveAndStrip(new AtomChatConfig(), legacy);
        assertEquals(1, retiredTexts().size());
    }

    @Test void failedDirectoryMigrationIsOnlyAttemptedOncePerProcessAndRoot() throws Exception {
        write("config/atomchat/history/file.jsonl", "keep");
        Files.createDirectories(game.resolve("atomchat/client/history/file.jsonl"));
        CacheDirs.migrateFromOldConfigPaths();
        long before = retiredTexts().size();
        CacheDirs.migrateFromOldConfigPaths();
        assertEquals(before, retiredTexts().size());
    }

    @Test void linkedPersonalRootIsRejectedBeforeWritingIntoConfig() throws Exception {
        Path config = Files.createDirectories(game.resolve("config/atomchat"));
        try { Files.createSymbolicLink(game.resolve("atomchat"), config); }
        catch (FileSystemException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"),
                    "OS does not permit creating test symlinks");
            // NTFS junctions need no admin/developer-mode privilege and must be rejected too.
            Process link = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                    game.resolve("atomchat").toString(), config.toString()).redirectErrorStream(true).start();
            assertTrue(link.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, link.exitValue(), new String(link.getInputStream().readAllBytes()));
        }
        assertFalse(AtomChatConfig.saveChecked(new AtomChatConfig()));
        assertFalse(Files.exists(config.resolve("client/client-data.json")));
    }

    @Test void aCurrentImageSlotWinsAcrossDifferentFileExtensions() throws Exception {
        for (String name : List.of("avatar", "wallpaper")) {
            write("config/atomchat/" + name + "/" + name + ".png", "old");
            write("atomchat/client/" + name + "/" + name + ".jpg", "current");
        }
        write("config/atomchat/banner.png", "old banner");
        write("atomchat/client/banner/banner.jpg", "current banner");
        CacheDirs.migrateFromOldConfigPaths();
        for (String name : List.of("avatar", "wallpaper")) {
            assertFalse(Files.exists(game.resolve("atomchat/client/" + name + "/" + name + ".png")), name);
            assertEquals("current", Files.readString(
                    game.resolve("atomchat/client/" + name + "/" + name + ".jpg")));
            assertFalse(Files.exists(game.resolve("config/atomchat/" + name + "/" + name + ".png")));
        }
        assertFalse(Files.exists(game.resolve("atomchat/client/banner/banner.png")));
        assertEquals("current banner", Files.readString(game.resolve("atomchat/client/banner/banner.jpg")));
        assertEquals(3, retiredTexts().size(), "all retired originals have verified backups");
    }

    @Test void freshInstallKeepsPersonalFieldsOutOfConfigAndUsesNewDefaults() throws Exception {
        AtomChatConfig.reload();
        AtomChatConfig config = AtomChatConfig.get();
        assertFalse(config.antiSpamEnabled);
        assertTrue(config.compactMessagesEnabled);
        assertEquals(AtomChatConfig.BubbleStyle.CORNER, config.effectiveBubbleStyle());
        assertEquals(1.0F, config.feedbackVolume, 0.0001F);
        var json = JsonParser.parseString(Files.readString(
                game.resolve("config/atomchat/atomchat-client.json"))).getAsJsonObject();
        assertFalse(json.has("quickPhrases"));
        assertFalse(json.has("playerSignature"));
        assertTrue(Files.isRegularFile(game.resolve("atomchat/client/client-data.json")));
        assertFalse(Files.exists(game.resolve("atomchat-data")));
        assertFalse(Files.exists(game.resolve("atomchat/client-data.json")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"config/atomchat", "atomchat-data", "atomchat"})
    void migratedAvatarAndBannerReachThePublicationBoundaryFromTheNewRoot(String source) throws Exception {
        write(source + "/avatar/avatar.png", "avatar bytes");
        write(source + "/banner.png", "banner bytes");
        AtomChatConfig.reload();
        java.util.List<Path> uploaded = new java.util.ArrayList<>();
        java.util.List<com.atom.chat.profile.ProfilePatch> published = new java.util.ArrayList<>();
        var transport = new com.atom.chat.profile.ProfileTransport() {
            public boolean available() { return true; }
            public boolean hostingEnabled() { return true; }
            public void request(java.util.UUID uuid) { }
            public void submit(com.atom.chat.profile.ProfilePatch patch,
                               java.util.function.Consumer<String> result) {
                published.add(patch);
                result.accept("");
            }
            public void uploadHosted(Path file, java.util.function.Consumer<String> ok,
                                     java.util.function.Consumer<String> error) {
                uploaded.add(file);
                ok.accept("atomchat-media:" + (uploaded.size() == 1 ? "a" : "b").repeat(64) + ".png");
            }
            public void uploadExternal(Path file, java.util.function.Consumer<String> ok,
                                       java.util.function.Consumer<String> error) {
                fail("successful hosted publication should not need an external host");
            }
            public void execute(Runnable task) { task.run(); }
            public void feedback(boolean success) { assertTrue(success); }
        };
        try {
            com.atom.chat.profile.ProfileSync.install(transport);
            com.atom.chat.profile.ProfileSync.synchronizeImages();
            assertEquals(List.of(game.resolve("atomchat/client/avatar/avatar.png"),
                    game.resolve("atomchat/client/banner/banner.png")), uploaded);
            assertEquals("avatar bytes", Files.readString(uploaded.get(0)));
            assertEquals("banner bytes", Files.readString(uploaded.get(1)));
            assertFalse(Files.exists(game.resolve(source + "/banner.png")));
            assertEquals(2, published.size());
            assertNotNull(published.get(0).avatar());
            assertNotNull(published.get(1).banner());
        } finally {
            com.atom.chat.profile.ProfileSync.uninstall();
        }
    }

    @Test void normalClientInitializationCreatesVisiblePersonalFoldersWithoutServerContent() throws Exception {
        CacheDirs.initializePersonalDirectories();
        for (String dir : List.of("avatar", "banner", "wallpaper", "emotes", "presets", "history")) {
            assertTrue(Files.isDirectory(game.resolve("atomchat/client/" + dir)), dir);
        }
        assertTrue(Files.isDirectory(game.resolve("atomchat/cache/debug")));
        assertFalse(Files.exists(game.resolve("atomchat/server")));
        assertFalse(Files.exists(game.resolve("atomchat/backups")));
        assertFalse(Files.exists(game.resolve("atomchat/avatar")));
        assertFalse(Files.exists(game.resolve("atomchat-data")));
    }

    @Test void deployedFlatPreviewLayoutIsRetiredIntoOrganizedPathsWithoutArchivingBackupsRecursively() throws Exception {
        for (String old : List.of("image-cache", "packs", "media", "avatars", "server-emotes")) {
            write("atomchat/" + old + "/file.dat", "preview-" + old);
        }
        for (String old : List.of("avatar", "wallpaper", "emotes", "appearance-presets", "history", "debug")) {
            write("atomchat/" + old + "/file.dat", "preview-" + old);
        }
        write("atomchat/banner.webp", "preview banner");
        write("atomchat/migration-backup/original/secret.dat", "previous backup");
        write("atomchat/client-data.json", "{\"playerSignature\":\"keep\"}");
        CacheDirs.migrateFromOldConfigPaths();
        String[][] moves = {{"image-cache", "cache/images"}, {"packs", "cache/packs"},
                {"media", "server/media"}, {"avatars", "server/avatars"},
                {"server-emotes", "server/emotes"}, {"avatar", "client/avatar"},
                {"wallpaper", "client/wallpaper"}, {"emotes", "client/emotes"},
                {"appearance-presets", "client/presets"}, {"history", "client/history"},
                {"debug", "cache/debug"}};
        for (String[] move : moves) {
            assertEquals("preview-" + move[0],
                    Files.readString(game.resolve("atomchat/" + move[1] + "/file.dat")));
            assertFalse(Files.exists(game.resolve("atomchat/" + move[0])));
        }
        assertEquals("preview banner", Files.readString(game.resolve("atomchat/client/banner/banner.webp")));
        assertFalse(Files.exists(game.resolve("atomchat/banner.webp")));
        assertFalse(Files.exists(game.resolve("atomchat/migration-backup")));
        assertEquals("previous backup",
                Files.readString(game.resolve("atomchat/backups/migration/previous/original/secret.dat")));
        assertEquals("{\"playerSignature\":\"keep\"}",
                Files.readString(game.resolve("atomchat/client/client-data.json")));
        int count = retiredTexts().size();
        assertEquals(14, count, "fourteen moved data files, without backup-of-backup copies");
        CacheDirs.migrateFromOldConfigPaths();
        assertEquals(count, retiredTexts().size());
    }

    @Test void currentNewLayoutWinsAgainstBothPreviousLayoutsAndRootBannerExtensions() throws Exception {
        write("atomchat/client/banner/banner.jpg", "current banner");
        write("atomchat/banner.png", "flat banner");
        write("atomchat-data/banner.webp", "old runtime banner");
        write("config/atomchat/banner.jpeg", "config banner");
        write("atomchat/cache/images/same.bin", "current cache");
        write("atomchat/image-cache/same.bin", "flat cache");
        write("atomchat-data/image-cache/same.bin", "old runtime cache");
        write("config/atomchat/image-cache/same.bin", "config cache");
        CacheDirs.migrateFromOldConfigPaths();
        try (var files = Files.list(game.resolve("atomchat/client/banner"))) {
            assertEquals(List.of("banner.jpg"), files.map(p -> p.getFileName().toString()).toList());
        }
        assertEquals("current banner", Files.readString(game.resolve("atomchat/client/banner/banner.jpg")));
        assertEquals("current cache", Files.readString(game.resolve("atomchat/cache/images/same.bin")));
        assertTrue(retiredTexts().containsAll(List.of("flat banner", "old runtime banner", "config banner",
                "flat cache", "old runtime cache", "config cache")));
    }

    @Test void linkedPersonalFolderIsNeverPopulatedOrUsedAsAMigrationDestination() throws Exception {
        Path outside = Files.createDirectories(game.resolve("outside"));
        Path link = game.resolve("atomchat/client/emotes");
        Files.createDirectories(link.getParent());
        try { Files.createSymbolicLink(link, outside); }
        catch (FileSystemException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
            Process proc = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", link.toString(),
                    outside.toString()).redirectErrorStream(true).start();
            assertTrue(proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, proc.exitValue(), new String(proc.getInputStream().readAllBytes()));
        }
        try {
            write("config/atomchat/emotes/keep.png", "original emote");
            CacheDirs.migrateFromOldConfigPaths();
            CacheDirs.initializePersonalDirectories();
            assertEquals("original emote", Files.readString(game.resolve("config/atomchat/emotes/keep.png")));
            try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
            assertTrue(com.atom.chat.util.DataMigration.takeWarning());
        } finally { Files.deleteIfExists(link); }
    }

    @Test void serverAndBackupContainersAreNeverTreatedAsLegacyPayloads() throws Exception {
        write("atomchat/server/host-only.dat", "host");
        write("atomchat/backups/migration/audit.log", "audit");
        CacheDirs.migrateFromOldConfigPaths();
        assertEquals("host", Files.readString(game.resolve("atomchat/server/host-only.dat")));
        assertEquals("audit", Files.readString(game.resolve("atomchat/backups/migration/audit.log")));
        assertEquals(1, retiredTexts().size());
    }
}
