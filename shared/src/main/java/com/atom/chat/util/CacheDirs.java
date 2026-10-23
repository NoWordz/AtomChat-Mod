package com.atom.chat.util;

import com.atom.chat.AtomChat;
import com.atom.chat.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * AtomChat's data root beside {@code config}. Its four public groups are deliberately small:
 * {@code client} for player-authored data, {@code cache} for disposable downloads and diagnostics,
 * {@code server} for host-side content, and {@code backups} for verified migration copies.
 */
public final class CacheDirs {
    private record Roots(Path game, Path config) { }
    private enum LegacyLayout { FLAT_PREVIEW, OLD_RUNTIME, CONFIG }

    private static final java.util.Set<Roots> ATTEMPTED = new java.util.HashSet<>();
    private static final List<String> CLIENT_DIRS = List.of(
            "avatar", "banner", "wallpaper", "emotes", "presets", "history");

    private CacheDirs() {
    }

    public static Path dataRoot() {
        return Platform.gameDir().resolve("atomchat");
    }

    public static Path clientRoot() {
        return dataRoot().resolve("client");
    }

    public static Path cacheRoot() {
        return dataRoot().resolve("cache");
    }

    public static Path serverRoot() {
        return dataRoot().resolve("server");
    }

    public static Path clientDataPath() {
        return clientRoot().resolve("client-data.json");
    }

    public static Path avatarDir() {
        return clientRoot().resolve("avatar");
    }

    public static Path bannerDir() {
        return clientRoot().resolve("banner");
    }

    public static Path wallpaperDir() {
        return clientRoot().resolve("wallpaper");
    }

    /** The player's own emotes, added through the panel. Never distributed. */
    public static Path emotesDir() {
        return clientRoot().resolve("emotes");
    }

    public static Path presetsDir() {
        return clientRoot().resolve("presets");
    }

    public static Path historyDir() {
        return clientRoot().resolve("history");
    }

    /** Processed chat-image cache files decoded from downloaded chat links. */
    public static Path imageCacheDir() {
        return cacheRoot().resolve("images");
    }

    /** Root of the downloaded server packs, one folder per {@code PackKeys} key. */
    public static Path packsRoot() {
        return cacheRoot().resolve("packs");
    }

    /** One server's downloaded pack. */
    public static Path packDir(String key) {
        return packsRoot().resolve(key);
    }

    /** Optional avatar diagnostics; safe to delete. */
    public static Path debugDir() {
        return cacheRoot().resolve("debug");
    }

    /** Companion server's uploaded custom-avatar PNGs. */
    public static Path avatarDataDir() {
        return serverRoot().resolve("avatars");
    }

    /**
     * The emotes a server offers to clients, which are admin content. A server must never build
     * its pack from {@link #emotesDir()}: that folder belongs to whoever is playing.
     */
    public static Path serverEmotesDir() {
        return serverRoot().resolve("emotes");
    }

    /** Companion server's hosted chat media (images / animated GIFs). */
    public static Path mediaDataDir() {
        return serverRoot().resolve("media");
    }

    public static Path migrationBackupDir() {
        return dataRoot().resolve("backups/migration");
    }

    /** Game directory root; {@code server-icon.png} lives here. */
    public static Path gameDir() {
        return Platform.gameDir();
    }

    /**
     * Shows the client-authored folders before their first file exists. The cache debug folder is
     * optional but useful for support; server and backup folders are created only when used.
     */
    public static void initializePersonalDirectories() {
        for (String name : CLIENT_DIRS) {
            initialize(clientRoot().resolve(name));
        }
        initialize(debugDir());
    }

