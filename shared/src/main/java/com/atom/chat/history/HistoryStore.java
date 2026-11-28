package com.atom.chat.history;

import com.atom.chat.chat.RecognitionServerKey;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Chat-history persistence: world key, file path and JSONL line format.
 *
 * <p>Pure functions and plain data only — no Minecraft types, no IO beyond path
 * arithmetic — so the whole format is unit-testable without a client. The
 * orchestration (when to save/load, which messages belong to which channel)
 * lives in {@link ChatHistory}.
 *
 * <p>Singleplayer and LAN hosts retain {@code SP:<level name>}.
 * Multiplayer uses the normalized configured address in a new hashed namespace.
 * Older title-keyed multiplayer files remain available on disk for recovery.
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
     * @param serverAddress multiplayer server address, may be null
     */
    public static String keyFor(boolean singleplayer, String levelName, String serverAddress) {
        if (singleplayer && levelName != null && !levelName.isBlank()) {
            return "SP:" + levelName;
        }
        String server = RecognitionServerKey.multiplayer(serverAddress);
        return server == null ? "world" : "MPADDR:" + sha256(server);
    }

    /** Only world-specific keys are persisted; the "world" fallback is memory-only. */
    public static boolean isWorldSpecific(String key) {
        return key != null && (key.startsWith("SP:") || key.startsWith("MP:") || key.startsWith("MPADDR:"));
    }

    public static boolean isLegacyMultiplayerFile(Path file) {
        String name = file.getFileName().toString();
        return name.startsWith("MP_") && name.endsWith(".jsonl");
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
        String digest = sha256(s);
        return digest.substring(0, Math.min(16, digest.length()));
    }

    private static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
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
        public final String senderComponentJson;
        public final String bodyComponentJson;
        public final com.atom.chat.chat.NativeMessageMeta nativeMeta;

        public Entry(long timestamp, boolean priv, String peer, boolean own, boolean system,
                     String quoteName, String quoteText, String senderUuid, String senderName,
                     String profileName, String contentText, String componentJson) {
            this(timestamp, priv, peer, own, system, quoteName, quoteText, senderUuid, senderName,
                    profileName, contentText, componentJson, null, null);
        }

        public Entry(long timestamp, boolean priv, String peer, boolean own, boolean system,
                     String quoteName, String quoteText, String senderUuid, String senderName,
                     String profileName, String contentText, String componentJson,
                     String senderComponentJson, String bodyComponentJson) {
            this(timestamp,priv,peer,own,system,quoteName,quoteText,senderUuid,senderName,profileName,
                    contentText,componentJson,senderComponentJson,bodyComponentJson,com.atom.chat.chat.NativeMessageMeta.UNKNOWN);
        }

        public Entry(long timestamp, boolean priv, String peer, boolean own, boolean system,
                     String quoteName, String quoteText, String senderUuid, String senderName,
                     String profileName, String contentText, String componentJson,
                     String senderComponentJson, String bodyComponentJson, com.atom.chat.chat.NativeMessageMeta metadata) {
            nativeMeta=(metadata==null?com.atom.chat.chat.NativeMessageMeta.UNKNOWN:metadata).persisted();
            if(nativeMeta.deleted()) {
                quoteName=null;quoteText=null;contentText=com.atom.chat.chat.NativeMessageMeta.DELETED_TEXT;
                componentJson=com.atom.chat.chat.NativeMessageMeta.DELETED_JSON;
                senderComponentJson=cleanSender(senderComponentJson);bodyComponentJson=null;
            }
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
            this.senderComponentJson = senderComponentJson;
            this.bodyComponentJson = bodyComponentJson;
        }
    }

    public static Entry deleted(Entry e) {
        return new Entry(e.timestamp,e.priv,e.peer,e.own,e.system,null,null,e.senderUuid,e.senderName,e.profileName,
                null,null,e.senderComponentJson,null,e.nativeMeta.asDeleted());
    }

    public static String toLine(Entry e) {
        return toLine(e, true);
    }

    static String toLine(Entry e, boolean includePresentation) {
        if(e.nativeMeta.deleted())e=deleted(e);
        JsonObject o = new JsonObject();
        o.addProperty("v", FORMAT_VERSION);
        if(e.nativeMeta.signature()!=null)o.addProperty("sig",e.nativeMeta.signature());
        if(e.nativeMeta.captured())o.addProperty("nativeKnown",true);
        if(e.nativeMeta.tag()!=null)o.add("tag",GSON.toJsonTree(e.nativeMeta.tag()));
        if(e.nativeMeta.deleted())o.addProperty("deleted",true);
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
        if (includePresentation && e.senderComponentJson != null) {
            o.add("senderComp", JsonParser.parseString(e.senderComponentJson));
        }
        if (includePresentation && e.bodyComponentJson != null) {
            o.add("bodyComp", JsonParser.parseString(e.bodyComponentJson));
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
            String senderComp = o.has("senderComp") ? o.get("senderComp").toString() : null;
            String bodyComp = o.has("bodyComp") ? o.get("bodyComp").toString() : null;
            var metadata=readNativeMeta(o);
            return new Entry(ts, priv, peer, own, system, qn, qt, uuid, sender, profile, content, comp,
                    senderComp, bodyComp,metadata);
        } catch (Exception e) {
            return null;
        }
    }
    private static String cleanSender(String json) {
        if(json==null)return null;
        try{var element=JsonParser.parseString(json);removeActions(element);return element.toString();}
        catch(RuntimeException invalid){return null;}
    }
    private static void removeActions(com.google.gson.JsonElement value) {
        if(value.isJsonObject()) {
            var object=value.getAsJsonObject();object.remove("clickEvent");object.remove("hoverEvent");object.remove("insertion");
            for(var entry:object.entrySet())removeActions(entry.getValue());
        }else if(value.isJsonArray())for(var child:value.getAsJsonArray())removeActions(child);
    }
    private static com.atom.chat.chat.NativeMessageMeta readNativeMeta(JsonObject value) {
        String signature=null;boolean known=false,deleted=false;com.atom.chat.chat.NativeMessageMeta.Tag tag=null;
        try{if(value.has("sig"))signature=value.get("sig").getAsString();}catch(RuntimeException invalid){}
        try{known=value.has("nativeKnown") && value.get("nativeKnown").getAsBoolean();}catch(RuntimeException invalid){}
        try{deleted=value.has("deleted") && value.get("deleted").getAsBoolean();}catch(RuntimeException invalid){}
        try{if(value.has("tag"))tag=GSON.fromJson(value.get("tag"),com.atom.chat.chat.NativeMessageMeta.Tag.class);}catch(RuntimeException invalid){}
        return new com.atom.chat.chat.NativeMessageMeta(signature,known,tag,deleted,-1,0);
    }
}
