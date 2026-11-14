package com.atom.chat.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Fetch registration and the delayed client-thread send share the upload session's epoch lock. */
public final class MediaFetchRequests {
    private static final int MAX_MEDIA_BYTES = 64 * 1024 * 1024;
    private final MediaUploadScheduler session;
    private final Executor client;
    private final Consumer<String> sender;
    private final Map<String, Request> requests = new HashMap<>();

    public MediaFetchRequests(MediaUploadScheduler session, Executor client, Consumer<String> sender) {
        this.session = session;
        this.client = client;
        this.sender = sender;
    }

    public CompletableFuture<byte[]> request(long epoch, String id) {
        synchronized (session) {
            if (!session.isCurrent(epoch)) return CompletableFuture.failedFuture(new IOException("disconnected"));
            if (requests.containsKey(id)) return CompletableFuture.failedFuture(new IOException("Duplicate media fetch: " + id));
            Request request = new Request(epoch);
            requests.put(id, request);
            try {
                client.execute(() -> {
                    synchronized (session) {
                        if (requests.get(id) != request) return;
                        if (!session.isCurrent(request.epoch)) {
                            fail(id, request, "disconnected");
                            return;
                        }
                        try {
                            sender.accept(id);
                        } catch (RuntimeException e) {
                            fail(id, request, "disconnected");
                        }
                    }
                });
            } catch (RuntimeException e) {
                fail(id, request, "cancelled");
            }
            return request.future;
        }
    }

    public void cancel(String id, CompletableFuture<byte[]> future) {
        synchronized (session) {
            Request request = requests.get(id);
            if (request != null && request.future == future) fail(id, request, "cancelled");
        }
    }

    public void reset() {
        synchronized (session) {
            var old = new java.util.ArrayList<>(requests.values());
            requests.clear();
            for (Request request : old) request.future.completeExceptionally(new IOException("disconnected"));
        }
    }

    public void onData(String id, int offset, int totalBytes, byte[] chunk) {
        synchronized (session) {
            Request request = requests.get(id);
            if (request == null || !session.isCurrent(request.epoch)) return;
            if (request.buffer == null) {
                request.total = totalBytes;
                request.buffer = new MediaUploadBuffer(totalBytes, MAX_MEDIA_BYTES);
            }
            if (request.total != totalBytes || !request.buffer.write(offset, chunk)) {
                fail(id, request, "Invalid media chunk for " + id);
            } else if (request.buffer.isComplete()) {
                requests.remove(id);
                request.future.complete(request.buffer.bytes());
            }
        }
    }

    public void onMissing(String id) {
        synchronized (session) {
            Request request = requests.get(id);
            if (request != null && session.isCurrent(request.epoch)) fail(id, request, "Media not found: " + id);
        }
    }

    private void fail(String id, Request request, String reason) {
        requests.remove(id, request);
        request.future.completeExceptionally(new IOException(reason));
    }

    private static final class Request {
        final long epoch;
        final CompletableFuture<byte[]> future = new CompletableFuture<>();
        MediaUploadBuffer buffer;
        int total;
        Request(long epoch) { this.epoch = epoch; }
    }
}