    private static void initialize(Path dir) {
        if (!DataMigration.allowPersonalPath(dir)) return;
        try {
            Files.createDirectories(dir);
        } catch (IOException | SecurityException e) {
            DataMigration.noteWarning();
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                    "resource", "Could not initialize AtomChat personal folder {}"), dir, e);
        }
    }

    public static synchronized void migrateFromOldConfigPaths() {
        Roots paths = new Roots(Platform.gameDir().toAbsolutePath().normalize(),
                Platform.configDir().toAbsolutePath().normalize());
        // A failed move is retried next launch, never on every panel/config reload.
        if (!ATTEMPTED.add(paths)) return;

        // Current organized/previous-flat data wins first; older roots fill only missing slots.
        migrateRoot(dataRoot(), LegacyLayout.FLAT_PREVIEW);

        Path oldRuntime = Platform.gameDir().resolve("atomchat-data");
        migrateRoot(oldRuntime, LegacyLayout.OLD_RUNTIME);
        if (Files.isDirectory(oldRuntime, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && DataMigration.allowPersonalPath(oldRuntime)) {
            try {
                Files.deleteIfExists(oldRuntime);
            } catch (java.nio.file.DirectoryNotEmptyException e) {
                DataMigration.noteWarning();
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                        "resource", "Legacy AtomChat runtime path {} still contains unrecognized data; preserved for inspection"),
                        oldRuntime);
            } catch (IOException | SecurityException e) {
                DataMigration.noteWarning();
            }
        } else if (Files.exists(oldRuntime, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            DataMigration.noteWarning();
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                    "resource", "Legacy AtomChat runtime path {} is not a safe directory; preserved for inspection"),
                    oldRuntime);
        }

        migrateRoot(Platform.configDir().resolve("atomchat"), LegacyLayout.CONFIG);
        migrate(Platform.configDir().resolve("atomchat.json"),
                migrationBackupDir().resolve("legacy-config/atomchat-flat.json"));
    }

    private static void migrateRoot(Path source, LegacyLayout layout) {
        if (!Files.exists(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        if (!DataMigration.allowPersonalPath(source)) return;
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.sorted().toList()) {
                Path target = targetFor(file, layout);
                if (target != null) migrate(file, target);
                else if (layout == LegacyLayout.FLAT_PREVIEW && !currentContainer(file.getFileName().toString())) {
                    DataMigration.noteWarning();
                    AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                            "resource", "Unknown AtomChat top-level item {} was preserved; move it into client/, cache/ or server/ if needed"),
                            file);
                }
            }
        } catch (IOException | SecurityException e) {
            DataMigration.noteWarning();
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message(
                    "resource", "Could not inspect legacy AtomChat data in {}; remaining originals are retained"),
                    source, e);
        }
    }

    private static Path targetFor(Path source, LegacyLayout layout) {
        String name = source.getFileName().toString();
        boolean currentContainer = currentContainer(name);
        if (currentContainer) return null;

        Path target = switch (name) {
            case "avatar" -> avatarDir();
            case "banner" -> bannerDir();
            case "wallpaper" -> wallpaperDir();
            case "emotes" -> emotesDir();
            case "appearance-presets", "presets" -> presetsDir();
            case "history" -> historyDir();
            case "client-data.json" -> clientDataPath();
            case "debug" -> debugDir();
            case "image-cache" -> imageCacheDir();
            case "packs" -> packsRoot();
            case "media" -> mediaDataDir();
            case "avatars" -> avatarDataDir();
            case "server-emotes" -> serverEmotesDir();
            case "migration-backup" -> migrationBackupDir().resolve(switch (layout) {
                case FLAT_PREVIEW -> "previous";
                case OLD_RUNTIME -> "previous-atomchat-data";
                case CONFIG -> "previous-config";
            });
            default -> null;
        };

        if (target == null && !Files.isDirectory(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && name.toLowerCase(Locale.ROOT).startsWith("banner.")) {
            target = bannerDir().resolve(name);
        }
        if (target == null && layout == LegacyLayout.CONFIG && legacyConfig(name)) {
            target = migrationBackupDir().resolve("legacy-config").resolve(name);
        }
        return target;
    }

    private static boolean currentContainer(String name) {
        return name.equals("client") || name.equals("cache")
                || name.equals("server") || name.equals("backups");
    }

    private static boolean legacyConfig(String name) {
        return name.equals("atomchat.json") || name.startsWith("atomchat.json.")
                || name.startsWith("atomchat-client.json.")
                || (name.startsWith("atomchat-config-") && name.endsWith(".tmp"));
    }

    private static void migrate(Path source, Path target) {
        DataMigration.migrate(source, target, migrationBackupDir(), dataRoot());
    }
}
