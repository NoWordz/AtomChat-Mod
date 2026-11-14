package com.atom.chat.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One connection's bounded read queue and round-robin upload stream. No Minecraft dependencies. */
public final class MediaUploadScheduler {
    private static final int MAX_PENDING = 8;
    private static final long MAX_RESERVED_BYTES = 64L * 1024 * 1024;
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int CHUNKS_PER_TICK = 4;
    private static final long TIMEOUT_MS = 20_000;

    @FunctionalInterface
    public interface Reader {
        byte[] read(Path file, int maxBytes) throws Exception;
    }

    /** Called synchronously by tick, which the platform invokes on the client thread. */
    public interface Sender {
        void start(UUID id, String name, int bytes) throws Exception;
        void chunk(UUID id, int offset, byte[] data) throws Exception;
        void finish(UUID id) throws Exception;
    }

    private final Executor io;
    private final Reader reader;
    private final Sender sender;
    private final LongSupplier clock;
    private final Map<UUID, Upload> uploads = new LinkedHashMap<>();
    private final ArrayDeque<Upload> reads = new ArrayDeque<>();
    private final ArrayDeque<Upload> ready = new ArrayDeque<>();
    private long epoch;
    private boolean connected;
    private boolean readerScheduled;
    private int reservations;
    private long reservedBytes;

    public MediaUploadScheduler(Executor io, Reader reader, Sender sender, LongSupplier clock) {
        this.io = io;
        this.reader = reader;
        this.sender = sender;
        this.clock = clock;
    }

    public synchronized long connectionEpoch() {
        return epoch;
    }

    public synchronized boolean isCurrent(long expectedEpoch) {
        return connected && epoch == expectedEpoch;
    }

    /** Also used by the platform's queued state/fetch senders at their actual send boundary. */
    public synchronized boolean runIfCurrent(long expectedEpoch, Runnable action) {
        if (!isCurrent(expectedEpoch)) return false;
        action.run();
        return true;
    }

    /** Both join and disconnect advance the generation, including queued and currently reading jobs. */
    public synchronized void reset(boolean connected) {
        epoch++;
        this.connected = connected;
        for (Upload upload : new ArrayList<>(uploads.values())) {
            complete(upload, null, "disconnected");
        }
    }

    /** Reserve the server's worst-case file size BEFORE enqueueing any filesystem work. */
    public synchronized void upload(long expectedEpoch, Path file, int maxBytes,
                                    Consumer<String> success, Consumer<String> error) {
        if (!isCurrent(expectedEpoch)) {
            notify(error, "disconnected");
            return;
        }
        int limit = Math.min(maxBytes, MAX_FILE_BYTES);
        if (limit <= 0) {
            notify(error, "hosting_disabled");
            return;
        }
        if (reservations >= MAX_PENDING || reservedBytes + limit > MAX_RESERVED_BYTES) {
            notify(error, "busy");
            return;
        }
        Upload upload = new Upload(epoch, file, limit, success, error, clock.getAsLong() + TIMEOUT_MS);
        reservations++;
        reservedBytes += limit;
        uploads.put(upload.id, upload);
        reads.addLast(upload);
        scheduleReader();
    }

    /** At most one IO runnable is queued/running, even across repeated resets. */
    private void scheduleReader() {
        if (readerScheduled || reads.isEmpty()) return;
        readerScheduled = true;
        try {
            io.execute(this::readNext);
        } catch (RejectedExecutionException rejected) {
            readerScheduled = false;
            for (Upload upload : new ArrayList<>(reads)) complete(upload, null, "io");
        }
    }

    private void readNext() {
        Upload upload;
        synchronized (this) {
            upload = reads.pollFirst();
            if (upload == null) {
                readerScheduled = false;
                return;
            }
            upload.reading = true;
        }
        byte[] bytes = null;
        String failure = null;
        try {
            bytes = reader.read(upload.file, upload.limit);
            if (bytes == null || bytes.length == 0) failure = "empty";
            else if (bytes.length > upload.limit) failure = "too_large";
        } catch (ReadFailure e) {
            failure = e.getMessage();
        } catch (Exception e) {
            failure = "io";
        } finally {
            synchronized (this) {
                upload.reading = false;
                if (upload.done) {
                    // A cancelled reader can still own a large buffer until this point.
                    release(upload);
                } else if (!isCurrent(upload.epoch)) {
                    complete(upload, null, "disconnected");
                } else if (failure != null) {
                    complete(upload, null, failure);
                } else {
                    reservedBytes -= upload.reserved - bytes.length;
                    upload.reserved = bytes.length;
                    upload.bytes = bytes;
                    ready.addLast(upload);
                }
                readerScheduled = false;
                scheduleReader();
            }
        }
    }

