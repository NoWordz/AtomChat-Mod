package com.atom.chat.history;

import com.atom.chat.text.RichText;
import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.history.HistoryStore.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Client-thread snapshots and publication; bounded JSONL and cleanup run on one background worker. */
public final class ChatHistory {
    private static final long AUTO_SAVE_MS = 30_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atomchat-history-io");
        t.setDaemon(true);
        return t;
    });
    private static volatile String currentKey;
    private static volatile boolean dirty;
    private static volatile long lastAutoSave;
    private static final HistoryLifecycle HISTORY = new HistoryLifecycle(IO,
            runnable -> Minecraft.getInstance().execute(runnable), new HistoryLifecycle.Storage() {
        public HistoryFiles.Loaded read(String key) throws IOException {
            Path file = HistoryStore.file(key);
            com.atom.chat.util.DataMigration.noLinks(file);
            return HistoryFiles.read(file);
        }
        public void cleanup(String key, int days, HistoryLifecycle.CleanupPermit permit) throws Exception {
            cleanupOldHistory(key, days, permit);
        }
        public boolean write(String key, List<Entry> entries, BooleanSupplier valid) throws IOException {
            Path file = HistoryStore.file(key);
            com.atom.chat.util.DataMigration.noLinks(file);
            return HistoryFiles.write(file, entries, valid);
        }
        public void delete(String key) throws IOException {
            Path file = HistoryStore.file(key);
            com.atom.chat.util.DataMigration.noLinks(file);
            Files.deleteIfExists(file);
        }
    }, ChatHistory::enabled, () -> keyFor(Minecraft.getInstance()), error ->
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("history", "Chat history background operation failed"), error));

    private ChatHistory() {}

    public static void init(Path dir) {
        HistoryStore.init(dir);
    }

    public static String currentKey() {
        return currentKey;
    }

    public static boolean enabled() {
        return AtomChatConfig.get().chatHistoryEnabled;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void onJoin(Minecraft client) {
        if (enabled() && HistoryStore.isWorldSpecific(currentKey)) saveNow(client);
        com.atom.chat.chat.NativeMessageSync.start();
        currentKey = keyFor(client);
        com.atom.chat.image.SkinResolver.clear();
        com.atom.chat.chat.RecognitionMemory.join(recognitionKeyFor(client), client::execute);
        String key = currentKey;
        HISTORY.join(key, AtomChatConfig.get().historyRetentionDays, entries -> publish(client, key, entries));
    }

    /** Take the departing world's bounded DTO snapshot before the caller clears its stores. */
    public static void onDisconnect(Minecraft client) {
        if (enabled() && HistoryStore.isWorldSpecific(currentKey)) saveNow(client);
        com.atom.chat.chat.RecognitionMemory.disconnect();
        com.atom.chat.image.SkinResolver.clear();
        HISTORY.disconnect();
        com.atom.chat.chat.NativeMessageSync.start();
        currentKey = null;
        dirty = false;
    }

    public static void tick(Minecraft client) {
        com.atom.chat.chat.RecognitionMemory.tick();
        if (!enabled() || !dirty || !HistoryStore.isWorldSpecific(currentKey)) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoSave < AUTO_SAVE_MS) return;
        lastAutoSave = now;
        saveNow(client);
    }

    /** Cached by background load/save/clear; opening settings never stats a file. */
    public static boolean hasSavedHistory() {
        return enabled() && HistoryStore.isWorldSpecific(currentKey) && HISTORY.hasSavedHistory();
    }

    public static void clearCurrent() {
        HISTORY.clear();
        com.atom.chat.chat.NativeMessageSync.start();
        dirty = false;
        ChatStore.get().reset();
        PrivateChatStore.reset();
    }

    public static void redactNow(String signature) {
        if(HistoryStore.isWorldSpecific(currentKey))saveNow(Minecraft.getInstance(),signature);
    }
    private static void saveNow(Minecraft client) {saveNow(client,null);}
    private static void saveNow(Minecraft client, String deletedSignature) {
        if (!HistoryStore.isWorldSpecific(currentKey)) return;
        // Only these selected <=500 messages touch Minecraft component/registry serialization.
        List<Entry> entries = HistorySnapshot.capture(ChatStore.get().rawSnapshot(), PrivateChatStore.knownPartners(),
                PrivateChatStore::rawMessages, (partner, message) -> {
                    String peer = partner == null ? null : peerKey(partner);
                    return toEntry(message, peer);
                });
        dirty = !(deletedSignature!=null?HISTORY.redact(entries,deletedSignature):HISTORY.save(entries));
    }

    /** Invoked by HistoryLifecycle only on the MC thread after epoch/clear/world/enabled checks. */
    private static void publish(Minecraft client, String key, List<Entry> entries) {
        if (!enabled() || !key.equals(currentKey) || !key.equals(keyFor(client))) return;
        List<ChatMessage> publicMessages = new ArrayList<>();
        Map<PlayerRef, List<ChatMessage>> privateMessages = new LinkedHashMap<>();
        for (Entry entry : entries) {
            ChatMessage message = fromEntry(entry);
            if (message == null) continue;
            if (!entry.priv) {
                publicMessages.add(message);
            } else {
                PlayerRef partner = partnerOf(entry.peer, message);
                if (partner != null) privateMessages.computeIfAbsent(partner, ignored -> new ArrayList<>()).add(message);
            }
        }
        // Prepend without resetting unread counts, active conversations, or newer live messages.
        ChatStore.get().prependLoaded(publicMessages);
        for (var conversation : privateMessages.entrySet())
            PrivateChatStore.prependLoaded(conversation.getKey(), conversation.getValue());
    }

    /** Retention scan stays on IO; the lifecycle permit guards each final unlink on the MC thread. */
    private static void cleanupOldHistory(String key, int days, HistoryLifecycle.CleanupPermit permit) throws Exception {
        if (days <= 0 || !permit.active()) return;
        Path dir = HistoryStore.baseDir();
        if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir) || !Files.isDirectory(dir)) return;
        long now = System.currentTimeMillis();
        long limit = days * 86_400_000L;
        Path current = HistoryStore.file(key);
        try (var stream = Files.list(dir)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!permit.active()) return;
                if (file.equals(current) || HistoryStore.isLegacyMultiplayerFile(file)) continue;
                try {
                    if (now - Files.getLastModifiedTime(file).toMillis() > limit) {
                        com.atom.chat.util.DataMigration.noLinks(file);
                        if (!permit.delete(file, () -> Files.deleteIfExists(file))) return;
                    }
                } catch (IOException ignored) {
                    // A locked or racing file must not fail the current world's load.
                }
            }
        } catch (IOException ignored) {
            // Best effort, as before.
        }
    }

    private static Entry toEntry(ChatMessage m, String peer) {
        String comp = encode(m.getComponent());
        String sender = m.getSenderName();
        String body = m.getContentText();
        // Persist resolved runs when the original text cannot locate them uniquely.
        boolean preserve = !m.isSystem() && (m.hasCapturedPresentation() || m.isIdentityUnknown()
                || !uniqueWithin(m.getRawText(), sender) || !uniqueWithin(m.getRawText(), body));
        String senderComp = preserve ? encode(m.getSenderRich().toText()) : null;
        String bodyComp = preserve ? encode(m.getContentRich().toText()) : null;
        UUID uuid = m.getSenderUuid();
        return new Entry(m.getTimestamp(), peer != null, peer, m.isOwn(), m.isSystem(),
                m.getQuoteName(), m.getQuoteText(),
                uuid != null ? uuid.toString() : null,
                sender, m.isIdentityUnknown() ? "" : m.getProfileName(), body, comp, senderComp, bodyComp,m.nativeMeta());
    }

    private static ChatMessage fromEntry(Entry e) {
        Component component = decode(e.componentJson);
        if (component == null) {
            // Plain-text fallback: the line is still worth showing.
            component = Component.literal(e.contentText != null ? e.contentText : "");
        }
        UUID uuid = null;
        if (e.senderUuid != null) {
            try {
                uuid = UUID.fromString(e.senderUuid);
            } catch (IllegalArgumentException ignored) {
                uuid = null;
            }
        }
        Component sender = decode(e.senderComponentJson);
        Component body = decode(e.bodyComponentJson);
        ChatMessage restored = new ChatMessage(component, e.own, e.system, e.quoteName, e.quoteText,
                uuid, e.senderName, e.profileName, e.contentText,
                sender == null ? null : RichText.of(sender), body == null ? null : RichText.of(body), e.timestamp).retainPresentation(sender != null || body != null).withNativeMeta(e.nativeMeta);
        return com.atom.chat.chat.NativeMessageSync.loaded(restored,com.atom.chat.chat.NativeMessageBridge.tick());
    }

    private static boolean uniqueWithin(String line, String part) {
        if (part == null || part.isEmpty()) return true;
        int first = line.indexOf(part);
        return first >= 0 && line.indexOf(part, first + 1) < 0;
    }

    public static String encodeNative(Component value){return encode(value);}
    public static Component decodeNative(String value){return decode(value);}

    private static String encode(Component value) {
        if (value == null) return null;
        try {
            return Component.Serializer.toJson(value);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static Component decode(String json) {
        if (json == null) return null;
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /**
     * Rebuilds the conversation partner from the persisted peer key: a UUID
     * string when it was known, otherwise the profile name (offline players and
     * bot-bridge relayed lines often have no UUID).
     */
    private static PlayerRef partnerOf(String peer, ChatMessage m) {
        if (peer == null) {
            return null;
        }
        UUID uuid = null;
        try {
            uuid = UUID.fromString(peer);
        } catch (IllegalArgumentException ignored) {
            uuid = null;
        }
        String name = uuid != null ? m.getProfileName() : peer;
        if (name == null) {
            name = m.getSenderName();
        }
        if (name == null) {
            return null;
        }
        return PlayerRef.of(uuid, name);
    }

    private static String peerKey(PlayerRef partner) {
        return partner.uuid() != null ? partner.uuid().toString() : partner.realName();
    }

    private static String recognitionKeyFor(Minecraft client) {
        if (client.getSingleplayerServer() != null) return com.atom.chat.chat.RecognitionServerKey.singleplayer(client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        return client.getCurrentServer() == null ? null
                : com.atom.chat.chat.RecognitionServerKey.multiplayer(client.getCurrentServer().ip);
    }

    private static String keyFor(Minecraft client) {
        boolean singleplayer = client.getSingleplayerServer() != null;
        String levelName = null;
        if (singleplayer) {
            try {
                levelName = client.getSingleplayerServer().getWorldData().getLevelName();
            } catch (Exception ignored) {
                levelName = null;
            }
        }
        var server = client.getCurrentServer();
        return HistoryStore.keyFor(singleplayer, levelName, server == null ? null : server.ip);
    }
}
