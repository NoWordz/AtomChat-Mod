package com.atom.chat.chat;

import com.atom.chat.AtomChat;
import com.atom.chat.util.CacheDirs;
import com.atom.chat.util.DataMigration;
import com.google.gson.Gson;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Server-isolated confirmed identities and envelopes. File IO never blocks message delivery. */
public final class RecognitionMemory {
    private record Saved(List<SeenPlayers.Seen> identities, List<LearnedChatFormats.Format> formats, List<ConfirmedSkinProfiles.Profile> skins) {}
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "AtomChat-recognition-memory"); thread.setDaemon(true); return thread;
    });
    private static final Gson JSON = new Gson();
    private static long epoch, lastSave;
    private static Path file;
    private static boolean loaded;
    private RecognitionMemory() {}
    public static synchronized void join(String serverKey, Consumer<Runnable> clientThread) {
        disconnect();
        SeenPlayers.clear(); LearnedChatFormats.clear(); ConfirmedSkinProfiles.clear();
        if (serverKey == null || serverKey.isBlank()) return;
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serverKey.getBytes(StandardCharsets.UTF_8)));
            file = CacheDirs.cacheRoot().resolve("recognition").resolve(hash + ".json");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        loaded = false;
        long token = epoch; Path source = file;
        IO.execute(() -> {
            Saved saved = null;
            try {
                DataMigration.noLinks(source);
                if (Files.isRegularFile(source) && Files.size(source) <= 8_388_608)
                    saved = JSON.fromJson(Files.readString(source), Saved.class);
            } catch (Exception error) { AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("capture", "Unable to load confirmed chat recognition memory"), error); }
            Saved incoming = saved;
            clientThread.accept(() -> { synchronized (RecognitionMemory.class) {
                if (token != epoch || !Objects.equals(source, file)) return;
                if (incoming != null) {
                    SeenPlayers.restore(incoming.identities()); LearnedChatFormats.restore(incoming.formats());
                    ConfirmedSkinProfiles.restore(incoming.skins());
                }
                loaded = true;
            }});
        });
    }

    public static synchronized void tick() {
        OutgoingMessages.expire(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        if (file != null && now - lastSave > 30_000) { lastSave = now; save(); }
    }
    private static void save() {
        if (file == null || !loaded) return;
        Path target = file;
        Saved saved = new Saved(SeenPlayers.snapshot(), LearnedChatFormats.snapshot(), ConfirmedSkinProfiles.snapshot());
        IO.execute(() -> {
            Path temporary = null;
            try {
                if (!DataMigration.allowPersonalPath(target)) return;
                DataMigration.noLinks(target); Files.createDirectories(target.getParent());
                temporary = Files.createTempFile(target.getParent(), "recognition-", ".tmp");
                Files.writeString(temporary, JSON.toJson(saved));
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (Exception error) { AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("capture", "Unable to save confirmed chat recognition memory"), error); }
            finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {} }
        });
    }
    public static synchronized void disconnect() {
        save(); file = null; epoch++; lastSave = 0; loaded = false;
        SeenPlayers.clear(); LearnedChatFormats.clear(); ConfirmedSkinProfiles.clear();
        OutgoingMessages.clear();
    }
    /** Clears only this server's learned data; old asynchronous reads cannot republish it. */
    public static synchronized void clearCurrent() {
        epoch++; loaded = true; SeenPlayers.clear(); LearnedChatFormats.clear(); ConfirmedSkinProfiles.clear(); save();
    }
}
