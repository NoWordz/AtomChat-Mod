package com.atom.chat.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class HistoryCleanupLifecycleTest {
    @TempDir Path temp;

    @Test void switchingAfterPathValidationBeforeDeleteProtectsNewWorldAndLoadsItsHistory() throws Exception {
        Fixture f = new Fixture(temp);
        Thread old = f.startPausedCleanup();
        try {
            f.lifecycle.disconnect();
            f.join("MP:b");
            f.storage.releaseValidation.countDown();
            f.main.next().run();
            finish(old);
            assertTrue(Files.exists(f.fileB));
            Thread next = f.io.startNext();
            f.main.next().run(); // B's cleanup checks its current session.
            f.main.next().run(); // B is protected at the actual deletion boundary.
            finish(next);
            f.main.runQueued();
            assertEquals(List.of(2L), f.published.stream().map(e -> e.timestamp).toList());
            assertTrue(f.errors.isEmpty());
        } finally { f.storage.releaseValidation.countDown(); }
    }

    @Test void disablingDuringValidationStopsCleanupAndDoesNotReadOrDeleteAfterwards() throws Exception {
        Fixture f = new Fixture(temp);
        Thread worker = f.startPausedCleanup();
        try {
            f.enabled.set(false);
            f.storage.releaseValidation.countDown();
            f.main.next().run();
            finish(worker);
            assertTrue(Files.exists(f.fileB));
            assertEquals(0, f.storage.reads);
            assertTrue(f.published.isEmpty());
        } finally { f.storage.releaseValidation.countDown(); }
    }

    @Test void clearDuringValidationInvalidatesTheCleanupPermit() throws Exception {
        Fixture f = new Fixture(temp);
        Thread worker = f.startPausedCleanup();
        try {
            f.lifecycle.clear();
            f.storage.releaseValidation.countDown();
            f.main.next().run();
            finish(worker);
            assertTrue(Files.exists(f.fileB));
            assertTrue(f.storage.deleted.isEmpty());
        } finally { f.storage.releaseValidation.countDown(); }
    }

    @Test void actualWorldChangeWithoutHistoryJoinAlsoInvalidatesPermission() throws Exception {
        Fixture f = new Fixture(temp);
        Thread worker = f.startPausedCleanup();
        try {
            f.actual.set("MP:b");
            f.storage.releaseValidation.countDown();
            f.main.next().run();
            finish(worker);
            assertTrue(Files.exists(f.fileB));
        } finally { f.storage.releaseValidation.countDown(); }
    }

    @Test void deleteAndJoinOrClearAreLinearizedWithoutHoldingTheLockDuringValidation() throws Exception {
        for (boolean clear : List.of(false, true)) {
            Fixture f = new Fixture(Files.createDirectories(temp.resolve(clear ? "clear" : "join")));
            f.storage.pauseInsideDelete = true;
            Thread worker = f.startPausedCleanup();
            Thread delivery = null, transition = null;
            try {
                f.storage.releaseValidation.countDown();
                delivery = new Thread(f.main.next());
                delivery.start();
                assertTrue(f.storage.inDelete.await(5, TimeUnit.SECONDS));
                CountDownLatch transitioned = new CountDownLatch(1);
                transition = new Thread(() -> {
                    if (clear) f.lifecycle.clear(); else f.join("MP:b");
                    transitioned.countDown();
                });
                transition.start();
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (transition.getState() != Thread.State.BLOCKED && System.nanoTime() < until) Thread.yield();
                assertEquals(Thread.State.BLOCKED, transition.getState(), "the transition must wait for the single delete boundary");
                assertEquals(1, transitioned.getCount());
                f.storage.releaseDelete.countDown();
                finish(delivery);
                finish(transition);
                finish(worker);
                assertEquals(0, transitioned.getCount());
                assertFalse(Files.exists(f.fileB), "this delete was ordered before the later transition");
            } finally {
                f.storage.releaseValidation.countDown();
                f.storage.releaseDelete.countDown();
                if (delivery != null) delivery.join(5000);
                if (transition != null) transition.join(5000);
                worker.join(5000);
            }
        }
    }

    @Test void theCurrentWorldFileIsProtectedEvenIfPassedAsADeleteCandidate() throws Exception {
        Fixture f = new Fixture(temp);
        f.storage.candidate = HistoryStore.file("MP:a");
        Thread worker = f.startPausedCleanup();
        try {
            f.storage.releaseValidation.countDown();
            f.main.next().run();
            finish(worker);
            assertTrue(Files.exists(f.storage.candidate));
            assertTrue(f.storage.deleted.isEmpty());
        } finally { f.storage.releaseValidation.countDown(); }
    }

    private static void finish(Thread thread) throws InterruptedException {
        thread.join(5000);
        assertFalse(thread.isAlive(), "controlled worker must finish");
    }

    static final class QueueExecutor implements Executor {
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        public void execute(Runnable runnable) { tasks.add(runnable); }
        Runnable next() throws InterruptedException {
            Runnable task = tasks.poll(5, TimeUnit.SECONDS);
            assertNotNull(task, "expected one queued main-thread permission");
            return task;
        }
        Thread startNext() throws InterruptedException {
            Thread thread = new Thread(next());
            thread.setDaemon(true);
            thread.start();
            return thread;
        }
        void runQueued() { for (Runnable r; (r = tasks.poll()) != null;) r.run(); }
    }

    static final class Storage implements HistoryLifecycle.Storage {
        final CountDownLatch validated = new CountDownLatch(1), releaseValidation = new CountDownLatch(1);
        final CountDownLatch inDelete = new CountDownLatch(1), releaseDelete = new CountDownLatch(1);
        final List<Path> deleted = new ArrayList<>();
        Path candidate;
        boolean pauseInsideDelete;
        int reads;
        public HistoryFiles.Loaded read(String key) throws Exception { reads++; return HistoryFiles.read(HistoryStore.file(key)); }
        public void cleanup(String key, int days, HistoryLifecycle.CleanupPermit permit) throws Exception {
            if (!permit.active()) return;
            if (key.equals("MP:a")) {
                validated.countDown(); // Candidate enumeration/path validation has finished outside the lifecycle lock.
                assertTrue(releaseValidation.await(5, TimeUnit.SECONDS));
            }
            permit.delete(candidate, () -> {
                inDelete.countDown();
                if (pauseInsideDelete) assertTrue(releaseDelete.await(5, TimeUnit.SECONDS));
                deleted.add(candidate);
                Files.deleteIfExists(candidate);
            });
        }
        public boolean write(String key, List<HistoryStore.Entry> entries, BooleanSupplier valid) throws Exception {
            return HistoryFiles.write(HistoryStore.file(key), entries, valid);
        }
        public void delete(String key) throws Exception { Files.deleteIfExists(HistoryStore.file(key)); }
    }

    static final class Fixture {
        final QueueExecutor io = new QueueExecutor(), main = new QueueExecutor();
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final AtomicReference<String> actual = new AtomicReference<>();
        final List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<HistoryStore.Entry> published = new ArrayList<>();
        final Storage storage = new Storage();
        final HistoryLifecycle lifecycle = new HistoryLifecycle(io, main, storage, enabled::get, actual::get, errors::add);
        final Path fileB;
        Fixture(Path dir) throws Exception {
            HistoryStore.init(dir);
            Files.writeString(HistoryStore.file("MP:a"), "{\"ts\":1}\n");
            fileB = HistoryStore.file("MP:b");
            Files.writeString(fileB, "{\"ts\":2}\n");
            storage.candidate = fileB;
        }
        void join(String key) { lifecycle.join(key, 7, published::addAll); actual.set(key); }
        Thread startPausedCleanup() throws Exception {
            join("MP:a");
            Thread worker = io.startNext();
            main.next().run();
            assertTrue(storage.validated.await(5, TimeUnit.SECONDS));
            return worker;
        }
    }
}
