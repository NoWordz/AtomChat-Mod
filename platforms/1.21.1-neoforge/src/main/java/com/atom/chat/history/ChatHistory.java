package com.atom.chat.history;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.history.HistoryStore.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates chat-history persistence: when to adopt a world key, when to
 * write, and how loaded lines are funnelled back into the public/private
 * stores. The file format itself lives in {@link HistoryStore}.
 *
 * <p>Off by default ({@code chatHistoryEnabled}): with the switch off nothing is
 * ever written and leaving a server still clears memory, so the shipped
 * behaviour is unchanged.
 *
 * <p>Model: memory is still cleared on disconnect (no cross-server residue);
 * persistence only decides whether a world's history is <em>loaded</em> when
 * you join it. Saving happens on disconnect, on world change and at most every
 * {@value #AUTO_SAVE_MS} ms while chatting.
 */
public final class ChatHistory {
    private static final long AUTO_SAVE_MS = 30_000L;
    /** Mirrors the in-memory cap: no point persisting more than the store keeps. */
    private static final int MAX_LINES = 500;

    private static final ExecutorService SAVE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atomchat-history-save");
        t.setDaemon(true);
        return t;
    });

    private static volatile String currentKey;
    private static volatile boolean dirty;
    private static volatile long lastAutoSave;
    /**
     * Bumped whenever the history is cleared. A save snapshot taken before the
     * bump is skipped when its write task comes back, so an auto-save can never
     * resurrect a history the user just deleted.
     */
    private static volatile int generation;

    private ChatHistory() {
    }

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

    /** Adopts the world key for the connection that just started. */
    public static void onJoin(Minecraft client) {
        if (enabled() && HistoryStore.isWorldSpecific(currentKey)) {
            saveNow(client);
        }
        currentKey = keyFor(client);
        if (!enabled() || !HistoryStore.isWorldSpecific(currentKey)) {
            return;
        }
        cleanupOldHistory();
        loadInto(client, currentKey);
    }

    /** Writes the world that is being left; the caller clears memory afterwards. */
    public static void onDisconnect(Minecraft client) {
        if (enabled() && HistoryStore.isWorldSpecific(currentKey)) {
            saveNow(client);
        }
        currentKey = null;
        dirty = false;
    }

    public static void tick(Minecraft client) {
        if (!enabled() || !dirty || !HistoryStore.isWorldSpecific(currentKey)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoSave < AUTO_SAVE_MS) {
            return;
        }
        lastAutoSave = now;
        dirty = false;
        saveNow(client);
    }

    /** Whether the current world has a saved history file on disk. */
    public static boolean hasSavedHistory() {
        if (!enabled() || !HistoryStore.isWorldSpecific(currentKey)) {
            return false;
        }
        return Files.exists(HistoryStore.file(currentKey));
    }

    /** Clears memory for the current world and deletes its history file. */
    public static void clearCurrent() {
        // Any in-flight save must be invalidated before it writes.
        generation++;
        dirty = false;
        ChatStore.get().reset();
        PrivateChatStore.reset();
        String key = currentKey;
        if (!enabled() || !HistoryStore.isWorldSpecific(key)) {
            return;
        }
        final int gen = generation;
        Path file = HistoryStore.file(key);
        SAVE.execute(() -> {
            if (gen != generation) {
                return;
            }
            try {
                com.atom.chat.util.DataMigration.noLinks(file);
                Files.deleteIfExists(file);
            } catch (IOException e) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("history", "Failed to delete chat history {}"), file, e);
            }
        });
    }

    private static void saveNow(Minecraft client) {
        String key = currentKey;
        if (key == null) {
            return;
        }
        net.minecraft.core.HolderLookup.Provider lookup = lookup(client);
        List<String> lines = new ArrayList<>();
        for (ChatMessage m : ChatStore.get().snapshot()) {
            String line = HistoryStore.toLine(toEntry(m, lookup, null));
            if (line != null) {
                lines.add(line);
            }
        }
        for (PlayerRef partner : PrivateChatStore.knownPartners()) {
            String peer = peerKey(partner);
            for (ChatMessage m : PrivateChatStore.messages(partner)) {
                String line = HistoryStore.toLine(toEntry(m, lookup, peer));
                if (line != null) {
                    lines.add(line);
                }
            }
        }
        List<String> out = lines;
        if (out.size() > MAX_LINES) {
            out = new ArrayList<>(out.subList(out.size() - MAX_LINES, out.size()));
        }
        final List<String> finalLines = out;
        dirty = false;
        final int gen = generation;
        Path file = HistoryStore.file(key);
        SAVE.execute(() -> {
            if (gen != generation) {
                return;
            }
            try {
                com.atom.chat.util.DataMigration.noLinks(file);
                Files.createDirectories(file.getParent());
                Files.write(file, finalLines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("history", "Failed to write chat history {}"), file, e);
            }
        });
    }

    private static void loadInto(Minecraft client, String key) {
        Path file = HistoryStore.file(key);
        if (!Files.exists(file)) {
            return;
        }
        List<String> raw;
        try {
            raw = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("history", "Failed to read chat history {}"), file, e);
            return;
        }
        net.minecraft.core.HolderLookup.Provider lookup = lookup(client);
        List<ChatMessage> loadedPublic = new ArrayList<>();
        List<Loaded> loadedPrivate = new ArrayList<>();
        for (String line : raw) {
            Entry e = HistoryStore.fromLine(line);
            if (e == null) {
                continue;
            }
            ChatMessage m = fromEntry(e, lookup);
            if (m == null) {
                continue;
            }
            if (e.priv) {
                loadedPrivate.add(new Loaded(m, e.peer));
            } else {
                loadedPublic.add(m);
            }
        }
        if (loadedPublic.size() > MAX_LINES) {
            loadedPublic = new ArrayList<>(loadedPublic.subList(loadedPublic.size() - MAX_LINES, loadedPublic.size()));
        }
        // Messages that arrived before the key was known (MOTD, join notices)
        // must stay newest, so they are re-appended under the loaded history.
        List<ChatMessage> early = new ArrayList<>(ChatStore.get().snapshot());
        ChatStore.get().reset();
        ChatStore.get().addLoaded(loadedPublic);
        ChatStore.get().addLoaded(early);
        for (Loaded l : loadedPrivate) {
            PlayerRef partner = partnerOf(l.peer(), l.message());
            if (partner != null) {
                PrivateChatStore.addLoaded(partner, List.of(l.message()));
            }
        }
    }

    /** A loaded private message plus the partner key it was filed under. */
    private record Loaded(ChatMessage message, String peer) {
    }

    private static void cleanupOldHistory() {
        int days = AtomChatConfig.get().historyRetentionDays;
        if (days <= 0) {
            return;
        }
        Path dir = HistoryStore.baseDir();
        if (!com.atom.chat.util.DataMigration.allowPersonalPath(dir) || !Files.isDirectory(dir)) {
            return;
        }
        long now = System.currentTimeMillis();
        long limit = days * 86_400_000L;
        Path current = HistoryStore.file(currentKey);
        try (var stream = Files.list(dir)) {
            for (Path f : (Iterable<Path>) stream::iterator) {
                if (f.equals(current)) {
                    continue;
                }
                try {
                    if (now - Files.getLastModifiedTime(f).toMillis() > limit) {
                        com.atom.chat.util.DataMigration.noLinks(f);
                        Files.deleteIfExists(f);
                    }
                } catch (IOException ignored) {
                    // A locked or racing file is not worth failing the join over.
                }
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    private static Entry toEntry(ChatMessage m, net.minecraft.core.HolderLookup.Provider lookup, String peer) {
        String comp = null;
        if (lookup != null && m.getComponent() != null) {
            try {
                comp = Component.Serializer.toJson(m.getComponent(), lookup);
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

    private static ChatMessage fromEntry(Entry e, net.minecraft.core.HolderLookup.Provider lookup) {
        Component component = null;
        if (e.componentJson != null && lookup != null) {
            try {
                component = Component.Serializer.fromJson(e.componentJson, lookup);
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

    private static net.minecraft.core.HolderLookup.Provider lookup(Minecraft client) {
        if (client == null) {
            return null;
        }
        try {
            if (client.level != null) {
                return client.level.registryAccess();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }
}
