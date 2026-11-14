package com.atom.chat.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MediaUploadSchedulerTest {
    @TempDir Path temp;

    @Test void changingServerBeforeReadCancelsWithoutReadingOrSending() {
        Fixture f = new Fixture();
        long first = f.scheduler.connectionEpoch();
        f.submit("old", 1024);
        f.scheduler.reset(false);
        f.scheduler.reset(true);
        assertTrue(f.scheduler.connectionEpoch() > first);
        f.io.runAll();
        f.scheduler.tick();
        assertEquals(0, f.reads.get());
        assertEquals(List.of("disconnected"), f.errors);
        assertTrue(f.sent.isEmpty());
    }

    @Test void changingServerAfterReadBeforeTickNeverSendsOldBytes() {
        Fixture f = new Fixture();
        f.submit("old", 1024);
        f.io.runAll();
        f.scheduler.reset(true);
        f.scheduler.tick();
        assertTrue(f.sent.isEmpty());
        assertEquals(List.of("disconnected"), f.errors);
    }

    @Test void changingServerBeforeResultDiscardsLateSuccessAndError() {
        Fixture f = new Fixture();
        f.submit("old", 1024);
        f.io.runAll();
        long oldEpoch = f.scheduler.connectionEpoch();
        f.scheduler.tick();
        UUID id = f.sent.get(0).id;
        f.scheduler.reset(true);
        f.scheduler.onResult(oldEpoch, id, "old-url", "");
        f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "old-url", "server_error");
        f.scheduler.tick();
        assertTrue(f.successes.isEmpty());
        assertEquals(List.of("disconnected"), f.errors);
    }

    @Test void concurrentAdmissionIncludesQueuedReadsAndReleasesCompletedSlots() throws Exception {
        Fixture f = new Fixture();
        var callers = Executors.newFixedThreadPool(12);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<java.util.concurrent.Future<?>> submitted = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                int n = i;
                submitted.add(callers.submit(() -> {
                    start.await();
                    f.submit("image" + n, 1024);
                    return null;
                }));
            }
            start.countDown();
            for (var job : submitted) job.get(5, TimeUnit.SECONDS);
            assertEquals(24, f.errors.size());
            assertTrue(f.errors.stream().allMatch("busy"::equals));
            assertEquals(0, f.reads.get());
            assertEquals(1, f.io.tasks.size(), "only one reader runnable may be queued");
            f.io.runAll();
            assertEquals(8, f.reads.get());
            f.scheduler.tick();
            f.scheduler.tick();
            List<UUID> ids = f.sent.stream().filter(e -> e.kind.equals("finish")).map(e -> e.id).toList();
            assertEquals(8, ids.size());
            for (UUID id : ids) f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "url", "");
            for (int i = 0; i < 8; i++) f.submit("next" + i, 1024);
            assertEquals(24, f.errors.size(), "all eight completed reservations must be reusable");
        } finally {
            callers.shutdownNow();
        }
    }

    @Test void byteBudgetCoversQueuedReadsAndWaitingForResult() {
        Fixture f = new Fixture();
        int halfBudget = 32 * 1024 * 1024;
        f.submit("a", halfBudget);
        f.submit("b", halfBudget);
        f.submit("c", halfBudget);
        assertEquals(List.of("busy"), f.errors);
        f.io.runAll(); // Small actual files release the unused part of the worst-case reservation.
        f.scheduler.tick();
        f.submit("d", halfBudget);
        f.submit("e", halfBudget);
        assertEquals(List.of("busy", "busy"), f.errors);
        for (Event event : List.copyOf(f.sent)) {
            if (event.kind.equals("finish")) f.scheduler.onResult(f.scheduler.connectionEpoch(), event.id, "ok", "");
        }
        f.submit("f", halfBudget);
        assertEquals(List.of("busy", "busy"), f.errors);
    }

    @Test void cancelledRunningReadKeepsItsBudgetUntilItActuallyReturns() throws Exception {
        QueueExecutor io = new QueueExecutor();
        CountDownLatch reading = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        MediaUploadScheduler scheduler = new MediaUploadScheduler(io, (path, limit) -> {
            reading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new byte[]{1};
        }, new Sink(), System::currentTimeMillis);
        scheduler.reset(true);
        scheduler.upload(scheduler.connectionEpoch(), Path.of("old"), 64 * 1024 * 1024, s -> fail(), errors::add);
        Thread reader = new Thread(io.tasks.remove());
        reader.start();
        try {
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            scheduler.reset(true);
            scheduler.upload(scheduler.connectionEpoch(), Path.of("new"), 1, s -> {}, errors::add);
            assertEquals(List.of("disconnected", "busy"), errors);
        } finally {
            release.countDown();
            reader.join(5000);
        }
        assertFalse(reader.isAlive());
        scheduler.upload(scheduler.connectionEpoch(), Path.of("new"), 1, s -> {}, errors::add);
        assertEquals(List.of("disconnected", "busy"), errors);
    }

    @Test void tickLimitsTotalChunksAndRoundRobinsWithoutChangingPerUploadOrder() {
        Fixture f = new Fixture();
        f.bytes = new byte[6 * 24 * 1024 + 1];
        f.submit("a", f.bytes.length);
        f.submit("b", f.bytes.length);
        f.io.runAll();
        f.scheduler.tick();
        List<Event> chunks = f.sent.stream().filter(e -> e.kind.equals("chunk")).toList();
        assertEquals(4, chunks.size());
        assertEquals(List.of(0, 0, 24576, 24576), chunks.stream().map(e -> e.offset).toList());
        assertEquals(chunks.get(0).id, chunks.get(2).id);
        assertEquals(chunks.get(1).id, chunks.get(3).id);
        assertNotEquals(chunks.get(0).id, chunks.get(1).id);
        for (int tick = 0; tick < 3; tick++) {
            int before = f.sent.size();
            f.scheduler.tick();
            assertTrue(f.sent.subList(before, f.sent.size()).stream().filter(e -> e.kind.equals("chunk")).count() <= 4);
        }
        for (String name : List.of("a", "b")) {
            UUID id = f.sent.stream().filter(e -> name.equals(e.name)).findFirst().orElseThrow().id;
            List<Event> packets = f.sent.stream().filter(e -> id.equals(e.id)).toList();
            assertEquals("start", packets.get(0).kind);
            assertEquals("finish", packets.get(packets.size() - 1).kind);
            assertEquals(List.of(0, 24576, 49152, 73728, 98304, 122880, 147456),
                    packets.stream().filter(e -> e.kind.equals("chunk")).map(e -> e.offset).toList());
            assertEquals(147457, packets.stream().filter(e -> e.kind.equals("chunk")).mapToInt(e -> e.size).sum());
        }
    }

    @Test void timeoutAndDuplicateResultsNotifyOnceAndReleaseCapacity() {
        Fixture f = new Fixture();
        f.submit("a", 1024);
        f.io.runAll();
        f.scheduler.tick();
        UUID id = f.sent.get(0).id;
        f.clock.set(20_001);
        f.scheduler.tick();
        f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "url", "");
        f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "", "failure");
        f.scheduler.reset(false);
        assertEquals(List.of("timeout"), f.errors);
        assertTrue(f.successes.isEmpty());
    }

    @Test void progressExtendsTimeoutSoBoundedSendingDoesNotTimeoutLargeTransfers() {
        Fixture f = new Fixture();
        f.bytes = new byte[9 * 24576];
        f.submit("large", f.bytes.length);
        f.io.runAll();
        f.clock.set(19_000);
        f.scheduler.tick();
        f.clock.set(38_000);
        f.scheduler.tick();
        f.clock.set(57_000);
        f.scheduler.tick();
        UUID id = f.sent.get(0).id;
        f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "ok", "");
        f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "duplicate", "");
        assertEquals(List.of("ok"), f.successes);
        assertTrue(f.errors.isEmpty());
    }

    @Test void staleResultEpochCannotCompleteCurrentUploadAndServerErrorCompletesOnce() {
        Fixture f = new Fixture();
        f.submit("current", 1);
        f.io.runAll();
        f.scheduler.tick();
        UUID id = f.sent.get(0).id;
        f.scheduler.onResult(f.scheduler.connectionEpoch() - 1, id, "stale", "");
        assertTrue(f.successes.isEmpty());
        f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "", "denied");
        f.scheduler.onResult(f.scheduler.connectionEpoch(), id, "late", "");
        f.scheduler.reset(false);
        assertEquals(List.of("denied"), f.errors);
        assertTrue(f.successes.isEmpty());
    }

    @Test void readFailureEmptyAndOversizeNotifyOnceWithoutSending() {
        for (String failure : List.of("io", "empty", "too_large")) {
            Fixture f = new Fixture();
            f.bytes = failure.equals("empty") ? new byte[0] : new byte[2];
            f.readError = failure.equals("io");
            f.submit("a", 1);
            f.io.runAll();
            f.scheduler.tick();
            f.scheduler.reset(false);
            assertEquals(List.of(failure), f.errors);
            assertTrue(f.sent.isEmpty());
        }
    }

    @Test void executorRejectionAndSendFailureDoNotLeakOrDoubleNotify() {
        List<String> errors = new ArrayList<>();
        MediaUploadScheduler scheduler = new MediaUploadScheduler(r -> { throw new RejectedExecutionException(); },
                (p, limit) -> new byte[]{1}, new Sink(), () -> 0);
        scheduler.reset(true);
        for (int i = 0; i < 10; i++) scheduler.upload(scheduler.connectionEpoch(), Path.of("x"),
                64 * 1024 * 1024, s -> fail(), errors::add);
        assertEquals(java.util.Collections.nCopies(10, "io"), errors);
        Fixture f = new Fixture();
        f.sink.failure = true;
        f.submit("a", 1);
        f.io.runAll();
        f.scheduler.tick();
        f.scheduler.reset(false);
        assertEquals(List.of("disconnected"), f.errors);
    }

    @Test void boundedReaderRejectsBeforeAllocatingOversizeAndPreservesValidBytes() throws Exception {
        Path file = Files.write(temp.resolve("image"), new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, MediaUploadScheduler.readFile(file, 3));
        assertEquals("too_large", assertThrows(IOException.class, () -> MediaUploadScheduler.readFile(file, 2)).getMessage());
    }

    static final class QueueExecutor implements Executor {
        final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        public void execute(Runnable task) { tasks.add(task); }
        void runAll() { for (Runnable r; (r = tasks.poll()) != null;) r.run(); }
    }

    record Event(String kind, UUID id, String name, int offset, int size) {}
    static class Sink implements MediaUploadScheduler.Sender {
        final List<Event> events = new ArrayList<>();
        boolean failure;
        public void start(UUID id, String name, int size) throws IOException {
            if (failure) throw new IOException("closed");
            events.add(new Event("start", id, name, 0, size));
        }
        public void chunk(UUID id, int offset, byte[] data) { events.add(new Event("chunk", id, null, offset, data.length)); }
        public void finish(UUID id) { events.add(new Event("finish", id, null, 0, 0)); }
    }

    static class Fixture {
        final QueueExecutor io = new QueueExecutor();
        final Sink sink = new Sink();
        final List<Event> sent = sink.events;
        final AtomicInteger reads = new AtomicInteger();
        final AtomicLong clock = new AtomicLong();
        final List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> successes = new java.util.concurrent.CopyOnWriteArrayList<>();
        byte[] bytes = new byte[]{1};
        boolean readError;
        final MediaUploadScheduler scheduler = new MediaUploadScheduler(io, (p, limit) -> {
            reads.incrementAndGet();
            if (readError) throw new IOException("read failed");
            return bytes;
        }, sink, clock::get);
        Fixture() { scheduler.reset(true); }
        void submit(String name, int limit) {
            scheduler.upload(scheduler.connectionEpoch(), Path.of(name), limit, successes::add, errors::add);
        }
    }
}