    /** Four data chunks TOTAL per tick; each active upload gets one turn before the next. */
    public synchronized void tick() {
        long now = clock.getAsLong();
        for (Upload upload : new ArrayList<>(uploads.values())) {
            if (now >= upload.deadline) complete(upload, null, "timeout");
        }
        int chunks = 0;
        while (chunks < CHUNKS_PER_TICK && !ready.isEmpty()) {
            Upload upload = ready.removeFirst();
            if (upload.done) continue;
            if (!isCurrent(upload.epoch)) {
                complete(upload, null, "disconnected");
                continue;
            }
            try {
                if (!upload.started) {
                    upload.started = true;
                    sender.start(upload.id, upload.file.getFileName().toString(), upload.bytes.length);
                    if (upload.done) continue;
                }
                int offset = upload.offset;
                int length = Math.min(MediaIds.CHUNK_BYTES, upload.bytes.length - offset);
                byte[] chunk = Arrays.copyOfRange(upload.bytes, offset, offset + length);
                upload.offset += length;
                chunks++;
                sender.chunk(upload.id, offset, chunk);
                if (upload.done) continue;
                upload.deadline = clock.getAsLong() + TIMEOUT_MS;
                if (upload.offset == upload.bytes.length) {
                    upload.finished = true;
                    upload.bytes = null;
                    sender.finish(upload.id);
                } else {
                    ready.addLast(upload);
                }
            } catch (Exception sendFailure) {
                complete(upload, null, "disconnected");
            }
        }
    }

    public synchronized void onResult(long expectedEpoch, UUID id, String value, String error) {
        Upload upload = uploads.get(id);
        if (upload == null || upload.done || upload.epoch != expectedEpoch || !isCurrent(expectedEpoch)) return;
        if (error != null && !error.isEmpty()) complete(upload, null, error);
        else if (upload.finished) complete(upload, value, null);
    }

    private void complete(Upload upload, String value, String error) {
        if (upload.done) return;
        upload.done = true;
        uploads.remove(upload.id);
        reads.remove(upload);
        ready.remove(upload);
        upload.bytes = null;
        if (!upload.reading) release(upload);
        notify(error == null ? upload.success : upload.error, error == null ? value : error);
    }

    private void release(Upload upload) {
        if (upload.released) return;
        upload.released = true;
        reservations--;
        reservedBytes -= upload.reserved;
    }

    private static void notify(Consumer<String> callback, String value) {
        try {
            callback.accept(value);
        } catch (RuntimeException failure) {
            // One consumer must not strand every other job during a connection reset.
            System.getLogger(MediaUploadScheduler.class.getName()).log(System.Logger.Level.WARNING,
                    "Media upload callback failed", failure);
        }
    }

    /** Reject oversized/growing files without readAllBytes allocating beyond the reserved budget. */
    public static byte[] readFile(Path file, int maxBytes) throws IOException {
        long size = Files.size(file);
        if (size <= 0) throw new ReadFailure("empty");
        if (size > maxBytes) throw new ReadFailure("too_large");
        byte[] bytes = new byte[(int) size];
        try (InputStream input = Files.newInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new IOException("File changed while reading");
                offset += count;
            }
            if (input.read() != -1) throw new ReadFailure("too_large");
        }
        return bytes;
    }

    private static final class ReadFailure extends IOException {
        ReadFailure(String reason) { super(reason); }
    }

    private static final class Upload {
        final UUID id = UUID.randomUUID();
        final long epoch;
        final Path file;
        final int limit;
        final Consumer<String> success, error;
        long deadline;
        int reserved, offset;
        boolean reading, started, finished, done, released;
        byte[] bytes;
        Upload(long epoch, Path file, int limit, Consumer<String> success, Consumer<String> error, long deadline) {
            this.epoch = epoch;
            this.file = file;
            this.limit = limit;
            this.reserved = limit;
            this.success = success;
            this.error = error;
            this.deadline = deadline;
        }
    }
}
