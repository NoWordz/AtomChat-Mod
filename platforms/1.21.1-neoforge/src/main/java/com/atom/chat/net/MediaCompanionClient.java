package com.atom.chat.net;

import com.atom.chat.image.ImageLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Client side of the media companion (0.2.7).
 *
 * <p>Flow: on join the client probes the server's media channel and asks for
 * {@code MediaState}. An image that the draft is about to send is read off the
 * render thread, chunked to the server and the returned content-addressed id
 * becomes a {@code atomchat-media:<id>} CICode. Rendering that url goes through
 * {@link ImageLoader}'s media seam, which blocks the loader thread on a chunked
 * {@code Fetch} reply.
 *
 * <p>Unknown state always degrades to the external image host (uguu), matching
 * the companion philosophy: no unknown payloads, no silent breakage.
 *
 * <p>NeoForge registers the S2C receivers centrally in
 * {@link MediaPayloads#register} and calls {@link #onState} /
 * {@link #onUploadResult} / {@link #onData} / {@link #onMissing} on the client
 * thread; {@link #init()} only wires the loader's media seam.
 */
public final class MediaCompanionClient {
    private MediaCompanionClient() {
    }

    public record State(boolean enabled, int maxBytes) {
    }

    private static final long FETCH_TIMEOUT_MS = 15_000L;
    private static final long UPLOAD_TIMEOUT_MS = 20_000L;
    private static final int MAX_PENDING_UPLOADS = 8;
    private static final int MAX_MEDIA_BYTES = 64 * 1024 * 1024;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AtomChat-MediaUpload");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile State state;
    private static volatile boolean stateRequested;

    private static final Map<UUID, PendingUpload> uploads = new ConcurrentHashMap<>();
    private static final Map<String, PendingFetch> fetches = new ConcurrentHashMap<>();

    /** Client init: points {@link ImageLoader} at this companion for media urls. */
    public static void init() {
        ImageLoader.setMediaFetcher(MediaCompanionClient::fetchBlocking);
    }

    public static void onJoin() {
        state = null;
        stateRequested = false;
        failAll("disconnected");
        requestState();
    }

    public static void onDisconnect() {
        state = null;
        stateRequested = false;
        failAll("disconnected");
    }

    /** Called from the client tick so a silent server cannot pin a draft forever. */
    public static void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingUpload> entry : uploads.entrySet()) {
            PendingUpload pending = entry.getValue();
            if (pending.deadlineMs < now && uploads.remove(entry.getKey(), pending)) {
                pending.onError.accept("timeout");
            }
        }
    }

    /**
     * True only once the server confirmed hosting is enabled. Unknown returns
     * false so the caller falls back to uguu rather than silently dropping an
     * image.
     */
    public static boolean hostingStateKnown() {
        return state != null || !serverSupportsMedia();
    }

    public static boolean hostingEnabled() {
        State current = state;
        if (current != null) {
            return current.enabled();
        }
        if (serverSupportsMedia()) {
            requestState();
        }
        return false;
    }

    /**
     * Avatars keep the legacy "assume yes until told otherwise" behaviour: the
     * server still enforces the master switch, this only avoids pointless
     * traffic once the state is known.
     */
    public static boolean avatarHostingAllowed() {
        State current = state;
        return current == null || current.enabled();
    }

    /** Reads the file off the render thread, then uploads it to the server. */
    public static void uploadFile(Path file, Consumer<String> onSuccess, Consumer<String> onError) {
        State current = state;
        if (current == null || !current.enabled()) {
            onError.accept("hosting_disabled");
            return;
        }
        if (uploads.size() >= MAX_PENDING_UPLOADS) {
            onError.accept("busy");
            return;
        }
        IO.execute(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file);
                upload(bytes, file.getFileName().toString(), onSuccess, onError);
            } catch (Exception e) {
                onError.accept("io");
            }
        });
    }

    private static void upload(byte[] bytes, String name, Consumer<String> onSuccess, Consumer<String> onError) {
        State current = state;
        if (current == null || !current.enabled()) {
            onError.accept("hosting_disabled");
            return;
        }
        if (bytes == null || bytes.length == 0) {
            onError.accept("empty");
            return;
        }
        if (bytes.length > current.maxBytes()) {
            // The caller falls back to the external host for oversize files.
            onError.accept("too_large");
            return;
        }
        UUID uploadId = UUID.randomUUID();
        uploads.put(uploadId, new PendingUpload(onSuccess, onError,
                System.currentTimeMillis() + UPLOAD_TIMEOUT_MS));
        send(() -> {
            PacketDistributor.sendToServer(new MediaPayloads.UploadStart(uploadId, name, bytes.length));
            for (int offset = 0; offset < bytes.length; offset += MediaIds.CHUNK_BYTES) {
                int len = Math.min(MediaIds.CHUNK_BYTES, bytes.length - offset);
                PacketDistributor.sendToServer(new MediaPayloads.UploadChunk(
                        uploadId, offset, Arrays.copyOfRange(bytes, offset, offset + len)));
            }
            PacketDistributor.sendToServer(new MediaPayloads.UploadFinish(uploadId));
        });
    }

    /** S2C receiver; already on the client thread via the payload context. */
    static void onState(boolean enabled, int maxBytes) {
        State previous = state;
        state = new State(enabled, maxBytes);
        // Degradation, logged once per join: with hosting off every upload goes
        // to the public image host instead. The player only ever sees "my image
        // ended up on some website", so the reason has to be in the log. The
        // previous-state comparison keeps a repeated state packet from spamming.
        if (!enabled && (previous == null || previous.enabled())) {
            com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("media", "This server has AtomChat media hosting switched off "
                    + "(hostingEnabled=false in its atomchat-server.json); images you send will be "
                    + "uploaded to the public image host instead of being stored on the server"));
        }
    }

    /** S2C receiver; already on the client thread via the payload context. */
    static void onUploadResult(MediaPayloads.UploadResult payload) {
        PendingUpload pending = payload.uploadId() == null ? null : uploads.remove(payload.uploadId());
        if (pending == null) {
            return;
        }
        String error = payload.error();
        if (error != null && !error.isEmpty()) {
            pending.onError.accept(error);
        } else {
            pending.onSuccess.accept(MediaIds.urlFor(payload.mediaId()));
        }
    }

    /**
     * Blocking byte source for {@link ImageLoader}; runs on the loader's worker
     * thread, so the request itself is scheduled onto the client thread.
     */
    private static byte[] fetchBlocking(String url) throws Exception {
        String mediaId = MediaIds.idOf(url);
        if (mediaId == null || !MediaIds.isSafeId(mediaId)) {
            throw new IOException("Bad media url: " + url);
        }
        State current = state;
        if (current == null || !current.enabled()) {
            throw new IOException("Server media hosting is not enabled");
        }
        PendingFetch pending = new PendingFetch();
        if (fetches.putIfAbsent(mediaId, pending) != null) {
            throw new IOException("Duplicate media fetch: " + mediaId);
        }
        send(() -> PacketDistributor.sendToServer(new MediaPayloads.Fetch(mediaId)));
        try {
            return pending.future.get(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fetches.remove(mediaId, pending);
            throw e;
        }
    }

    /** S2C receiver; already on the client thread via the payload context. */
    static void onData(MediaPayloads.Data payload) {
        PendingFetch pending = payload.mediaId() == null ? null : fetches.get(payload.mediaId());
        if (pending == null) {
            return;
        }
        byte[] complete = pending.write(payload.offset(), payload.totalBytes(), payload.data());
        if (complete != null) {
            fetches.remove(payload.mediaId(), pending);
            pending.future.complete(complete);
        } else if (pending.failed()) {
            fetches.remove(payload.mediaId(), pending);
            pending.future.completeExceptionally(new IOException("Invalid media chunk for " + payload.mediaId()));
        }
    }

    /** S2C receiver; already on the client thread via the payload context. */
    static void onMissing(MediaPayloads.Missing payload) {
        PendingFetch pending = payload.mediaId() == null ? null : fetches.remove(payload.mediaId());
        if (pending != null) {
            pending.future.completeExceptionally(new IOException("Media not found: " + payload.mediaId()));
        }
    }

    private static void failAll(String reason) {
        for (Map.Entry<UUID, PendingUpload> entry : uploads.entrySet()) {
            PendingUpload pending = entry.getValue();
            if (uploads.remove(entry.getKey(), pending)) {
                pending.onError.accept(reason);
            }
        }
        for (Map.Entry<String, PendingFetch> entry : fetches.entrySet()) {
            PendingFetch pending = entry.getValue();
            if (fetches.remove(entry.getKey(), pending)) {
                pending.future.completeExceptionally(new IOException(reason));
            }
        }
    }

    private static boolean serverSupportsMedia() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ClientPacketListener connection = minecraft.getConnection();
            return connection != null && NetworkRegistry.hasChannel(
                    connection, MediaPayloads.StateRequest.TYPE.id());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void requestState() {
        if (stateRequested || !serverSupportsMedia()) {
            return;
        }
        stateRequested = true;
        send(() -> PacketDistributor.sendToServer(new MediaPayloads.StateRequest()));
    }

    /** Payloads are only ever sent from the client thread. */
    private static void send(Runnable sender) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(sender);
    }

    private static final class PendingUpload {
        final Consumer<String> onSuccess;
        final Consumer<String> onError;
        final long deadlineMs;

        PendingUpload(Consumer<String> onSuccess, Consumer<String> onError, long deadlineMs) {
            this.onSuccess = onSuccess;
            this.onError = onError;
            this.deadlineMs = deadlineMs;
        }
    }

    private static final class PendingFetch {
        final CompletableFuture<byte[]> future = new CompletableFuture<>();
        private byte[] data;
        private int written;
        private boolean failed;

        /** @return the assembled bytes once the last chunk landed, otherwise null. */
        synchronized byte[] write(int offset, int totalBytes, byte[] chunk) {
            if (failed || chunk == null || chunk.length == 0) {
                failed = true;
                return null;
            }
            if (data == null) {
                if (totalBytes <= 0 || totalBytes > MAX_MEDIA_BYTES) {
                    failed = true;
                    return null;
                }
                data = new byte[totalBytes];
            }
            if (offset != written || offset < 0 || (long) offset + chunk.length > data.length) {
                failed = true;
                return null;
            }
            System.arraycopy(chunk, 0, data, offset, chunk.length);
            written += chunk.length;
            return written == data.length ? data : null;
        }

        synchronized boolean failed() {
            return failed;
        }
    }
}
