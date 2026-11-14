package com.atom.chat.net;

import com.atom.chat.image.ImageLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

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
            ClientPlayNetworking.send(new MediaPayloads.UploadStart(id, name, size));
        }
        public void chunk(UUID id, int offset, byte[] bytes) {
            ensureConnection();
            ClientPlayNetworking.send(new MediaPayloads.UploadChunk(id, offset, bytes));
        }
        public void finish(UUID id) {
            ensureConnection();
            ClientPlayNetworking.send(new MediaPayloads.UploadFinish(id));
        }
    }, System::currentTimeMillis);
    private static final MediaFetchRequests FETCHES = new MediaFetchRequests(UPLOADS,
            runnable -> MinecraftClient.getInstance().execute(runnable), id -> {
        ensureConnection();
        ClientPlayNetworking.send(new MediaPayloads.Fetch(id));
    });
    private static volatile State state;
    private static volatile boolean stateRequested;
    private static volatile Object activeConnection;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MediaPayloads.MediaState.ID,
                (payload, context) -> receive(context.player().networkHandler,
                        () -> onState(payload.enabled(), payload.maxBytes())));
        ClientPlayNetworking.registerGlobalReceiver(MediaPayloads.UploadResult.ID,
                (payload, context) -> receive(context.player().networkHandler, () -> onUploadResult(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MediaPayloads.Data.ID,
                (payload, context) -> receive(context.player().networkHandler, () -> onData(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MediaPayloads.Missing.ID,
                (payload, context) -> receive(context.player().networkHandler, () -> onMissing(payload)));
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
            activeConnection = MinecraftClient.getInstance().getNetworkHandler();
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
        if (activeConnection != null && activeConnection != MinecraftClient.getInstance().getNetworkHandler()) onDisconnect();
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
            if (!UPLOADS.isCurrent(epoch) || activeConnection == null || activeConnection != MinecraftClient.getInstance().getNetworkHandler()) {
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
            if (activeConnection == null || activeConnection != MinecraftClient.getInstance().getNetworkHandler()) return;
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

    private static void onUploadResult(MediaPayloads.UploadResult payload) {
        synchronized (UPLOADS) {
            if (activeConnection == null || activeConnection != MinecraftClient.getInstance().getNetworkHandler() || payload.uploadId() == null) return;
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
            if (!UPLOADS.isCurrent(epoch) || activeConnection == null || activeConnection != MinecraftClient.getInstance().getNetworkHandler())
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

    private static void onData(MediaPayloads.Data payload) {
        synchronized (UPLOADS) {
            if (activeConnection != null && activeConnection == MinecraftClient.getInstance().getNetworkHandler())
                FETCHES.onData(payload.mediaId(), payload.offset(), payload.totalBytes(), payload.data());
        }
    }

    private static void onMissing(MediaPayloads.Missing payload) {
        synchronized (UPLOADS) {
            if (activeConnection != null && activeConnection == MinecraftClient.getInstance().getNetworkHandler()) FETCHES.onMissing(payload.mediaId());
        }
    }

    private static boolean serverSupportsMedia() {
        try {
            return ClientPlayNetworking.canSend(MediaPayloads.StateRequest.ID);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void requestState() {
        synchronized (UPLOADS) {
            if (stateRequested || activeConnection == null || !serverSupportsMedia()) return;
            stateRequested = true;
            long epoch = connectionEpoch();
            MinecraftClient.getInstance().execute(() -> UPLOADS.runIfCurrent(epoch, () -> {
                if (activeConnection == MinecraftClient.getInstance().getNetworkHandler()) ClientPlayNetworking.send(new MediaPayloads.StateRequest());
            }));
        }
    }

    private static void ensureConnection() {
        if (activeConnection == null || activeConnection != MinecraftClient.getInstance().getNetworkHandler()) throw new IllegalStateException("disconnected");
    }

    /** Capture the originating handler before the extra client-thread hop. */
    private static void receive(Object origin, Runnable receiver) {
        long epoch = connectionEpoch();
        MinecraftClient.getInstance().execute(() -> UPLOADS.runIfCurrent(epoch, () -> {
            if (origin == activeConnection && origin == MinecraftClient.getInstance().getNetworkHandler()) receiver.run();
        }));
    }

}
