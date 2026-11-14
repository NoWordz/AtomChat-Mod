package com.atom.chat.net;

import com.atom.chat.image.ImageLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Connection-scoped media transfers; shared scheduling keeps the three loaders equivalent. */
public final class MediaCompanionClient {
    private MediaCompanionClient() {}

    public record State(boolean enabled, int maxBytes) {}

    private static final long FETCH_TIMEOUT_MS = 15_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AtomChat-MediaUpload");
        thread.setDaemon(true);
        return thread;
    });
    private static final MediaUploadScheduler UPLOADS = new MediaUploadScheduler(IO,
            MediaUploadScheduler::readFile, new MediaUploadScheduler.Sender() {
        public void start(UUID id, String name, int size) {
            ensureConnection();
            MediaPayloads.CHANNEL.sendToServer(new MediaPayloads.UploadStart(id, name, size));
        }
        public void chunk(UUID id, int offset, byte[] bytes) {
            ensureConnection();
            MediaPayloads.CHANNEL.sendToServer(new MediaPayloads.UploadChunk(id, offset, bytes));
        }
        public void finish(UUID id) {
            ensureConnection();
            MediaPayloads.CHANNEL.sendToServer(new MediaPayloads.UploadFinish(id));
        }
    }, System::currentTimeMillis);
    private static final MediaFetchRequests FETCHES = new MediaFetchRequests(UPLOADS,
            runnable -> Minecraft.getInstance().execute(runnable), id -> {
        ensureConnection();
        MediaPayloads.CHANNEL.sendToServer(new MediaPayloads.Fetch(id));
    });
    private static volatile State state;
    private static volatile boolean stateRequested;
    private static volatile Object activeConnection;

    public static void init() {
        ImageLoader.setMediaFetcher(MediaCompanionClient::fetchBlocking);
    }

    /** Public so external-host callers can reject a result after changing servers. */
    public static long connectionEpoch() {
        return UPLOADS.connectionEpoch();
    }

    public static void onJoin() {
        synchronized (UPLOADS) {
            state = null;
            stateRequested = false;
            activeConnection = Minecraft.getInstance().getConnection();
            UPLOADS.reset(true);
            FETCHES.reset();
        }
        com.atom.chat.image.ImageUploader.cancelInvalidShared();
        ImageLoader.get().invalidateMedia();
        requestState();
    }

    public static void onDisconnect() {
        synchronized (UPLOADS) {
            state = null;
            stateRequested = false;
            activeConnection = null;
            UPLOADS.reset(false);
            FETCHES.reset();
        }
        com.atom.chat.image.ImageUploader.cancelInvalidShared();
        ImageLoader.get().invalidateMedia();
    }

    /** Invoked on the client thread: at most four 24 KiB data chunks in total. */
    public static void tick() {
        if (activeConnection != null && activeConnection != Minecraft.getInstance().getConnection()) onDisconnect();
        UPLOADS.tick();
    }

    public static boolean hostingStateKnown() {
        return state != null || !serverSupportsMedia();
    }

    public static boolean hostingEnabled() {
        State current = state;
        if (current != null) return current.enabled();
        if (serverSupportsMedia()) requestState();
        return false;
    }

    public static boolean avatarHostingAllowed() {
        State current = state;
        return current == null || current.enabled();
    }

    /** Admission and the connection snapshot are atomic; no disk work runs here. */
    public static void uploadFile(Path file, Consumer<String> onSuccess, Consumer<String> onError) {
        synchronized (UPLOADS) {
            long epoch = connectionEpoch();
            if (!UPLOADS.isCurrent(epoch) || activeConnection == null || activeConnection != Minecraft.getInstance().getConnection()) {
                onError.accept("disconnected");
                return;
            }
            State current = state;
            if (current == null || !current.enabled()) {
                onError.accept("hosting_disabled");
                return;
            }
            UPLOADS.upload(epoch, file, current.maxBytes(), onSuccess, onError);
        }
    }

    static void onState(boolean enabled, int maxBytes) {
        synchronized (UPLOADS) {
            if (activeConnection == null || activeConnection != Minecraft.getInstance().getConnection()) return;
            State previous = state;
            state = new State(enabled, maxBytes);
            if (!enabled && (previous == null || previous.enabled())) {
                com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("media",
                        "This server has AtomChat media hosting switched off "
                        + "(hostingEnabled=false in its atomchat-server.json); images you send will be "
                        + "uploaded to the public image host instead of being stored on the server"));
            }
        }
    }

    static void onUploadResult(MediaPayloads.UploadResult payload) {
        synchronized (UPLOADS) {
            if (activeConnection == null || activeConnection != Minecraft.getInstance().getConnection() || payload.uploadId() == null) return;
            String error = payload.error();
            UPLOADS.onResult(connectionEpoch(), payload.uploadId(),
                    error == null || error.isEmpty() ? MediaIds.urlFor(payload.mediaId()) : null, error);
        }
    }

    private static byte[] fetchBlocking(String url) throws Exception {
        long epoch = connectionEpoch();
        String mediaId = MediaIds.idOf(url);
        if (mediaId == null || !MediaIds.isSafeId(mediaId)) throw new IOException("Bad media url: " + url);
        CompletableFuture<byte[]> future;
        synchronized (UPLOADS) {
            if (!UPLOADS.isCurrent(epoch) || activeConnection == null || activeConnection != Minecraft.getInstance().getConnection())
                throw new IOException("disconnected");
            State current = state;
            if (current == null || !current.enabled()) throw new IOException("Server media hosting is not enabled");
            future = FETCHES.request(epoch, mediaId);
        }
        try {
            return future.get(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            FETCHES.cancel(mediaId, future);
        }
    }

    static void onData(MediaPayloads.Data payload) {
        synchronized (UPLOADS) {
            if (activeConnection != null && activeConnection == Minecraft.getInstance().getConnection())
                FETCHES.onData(payload.mediaId(), payload.offset(), payload.totalBytes(), payload.data());
        }
    }

    static void onMissing(MediaPayloads.Missing payload) {
        synchronized (UPLOADS) {
            if (activeConnection != null && activeConnection == Minecraft.getInstance().getConnection()) FETCHES.onMissing(payload.mediaId());
        }
    }

    private static boolean serverSupportsMedia() {
        try {
            if (MediaPayloads.CHANNEL == null) return false;
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            return connection != null && connection.getConnection() != null
                    && MediaPayloads.CHANNEL.isRemotePresent(connection.getConnection());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void requestState() {
        synchronized (UPLOADS) {
            if (stateRequested || activeConnection == null || !serverSupportsMedia()) return;
            stateRequested = true;
            long epoch = connectionEpoch();
            Minecraft.getInstance().execute(() -> UPLOADS.runIfCurrent(epoch, () -> {
                if (activeConnection == Minecraft.getInstance().getConnection()) MediaPayloads.CHANNEL.sendToServer(new MediaPayloads.StateRequest());
            }));
        }
    }

    private static void ensureConnection() {
        if (activeConnection == null || activeConnection != Minecraft.getInstance().getConnection()) throw new IllegalStateException("disconnected");
    }

    /** Origin is the NetworkEvent context's Connection, captured before enqueueWork. */
    static void receive(Object origin, Consumer<Runnable> enqueue, Runnable receiver) {
        MediaReceiver.enqueue(origin, UPLOADS, enqueue, () -> {
            Object active = activeConnection;
            return active == Minecraft.getInstance().getConnection() && active instanceof ClientPacketListener listener
                    ? listener.getConnection() : null;
        }, () -> {
            ClientPacketListener current = Minecraft.getInstance().getConnection();
            return current == null ? null : current.getConnection();
        }, receiver);
    }

}
