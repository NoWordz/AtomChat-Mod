package com.atom.chat.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client-side copy of one server's pack: {@code manifest.json} plus the
 * emote files and the icon, all inside one directory named by {@link PackKeys}.
 *
 * <p>A sync writes a fresh temporary directory and only then swaps it in
 * ({@link #install}), so a half-finished download can never be read back as a
 * working pack — the next join simply re-diffs and re-fetches what is missing.
 */
public final class PackManifestFile {
    public static final String MANIFEST_NAME = "manifest.json";
    public static final String EMOTES_DIR = "emotes";
    public static final String ICON_NAME = "icon.png";
    private static final int FORMAT_VERSION = 1;
    private static final String TEMP_SUFFIX = ".tmp";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PackManifestFile() {
    }

    /** Writes a verified pack into a directory that is expected to be fresh. */
    public static void write(Path dir, ServerPack pack, Map<String, byte[]> contents) throws IOException {
        Path emotes = dir.resolve(EMOTES_DIR);
        Files.createDirectories(emotes);
        for (ServerPack.FileEntry entry : pack.files()) {
            byte[] bytes = contents.get(entry.name());
            if (bytes == null) {
                throw new IOException("no content for pack file " + entry.name());
            }
            Files.write(emotes.resolve(entry.name()), bytes);
        }
        byte[] icon = pack.icon();
        if (icon != null) {
            Files.write(dir.resolve(ICON_NAME), icon);
        }
        Files.writeString(dir.resolve(MANIFEST_NAME),
                GSON.toJson(toJson(pack)), StandardCharsets.UTF_8);
    }

    /**
     * Swaps a freshly written pack into place. The old copy is removed first
     * because Windows cannot atomically replace a non-empty directory; a crash
     * in between only means the next join re-downloads everything.
     */
    public static void install(Path packsRoot, String key, ServerPack pack,
                              Map<String, byte[]> contents) throws IOException {
        if (!PackKeys.isKey(key)) {
            throw new IOException("refusing to install into a bad pack key");
        }
        Path target = packsRoot.resolve(key);
        Path temp = packsRoot.resolve(key + TEMP_SUFFIX);
        deleteTree(temp);
        Files.createDirectories(temp);
        try {
            write(temp, pack, contents);
        } catch (IOException | RuntimeException e) {
            // Never leave a half-written pack lying next to the good one.
            deleteTree(temp);
            throw e;
        }
        com.atom.chat.util.DirectorySwap.replace(temp,target);
    }

    /**
     * Completes a downloaded set with the files that were already correct on
     * disk, so an incremental sync can install a whole pack.
     *
     * <p>The diff only asks for what differs (decision 21), which means the
     * download alone never covers the manifest - without this step the installer
     * would refuse the pack for the files it deliberately did not fetch. Every
     * kept file is re-hashed here, so a file that changed since the diff is a
     * failed sync rather than a silently mixed pack.
     *
     * @throws IOException when a kept file is missing or no longer matches
     */
    public static Map<String, byte[]> completeWithExisting(Path dir, ServerPack pack,
                                                           Map<String, byte[]> downloaded)
            throws IOException {
        Map<String, byte[]> contents = new LinkedHashMap<>();
        Path emotes = dir.resolve(EMOTES_DIR);
        for (ServerPack.FileEntry entry : pack.files()) {
            byte[] fetched = downloaded.get(entry.name());
            if (fetched != null) {
                contents.put(entry.name(), fetched);
                continue;
            }
            byte[] existing = Files.readAllBytes(emotes.resolve(entry.name()));
            if (existing.length != entry.size()
                    || !ServerPack.sha256Hex(existing).equals(entry.sha256Hex())) {
                throw new IOException("kept pack file changed under us: " + entry.name());
            }
            contents.put(entry.name(), existing);
        }
        return contents;
    }

    /** Reads a stored pack, or null when it is absent, unreadable or malformed. */
    public static ServerPack read(Path dir) {
        Path manifest = dir.resolve(MANIFEST_NAME);
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            ManifestJson json = GSON.fromJson(Files.readString(manifest, StandardCharsets.UTF_8),
                    ManifestJson.class);
            if (json == null || json.version != FORMAT_VERSION || json.packHash == null) {
                return null;
            }
            List<ServerPack.FileEntry> files = new ArrayList<>();
            if (json.files != null) {
                for (FileJson file : json.files) {
                    // Anything the wire could not have produced means the file
                    // was edited by hand or truncated: start over.
                    if (file == null || !ServerPack.isSafeName(file.name)
                            || file.sha256 == null || file.sha256.length() != 64 || file.size < 0) {
                        return null;
                    }
                    files.add(new ServerPack.FileEntry(file.name, file.sha256, file.size));
                }
            }
            return new ServerPack(json.packHash, files,
                    json.phrases == null ? List.of() : json.phrases,
                    json.serverName == null ? "" : json.serverName,
                    readIcon(dir));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Hashes every emote file currently on disk, so the next join can diff
     * against the manifest instead of trusting a cached hash (decision 21).
     */
    public static Map<String, String> localHashes(Path dir) {
        Map<String, String> hashes = new LinkedHashMap<>();
        Path emotes = dir.resolve(EMOTES_DIR);
        if (!Files.isDirectory(emotes)) {
            return hashes;
        }
        try (var stream = Files.list(emotes)) {
            List<Path> files = new ArrayList<>();
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
            files.sort(Comparator.comparing(path -> path.getFileName().toString()));
            for (Path path : files) {
                String name = ServerPack.normalizeName(path.getFileName().toString());
                if (!ServerPack.isSafeName(name)) {
                    continue;
                }
                hashes.put(name, ServerPack.sha256Hex(Files.readAllBytes(path)));
            }
        } catch (IOException e) {
            return Map.of();
        }
        return hashes;
    }

    /** Removes leftover files the server no longer offers. */
    public static void deleteStale(Path dir, List<String> names) {
        for (String name : names) {
            if (!ServerPack.isSafeName(name)) {
                continue;
            }
            try {
                Files.deleteIfExists(dir.resolve(EMOTES_DIR).resolve(name));
            } catch (IOException ignored) {
                // A file we cannot delete is only cosmetic: the panel never shows
                // it once the manifest is rewritten.
            }
        }
    }

    /** Removes a pack directory (or a leftover temp directory). */
    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static byte[] readIcon(Path dir) {
        try {
            Path icon = dir.resolve(ICON_NAME);
            return Files.isRegularFile(icon) ? Files.readAllBytes(icon) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static ManifestJson toJson(ServerPack pack) {
        ManifestJson json = new ManifestJson();
        json.version = FORMAT_VERSION;
        json.packHash = pack.packHash();
        json.serverName = pack.serverName();
        json.phrases = new ArrayList<>(pack.phrases());
        json.files = new ArrayList<>();
        for (ServerPack.FileEntry entry : pack.files()) {
            FileJson file = new FileJson();
            file.name = entry.name();
            file.sha256 = entry.sha256Hex();
            file.size = entry.size();
            json.files.add(file);
        }
        return json;
    }

    static final class ManifestJson {
        int version;
        String packHash;
        String serverName;
        List<FileJson> files = new ArrayList<>();
        List<String> phrases = new ArrayList<>();
    }

    static final class FileJson {
        String name;
        String sha256;
        int size;
    }
}
