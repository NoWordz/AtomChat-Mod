package com.atom.chat.history;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Chat-history persistence: world key, file path and JSONL line format.
 *
 * <p>Pure functions and plain data only — no Minecraft types, no IO beyond path
 * arithmetic — so the whole format is unit-testable without a client. The
 * orchestration (when to save/load, which messages belong to which channel)
 * lives in {@link ChatHistory}.
 *
 * <p>Key scheme is ported from e33chat: singleplayer / LAN host uses
 * {@code SP:<level name>}, multiplayer uses {@code MP:<server entry name>}.
 * Neither contains an address or a port, so re-opening a LAN world on a new
 * port keeps writing the same file.
 */
public final class HistoryStore {
    private HistoryStore() {
    }

    public static final int FORMAT_VERSION = 1;
    public static final String CHANNEL_PUBLIC = "public";
    public static final String CHANNEL_PRIVATE = "private";

    private static final Gson GSON = new Gson();
    private static volatile Path baseDir;

    /** Injected once from client init (config dir). Tests point it at a temp dir. */
    public static void init(Path dir) {
        baseDir = dir;
    }

    public static Path baseDir() {
        return baseDir;
    }

    /**
     * @param singleplayer true when an integrated server is running (singleplayer
     *                     or a LAN host)
     * @param levelName    integrated-server world name, may be null
     * @param serverName   multiplayer server-list entry name, may be null
     */
    public static String keyFor(boolean singleplayer, String levelName, String serverName) {
        if (singleplayer && levelName != null && !levelName.isBlank()) {
            return "SP:" + levelName;
        }
        if (serverName != null && !serverName.isBlank()) {
            return "MP:" + serverName;
        }
        return "world";
    }

    /** Only world-specific keys are persisted; the "world" fallback is memory-only. */
    public static boolean isWorldSpecific(String key) {
        return key != null && (key.startsWith("SP:") || key.startsWith("MP:"));
    }

    public static Path file(String worldKey) {
        // Keep Unicode (Chinese world names stay readable); only strip characters
        // that break file systems / path parsing. The short hash disambiguates
        // worlds whose sanitized names collide.
        String safe = worldKey.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        Path dir = baseDir != null ? baseDir : Path.of("atomchat", "client", "history");
        return dir.resolve(safe + "_" + sha256Short(worldKey) + ".jsonl");
    }

    static String sha256Short(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /**
     * One persisted message. {@code componentJson} is the Minecraft
     * {@code Text.Serialization} JSON of the original component (computed on the
     * render thread, because serialization needs a registry lookup). It may be
     * null for lines written before rich serialization existed or when the
     * lookup was unavailable.
     */
    public static final class Entry {
        public final long timestamp;
        public final boolean priv;
        /** Private channel partner: UUID string when known, otherwise the name. */
        public final String peer;
        public final boolean own;
        public final boolean system;
        public final String quoteName;
        public final String quoteText;
        public final String senderUuid;
        public final String senderName;
        public final String profileName;
        public final String contentText;
        public final String componentJson;

        public Entry(long timestamp, boolean priv, String peer, boolean own, boolean system,
                     String quoteName, String quoteText, String senderUuid, String senderName,
                     String profileName, String contentText, String componentJson) {
            this.timestamp = timestamp;
            this.priv = priv;
            this.peer = peer;
            this.own = own;
            this.system = system;
            this.quoteName = quoteName;
            this.quoteText = quoteText;
            this.senderUuid = senderUuid;
            this.senderName = senderName;
            this.profileName = profileName;
            this.contentText = contentText;
            this.componentJson = componentJson;
        }
    }

    public static String toLine(Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("v", FORMAT_VERSION);
        o.addProperty("ts", e.timestamp);
        o.addProperty("ch", e.priv ? CHANNEL_PRIVATE : CHANNEL_PUBLIC);
        if (e.peer != null) {
            o.addProperty("peer", e.peer);
        }
        o.addProperty("own", e.own);
        o.addProperty("sys", e.system);
        if (e.quoteName != null) {
            o.addProperty("qn", e.quoteName);
        }
        if (e.quoteText != null) {
            o.addProperty("qt", e.quoteText);
        }
        if (e.senderUuid != null) {
            o.addProperty("uuid", e.senderUuid);
        }
        if (e.senderName != null) {
            o.addProperty("sender", e.senderName);
        }
        if (e.profileName != null) {
            o.addProperty("profile", e.profileName);
        }
        if (e.contentText != null) {
            o.addProperty("content", e.contentText);
        }
        if (e.componentJson != null) {
            o.add("comp", JsonParser.parseString(e.componentJson));
        }
        return GSON.toJson(o);
    }

    /** @return the parsed entry, or null when the line is unreadable (caller skips it). */
    public static Entry fromLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            if (!o.has("ts")) {
                return null;
            }
            long ts = o.get("ts").getAsLong();
            boolean priv = o.has("ch") && CHANNEL_PRIVATE.equals(o.get("ch").getAsString());
            String peer = o.has("peer") ? o.get("peer").getAsString() : null;
            boolean own = o.has("own") && o.get("own").getAsBoolean();
            boolean system = o.has("sys") && o.get("sys").getAsBoolean();
            String qn = o.has("qn") ? o.get("qn").getAsString() : null;
            String qt = o.has("qt") ? o.get("qt").getAsString() : null;
            String uuid = o.has("uuid") ? o.get("uuid").getAsString() : null;
            String sender = o.has("sender") ? o.get("sender").getAsString() : null;
            String profile = o.has("profile") ? o.get("profile").getAsString() : null;
            String content = o.has("content") ? o.get("content").getAsString() : null;
            String comp = o.has("comp") ? o.get("comp").toString() : null;
            return new Entry(ts, priv, peer, own, system, qn, qt, uuid, sender, profile, content, comp);
        } catch (Exception e) {
            return null;
        }
    }
}
