package com.atom.chat.config;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig.ConfigSnapshot;
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

    static ConfigSnapshot load(AtomChatConfig config) {
        Path file = path();
        try {
            DataMigration.noLinks(file);
            ConfigSnapshot snapshot = ConfigSnapshot.read(file);
            JsonObject data = snapshot.original();
            if (data == null) return snapshot;
            // Validate every value before touching the live config.
            JsonObject merged = GSON.toJsonTree(config).getAsJsonObject();
            for (String name : FIELDS) if (data.has(name)) merged.add(name, data.get(name));
            AtomChatConfig parsed = GSON.fromJson(merged, AtomChatConfig.class);
            for (String name : FIELDS) field(name).set(config, field(name).get(parsed));
            if (config.quickPhrases == null) config.quickPhrases = new java.util.ArrayList<>();
            if (config.blockedPlayers == null) config.blockedPlayers = new java.util.ArrayList<>();
            return snapshot;
        } catch (IOException | RuntimeException | ReflectiveOperationException e) {
            DataMigration.noteWarning();
            AtomChat.LOGGER.error(com.atom.chat.diagnostics.AtomChatLog.message("config", "Could not read AtomChat personal data at {}; original data and config retained, check before sharing config"), file, e);
            return null;
        }
    }

    private static Field field(String name) throws NoSuchFieldException { return AtomChatConfig.class.getField(name); }
    private static JsonObject read(Path file) throws IOException {
        JsonElement data = JsonParser.parseString(Files.readString(file));
        if (!data.isJsonObject()) throw new IOException("Personal data is not an object: " + file);
        return data.getAsJsonObject();
    }

    /** Personal data must commit before its fields may be stripped from the old config. */
    static JsonObject saveAndStrip(AtomChatConfig config, ConfigSnapshot snapshot,
                                   ConfigSnapshot loadedPersonal) throws IOException {
        Path file = path();
        DataMigration.noLinks(file);
        ConfigSnapshot previous = loadedPersonal == null ? ConfigSnapshot.read(file) : loadedPersonal;
        JsonObject existing = previous.original();
        if (existing != null) GSON.fromJson(existing, AtomChatConfig.class);
        JsonObject settings = GSON.toJsonTree(config).getAsJsonObject();
        JsonObject personal = new JsonObject();
        for (String name : FIELDS) {
            JsonElement value = settings.remove(name);
            if (value != null && !value.isJsonNull()) personal.add(name, value);
        }
        JsonObject original = snapshot.original();
        if (original != null && FIELDS.stream().anyMatch(original::has)) {
            snapshot.requireCurrent();
            previous.requireCurrent();
            DataMigration.backupConfig(snapshot.path(), CacheDirs.migrationBackupDir());
        }
        // JsonPrimitive(Float) compares differently from the same decimal parsed from disk.
        personal = JsonParser.parseString(GSON.toJson(personal)).getAsJsonObject();
        if (personal.equals(existing)) return settings;
        Files.createDirectories(file.getParent());
        Path staged = Files.createTempFile(file.getParent(), "client-data-", ".tmp");
        try {
            Files.writeString(staged, GSON.toJson(personal));
            snapshot.requireCurrent();
            previous.requireCurrent();
            replace(staged, file);
            if (!personal.equals(read(file))) throw new IOException("Personal data read-back failed");
        } finally { Files.deleteIfExists(staged); }
        return settings;
    }

    static void replace(Path staged, Path target) throws IOException {
        try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
