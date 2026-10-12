package com.atom.chat.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The server settings a client is allowed to edit, as one plain record.
 *
 * <p>It is the single shape used by the wire codecs, the config screen and the
 * server-side validation, so "what the screen shows", "what travels" and "what
 * is allowed" can never drift apart.
 *
 * <p>The server never trusts these numbers: {@link #validate()} re-checks every
 * field on the server thread before anything is written, and {@link #sanitize()}
 * trims what is merely sloppy rather than wrong (blank phrases, a padded name,
 * an over-long phrase).
 */
public record ServerConfigValues(boolean hostingEnabled, boolean packEnabled,
                                 int maxFileKb, int maxTotalMb, int maxAvatarTotalMb,
                                 int retentionDays, int uploadCooldownMs,
                                 int packMaxFiles, int packMaxMb,
                                 String packName, List<String> phrases) {
    public static final int MAX_FILE_KB_LIMIT = 16384;
    public static final int MAX_STORE_MB_LIMIT = 65536;
    public static final int MAX_RETENTION_DAYS = 3650;
    public static final int MAX_COOLDOWN_MS = 600_000;
    public static final int MAX_PACK_FILES_LIMIT = 200;
    public static final int MAX_PACK_MB_LIMIT = 64;
    public static final int MAX_NAME_CHARS = 48;
    public static final int MAX_PHRASES = 20;
    public static final int MAX_PHRASE_CHARS = 200;

    /** Reason a set of values was refused, or null when it is acceptable. */
    public static final String ERR_RANGE = "bad_range";
    public static final String ERR_PHRASES = "bad_phrases";
    public static final String ERR_NAME = "bad_name";

    public ServerConfigValues {
        // A hand-edited config file can contain null entries, so this copy
        // tolerates them; sanitize() is what drops them.
        phrases = phrases == null
                ? List.of()
                : java.util.Collections.unmodifiableList(new ArrayList<>(phrases));
    }

    public static ServerConfigValues of(AtomChatServerConfig config) {
        return new ServerConfigValues(config.hostingEnabled, config.packEnabled,
                config.maxFileKb, config.maxTotalMb, config.maxAvatarTotalMb,
                config.retentionDays, config.uploadCooldownMs,
                config.packMaxFiles, config.packMaxMb,
                config.packName == null ? "" : config.packName,
                config.phrases == null ? List.of() : config.phrases);
    }

    /** Copies these values onto the live config object (the caller persists it). */
    public AtomChatServerConfig applyTo(AtomChatServerConfig config) {
        config.hostingEnabled = hostingEnabled;
        config.packEnabled = packEnabled;
        config.maxFileKb = maxFileKb;
        config.maxTotalMb = maxTotalMb;
        config.maxAvatarTotalMb = maxAvatarTotalMb;
        config.retentionDays = retentionDays;
        config.uploadCooldownMs = uploadCooldownMs;
        config.packMaxFiles = packMaxFiles;
        config.packMaxMb = packMaxMb;
        config.packName = packName;
        config.phrases = new ArrayList<>(phrases);
        return config;
    }

    /** @return an error code for the client to translate, or null when acceptable */
    public String validate() {
        if (maxFileKb < 1 || maxFileKb > MAX_FILE_KB_LIMIT
                || maxTotalMb < 1 || maxTotalMb > MAX_STORE_MB_LIMIT
                || maxAvatarTotalMb < 1 || maxAvatarTotalMb > MAX_STORE_MB_LIMIT
                || retentionDays < 0 || retentionDays > MAX_RETENTION_DAYS
                || uploadCooldownMs < 0 || uploadCooldownMs > MAX_COOLDOWN_MS
                || packMaxFiles < 1 || packMaxFiles > MAX_PACK_FILES_LIMIT
                || packMaxMb < 1 || packMaxMb > MAX_PACK_MB_LIMIT) {
            return ERR_RANGE;
        }
        if (phrases.size() > MAX_PHRASES) {
            return ERR_PHRASES;
        }
        if (packName != null && packName.strip().length() > MAX_NAME_CHARS) {
            return ERR_NAME;
        }
        return null;
    }

    /** Trims what is sloppy: a padded name, blank phrases, over-long phrases. */
    public ServerConfigValues sanitize() {
        List<String> cleaned = new ArrayList<>();
        for (String phrase : phrases) {
            if (phrase == null) {
                continue;
            }
            String trimmed = phrase.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            cleaned.add(trimmed.length() > MAX_PHRASE_CHARS
                    ? trimmed.substring(0, MAX_PHRASE_CHARS)
                    : trimmed);
        }
        return new ServerConfigValues(hostingEnabled, packEnabled,
                maxFileKb, maxTotalMb, maxAvatarTotalMb, retentionDays, uploadCooldownMs,
                packMaxFiles, packMaxMb,
                packName == null ? "" : packName.strip(), cleaned);
    }
}
