package com.atom.chat.config;

import com.atom.chat.AtomChat;
import com.atom.chat.util.CacheDirs;
import com.atom.chat.util.DataMigration;
import com.google.gson.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;

/** Keeps the public config API while persisting player-authored data outside distributable settings. */
final class PersonalDataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> FIELDS = List.of("quickPhrases", "guiTipSeeded", "blockedPlayers",
            "playerSignature", "profileSignatureEdited", "profileAvatarCleared", "profileBannerCleared",
            "profileSignatureMigrationDone");
    private PersonalDataStore() { }
    private static Path path() { return CacheDirs.clientDataPath(); }

    static boolean load(AtomChatConfig config) {
        Path file = path();
        try {
            DataMigration.noLinks(file);
            if (!Files.exists(file)) return true;
            JsonObject data = read(file);
            // Validate every value before touching the live config.
            JsonObject merged = GSON.toJsonTree(config).getAsJsonObject();
            for (String name : FIELDS) if (data.has(name)) merged.add(name, data.get(name));
            AtomChatConfig parsed = GSON.fromJson(merged, AtomChatConfig.class);
            for (String name : FIELDS) field(name).set(config, field(name).get(parsed));
            if (config.quickPhrases == null) config.quickPhrases = new java.util.ArrayList<>();
            if (config.blockedPlayers == null) config.blockedPlayers = new java.util.ArrayList<>();
            return true;
        } catch (IOException | RuntimeException | ReflectiveOperationException e) {
            DataMigration.noteWarning();
            AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("config", "Could not read AtomChat personal data at {}; original data and config retained, check before sharing config"), file, e);
            return false;
        }
    }

    private static Field field(String name) throws NoSuchFieldException { return AtomChatConfig.class.getField(name); }
    private static JsonObject read(Path file) throws IOException {
        JsonElement data = JsonParser.parseString(Files.readString(file));
        if (!data.isJsonObject()) throw new IOException("Personal data is not an object: " + file);
        return data.getAsJsonObject();
    }

    /** Personal data must commit before its fields may be stripped from the old config. */
    static JsonObject saveAndStrip(AtomChatConfig config, Path legacyConfig) throws IOException {
        DataMigration.noLinks(path());
        if (Files.exists(path())) GSON.fromJson(read(path()), AtomChatConfig.class);
        JsonObject settings = GSON.toJsonTree(config).getAsJsonObject();
        JsonObject personal = new JsonObject();
        for (String name : FIELDS) personal.add(name, settings.remove(name));
        if (Files.isRegularFile(legacyConfig)) {
            JsonObject old = read(legacyConfig);
            if (FIELDS.stream().anyMatch(old::has)) {
                DataMigration.backupConfig(legacyConfig, CacheDirs.migrationBackupDir());
            }
        }
        Files.createDirectories(path().getParent());
        Path staged = Files.createTempFile(path().getParent(), "client-data-", ".tmp");
        try {
            Files.writeString(staged, GSON.toJson(personal));
            replace(staged, path());
            if (!personal.equals(read(path()))) throw new IOException("Personal data read-back failed");
        } finally { Files.deleteIfExists(staged); }
        return settings;
    }

    static void replace(Path staged, Path target) throws IOException {
        try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
