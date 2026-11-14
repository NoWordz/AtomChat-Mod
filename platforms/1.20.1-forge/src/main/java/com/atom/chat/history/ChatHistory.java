package com.atom.chat.history;

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
        currentKey = keyFor(client);
        String key = currentKey;
        HISTORY.join(key, AtomChatConfig.get().historyRetentionDays, entries -> publish(client, key, entries));
    }

    /** Take the departing world's bounded DTO snapshot before the caller clears its stores. */
    public static void onDisconnect(Minecraft client) {
        if (enabled() && HistoryStore.isWorldSpecific(currentKey)) saveNow(client);
        HISTORY.disconnect();
        currentKey = null;
        dirty = false;
    }

    public static void tick(Minecraft client) {
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
        dirty = false;
        ChatStore.get().reset();
        PrivateChatStore.reset();
    }

    private static void saveNow(Minecraft client) {
        if (!HistoryStore.isWorldSpecific(currentKey)) return;
        // Only these selected <=500 messages touch Minecraft component/registry serialization.
        List<Entry> entries = HistorySnapshot.capture(ChatStore.get().snapshot(), PrivateChatStore.knownPartners(),
                PrivateChatStore::messages, (partner, message) -> {
                    String peer = partner == null ? null : peerKey(partner);
                    return toEntry(message, peer);
                });
        dirty = !HISTORY.save(entries);
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
                if (file.equals(current)) continue;
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
        String comp = null;
        if (m.getComponent() != null) {
            try {
                comp = Component.Serializer.toJson(m.getComponent());
            } catch (Exception e) {
                comp = null;
            }
        }
        UUID uuid = m.getSenderUuid();
        return new Entry(m.getTimestamp(), peer != null, peer, m.isOwn(), m.isSystem(),
                m.getQuoteName(), m.getQuoteText(),
                uuid != null ? uuid.toString() : null,
                m.getSenderName(), m.getProfileName(), m.getContentText(), comp);
    }

    private static ChatMessage fromEntry(Entry e) {
        Component component = null;
        if (e.componentJson != null) {
            try {
                component = Component.Serializer.fromJson(e.componentJson);
            } catch (Exception ignored) {
                component = null;
            }
        }
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
        return new ChatMessage(component, e.own, e.system, e.quoteName, e.quoteText,
                uuid, e.senderName, e.profileName, e.contentText, e.timestamp);
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
        String serverName = null;
        if (client.getCurrentServer() != null) {
            serverName = client.getCurrentServer().name;
        }
        return HistoryStore.keyFor(singleplayer, levelName, serverName);
    }
}
