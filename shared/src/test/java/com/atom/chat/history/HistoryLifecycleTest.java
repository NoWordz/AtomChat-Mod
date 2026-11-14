package com.atom.chat.history;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class HistoryLifecycleTest {
    @Test void loadDoesNotReadOnJoinAndPublishesOnlyOnTheMainExecutor() {
        Fixture f = new Fixture();
        f.storage.files.put("MP:a", List.of(HistoryFilesTest.entry(1)));
        f.join("MP:a");
        assertEquals(0, f.storage.reads);
        f.io.runAll();
        assertEquals(1, f.storage.reads);
        assertTrue(f.published.isEmpty());
        f.main.runAll();
        assertEquals(List.of(1L), f.published.stream().map(e -> e.timestamp).toList());
        assertTrue(f.lifecycle.hasSavedHistory());
        for (int i = 0; i < 100; i++) assertTrue(f.lifecycle.hasSavedHistory());
        assertEquals(1, f.storage.reads);
    }

    @Test void outOfOrderPublicationCannotReplaceNewServerOrSameKeyNewConnection() {
        Fixture f = new Fixture();
        f.storage.files.put("MP:a", List.of(HistoryFilesTest.entry(1)));
        f.storage.files.put("MP:b", List.of(HistoryFilesTest.entry(2)));
        f.join("MP:a");
        f.io.runAll();
        f.lifecycle.disconnect();
        f.join("MP:b");
        f.main.runAll();
        assertTrue(f.published.isEmpty());
        f.io.runAll();
        f.main.runAll();
        assertEquals(List.of(2L), f.published.stream().map(e -> e.timestamp).toList());
        f.published.clear();
        f.join("MP:a");
        f.io.runAll();
        f.lifecycle.disconnect();
        f.join("MP:a");
        f.main.runAll();
        assertTrue(f.published.isEmpty());
    }

    @Test void clearInvalidatesQueuedLoadsPostedLoadsAndSnapshots() {
        Fixture f = new Fixture();
        f.storage.files.put("MP:a", List.of(HistoryFilesTest.entry(1)));
        f.join("MP:a");
        f.io.runAll();
        f.lifecycle.save(List.of(HistoryFilesTest.entry(2)));
        f.lifecycle.clear();
        f.io.runAll();
        f.main.runAll();
        assertTrue(f.published.isEmpty());
        assertFalse(f.storage.files.containsKey("MP:a"));
        assertFalse(f.lifecycle.hasSavedHistory());
        assertEquals(0, f.storage.writes);
    }

    @Test void clearingAnotherWorldDoesNotCancelTheFirstWorldDeletion() {
        Fixture f = new Fixture();
        f.storage.files.put("MP:a", List.of(HistoryFilesTest.entry(1)));
        f.storage.files.put("MP:b", List.of(HistoryFilesTest.entry(2)));
        f.join("MP:a");
        f.lifecycle.clear();
        f.lifecycle.disconnect();
        f.join("MP:b");
        f.lifecycle.clear();
        f.io.runAll();
        f.main.runAll();
        assertTrue(f.storage.files.isEmpty());
        assertTrue(f.published.isEmpty());
    }

    @Test void configAndActualWorldAreRecheckedBeforePublication() {
        Fixture f = new Fixture();
        f.join("MP:a");
        f.io.runAll();
        f.enabled.set(false);
        f.main.runAll();
        assertEquals(0, f.publications);
        f.enabled.set(true);
        f.join("MP:b");
        f.io.runAll();
        f.world = "MP:c";
        f.main.runAll();
        assertEquals(0, f.publications);
    }

    @Test void disconnectSnapshotMergesUnpublishedDiskHistoryAndPreservesItsWorld() {
        Fixture f = new Fixture();
        f.storage.files.put("MP:a", List.of(HistoryFilesTest.entry(1)));
        f.join("MP:a");
        f.lifecycle.save(List.of(HistoryFilesTest.entry(2)));
        f.lifecycle.disconnect();
        f.join("MP:b");
        f.io.runAll();
        f.main.runAll();
        assertEquals(List.of(1L, 2L), f.storage.files.get("MP:a").stream().map(e -> e.timestamp).toList());
        assertTrue(f.published.isEmpty());
    }

    @Test void repeatedSnapshotsBeforePublicationDoNotDuplicateEarlyLiveMessages() {
        Fixture f = new Fixture();
        f.storage.files.put("MP:a", List.of(HistoryFilesTest.entry(1)));
        f.join("MP:a");
        f.io.runAll();
        f.lifecycle.save(List.of(HistoryFilesTest.entry(2)));
        f.io.runAll();
        f.lifecycle.save(List.of(HistoryFilesTest.entry(2), HistoryFilesTest.entry(3)));
        f.io.runAll();
        assertEquals(List.of(1L, 2L, 3L), f.storage.files.get("MP:a").stream().map(e -> e.timestamp).toList());
    }

    @Test void saveCopiesTheBoundedSnapshotBeforeWorkerExecutionAndUpdatesCachedFactOnMain() {
        Fixture f = new Fixture();
        f.join("MP:a");
        f.io.runAll();
        f.main.runAll();
        List<HistoryStore.Entry> snapshot = new ArrayList<>();
        for (int i = 0; i < 700; i++) snapshot.add(HistoryFilesTest.entry(i));
        assertTrue(f.lifecycle.save(snapshot));
        snapshot.clear();
        assertFalse(f.lifecycle.hasSavedHistory());
        f.io.runAll();
        assertFalse(f.lifecycle.hasSavedHistory());
        f.main.runAll();
        assertTrue(f.lifecycle.hasSavedHistory());
        var saved = f.storage.files.get("MP:a");
        assertEquals(500, saved.size());
        assertEquals(200, saved.get(0).timestamp);
        assertEquals(699, saved.get(499).timestamp);
    }

    @Test void loadedPublicAndPrivateDtosArriveBeforeTheirNewerLiveTail() {
        Fixture f = new Fixture();
        var oldPrivate = new HistoryStore.Entry(2, true, "peer", false, false,
                null, null, null, "peer", "peer", "old private", null);
        f.storage.files.put("MP:a", List.of(HistoryFilesTest.entry(1), oldPrivate));
        f.world = "MP:a";
        Map<String, List<Long>> channels = new HashMap<>();
        channels.put("public", List.of(10L));
        channels.put("peer", List.of(11L));
        f.lifecycle.join("MP:a", 30, loaded -> {
            Map<String, List<Long>> historical = new HashMap<>();
            for (var entry : loaded) historical.computeIfAbsent(entry.priv ? entry.peer : "public",
                    ignored -> new ArrayList<>()).add(entry.timestamp);
            historical.forEach((channel, entries) -> channels.put(channel,
                    HistorySnapshot.prepend(entries, channels.getOrDefault(channel, List.of()))));
        });
        f.io.runAll();
        f.main.runAll();
        assertEquals(List.of(1L, 10L), channels.get("public"));
        assertEquals(List.of(2L, 11L), channels.get("peer"));
    }

    @Test void saveQueueCoalescesSnapshotsBeforeSerializationAndHasAFiniteWorldLimit() {
        Fixture f = new Fixture();
        f.join("MP:a");
        f.io.runAll();
        f.main.runAll();
        for (int i = 0; i < 100; i++) f.lifecycle.save(List.of(HistoryFilesTest.entry(i)));
        assertEquals(0, f.storage.writes);
        assertEquals(1, f.io.tasks.size());
        f.io.runAll();
        assertEquals(1, f.storage.writes);
        assertEquals(99, f.storage.files.get("MP:a").get(0).timestamp);
        for (int i = 0; i < 30; i++) {
            f.join("MP:" + i);
            f.lifecycle.save(List.of(HistoryFilesTest.entry(i)));
            f.lifecycle.disconnect();
        }
        assertEquals(1, f.io.tasks.size());
        f.io.runAll();
        assertTrue(f.storage.writes <= 9, "at most eight pending world writes plus the prior completed one");
        assertFalse(f.errors.isEmpty(), "overload must be observable rather than grow indefinitely");
    }

    static final class QueueExecutor implements Executor {
        final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();
        public void execute(Runnable runnable) { tasks.add(runnable); }
        void runAll() { for (Runnable r; (r = tasks.poll()) != null;) r.run(); }
    }

    static final class MemoryStorage implements HistoryLifecycle.Storage {
        final Map<String, List<HistoryStore.Entry>> files = new HashMap<>();
        int reads, writes;
        public HistoryFiles.Loaded read(String key) {
            reads++;
            return new HistoryFiles.Loaded(files.containsKey(key), files.getOrDefault(key, List.of()));
        }
        public void cleanup(String key, int days, HistoryLifecycle.CleanupPermit permit) {}
        public boolean write(String key, List<HistoryStore.Entry> entries, BooleanSupplier valid) {
            if (!valid.getAsBoolean()) return false;
            writes++;
            files.put(key, List.copyOf(entries));
            return true;
        }
        public void delete(String key) { files.remove(key); }
    }

    static final class Fixture {
        final QueueExecutor io = new QueueExecutor(), main = new QueueExecutor();
        final MemoryStorage storage = new MemoryStorage();
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final List<Throwable> errors = new ArrayList<>();
        final List<HistoryStore.Entry> published = new ArrayList<>();
        int publications;
        String world;
        final HistoryLifecycle lifecycle = new HistoryLifecycle(io, main, storage, enabled::get, () -> world, errors::add);
        void join(String key) {
            world = key;
            lifecycle.join(key, 30, entries -> {
                publications++;
                published.addAll(entries);
            });
        }
    }
}
