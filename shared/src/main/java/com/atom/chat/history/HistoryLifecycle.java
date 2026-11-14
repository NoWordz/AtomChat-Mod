package com.atom.chat.history;

import com.atom.chat.history.HistoryStore.Entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread session state plus a single bounded, coalescing disk-work queue. */
public final class HistoryLifecycle {
    public interface Storage {
        HistoryFiles.Loaded read(String key) throws Exception;
        void cleanup(String key, int days, CleanupPermit permit) throws Exception;
        boolean write(String key, List<Entry> snapshot, BooleanSupplier valid) throws Exception;
        void delete(String key) throws Exception;
    }

    @FunctionalInterface
    public interface DeleteAction {
        void run() throws Exception;
    }

    public interface CleanupPermit {
        boolean active() throws Exception;
        /** Returns false if the scan must stop. The current world's file is skipped without deleting it. */
        boolean delete(Path candidate, DeleteAction action) throws Exception;
    }

    private final Executor io, main;
    private final Storage storage;
    private final BooleanSupplier enabled;
    private final Supplier<String> actualWorld;
    private final Consumer<Throwable> failure;
    private final LinkedHashMap<String, Runnable> work = new LinkedHashMap<>();
    private final Object lifecycleLock = new Object();
    private final java.util.concurrent.ConcurrentMap<String, Long> clearGenerations = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean scheduled;
    private Runnable publication;
    private boolean publicationScheduled;
    private volatile long epoch;
    private volatile String currentKey;
    private boolean savedHistory, loading;
    private LoadState loadState;

    public HistoryLifecycle(Executor io, Executor main, Storage storage, BooleanSupplier enabled,
                            Supplier<String> actualWorld, Consumer<Throwable> failure) {
        this.io = io;
        this.main = main;
        this.storage = storage;
        this.enabled = enabled;
        this.actualWorld = actualWorld;
        this.failure = failure;
    }

    /** Called on the client thread; filesystem work only happens in the submitted worker. */
    public void join(String key, int days, Consumer<List<Entry>> publish) {
        LoadState state;
        boolean shouldLoad;
        synchronized (lifecycleLock) {
            epoch++;
            currentKey = key;
            savedHistory = false;
            loading = enabled.getAsBoolean() && HistoryStore.isWorldSpecific(key);
            state = new LoadState(new Ticket(epoch, generation(key), key));
            loadState = state;
            shouldLoad = loading;
        }
        if (!shouldLoad) return;
        if (!submit("load", () -> {
            if (!current(state.ticket)) return;
            try {
                CleanupPermission permit = new CleanupPermission(state.ticket);
                storage.cleanup(key, days, permit);
                if (permit.stopped || !current(state.ticket)) return;
                HistoryFiles.Loaded loaded = base(state);
                postLoad(() -> {
                    if (!current(state.ticket)) return;
                    loading = false;
                    if (!enabled.getAsBoolean() || !Objects.equals(actualWorld.get(), key)) return;
                    savedHistory = loaded.exists();
                    publish.accept(loaded.entries());
                });
            } catch (Exception e) {
                failure.accept(e);
                postLoad(() -> { if (current(state.ticket)) loading = false; });
            }
        })) loading = false;
    }

    public void disconnect() {
        synchronized (lifecycleLock) {
            epoch++;
            currentKey = null;
            savedHistory = false;
            loading = false;
            loadState = null;
        }
    }

    public boolean hasSavedHistory() {
        return savedHistory;
    }

    /** Input must already contain immutable DTOs, with all Minecraft conversion done on the client. */
    public boolean save(List<Entry> snapshot) {
        String key = currentKey;
        if (!enabled.getAsBoolean() || !HistoryStore.isWorldSpecific(key)) return false;
        List<Entry> selected = List.copyOf(snapshot.subList(Math.max(0,
                snapshot.size() - HistorySnapshot.MAX_ENTRIES), snapshot.size()));
        Ticket ticket = new Ticket(epoch, generation(key), key);
        LoadState pendingLoad = loading ? loadState : null;
        return submit("write:" + key, () -> {
            if (generation(key) != ticket.generation) return;
            try {
                List<Entry> entries = pendingLoad == null ? selected
                        : HistorySnapshot.prepend(base(pendingLoad).entries(), selected);
                if (storage.write(key, entries, () -> generation(key) == ticket.generation)) {
                    main.execute(() -> {
                        if (current(ticket) && enabled.getAsBoolean() && Objects.equals(actualWorld.get(), key))
                            savedHistory = true;
                    });
                }
            } catch (Exception e) {
                failure.accept(e);
            }
        });
    }

    /** Invalidate before deletion is queued, so neither a stale load nor save can revive cleared rows. */
    public void clear() {
        String key;
        long clearGeneration;
        synchronized (lifecycleLock) {
            key = currentKey;
            if (key != null) clearGenerations.merge(key, 1L, Long::sum);
            savedHistory = false;
            loading = false;
            loadState = null;
            clearGeneration = generation(key);
        }
        if (!enabled.getAsBoolean() || !HistoryStore.isWorldSpecific(key)) return;
        submit("write:" + key, () -> {
            if (generation(key) != clearGeneration) return;
            try {
                storage.delete(key);
            } catch (Exception e) {
                failure.accept(e);
            }
        });
    }

    private HistoryFiles.Loaded base(LoadState state) throws Exception {
        if (state.base == null) state.base = storage.read(state.ticket.key);
        return state.base;
    }

    private boolean current(Ticket ticket) {
        return epoch == ticket.epoch && generation(ticket.key) == ticket.generation && Objects.equals(currentKey, ticket.key);
    }

    private long generation(String key) { return key == null ? 0 : clearGenerations.getOrDefault(key, 0L); }

    /** Only the last check and one unlink run here; enumeration, stats and link validation stay on IO. */
    private final class CleanupPermission implements CleanupPermit {
        private final Ticket ticket;
        private volatile boolean stopped;
        CleanupPermission(Ticket ticket) { this.ticket = ticket; }

        public boolean active() throws Exception {
            return onMain(() -> {
                synchronized (lifecycleLock) {
                    if (!valid()) stopped = true;
                    return !stopped;
                }
            });
        }

        public boolean delete(Path candidate, DeleteAction action) throws Exception {
            return onMain(() -> {
                synchronized (lifecycleLock) {
                    if (stopped || !valid()) {
                        stopped = true;
                        return false;
                    }
                    Path protectedFile = HistoryStore.file(actualWorld.get()).toAbsolutePath().normalize();
                    if (!candidate.toAbsolutePath().normalize().equals(protectedFile)) action.run();
                    return true;
                }
            });
        }

        private boolean valid() {
            // Both suppliers can read MC/config state: evaluate them on the client thread, never on IO.
            return current(ticket) && enabled.getAsBoolean() && Objects.equals(actualWorld.get(), ticket.key);
        }
    }

    @FunctionalInterface
    private interface MainOperation<T> { T run() throws Exception; }

    private <T> T onMain(MainOperation<T> operation) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        main.execute(() -> {
            try { result.complete(operation.run()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (java.util.concurrent.ExecutionException failed) {
            if (failed.getCause() instanceof Exception exception) throw exception;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failed.getCause());
        }
    }

    /** Keep only one pending loaded DTO publication if the client is busy switching worlds. */
    private synchronized void postLoad(Runnable action) {
        publication = action;
        if (publicationScheduled) return;
        publicationScheduled = true;
        try {
            main.execute(() -> {
                Runnable next;
                synchronized (HistoryLifecycle.this) {
                    next = publication;
                    publication = null;
                    publicationScheduled = false;
                }
                if (next != null) next.run();
            });
        } catch (RuntimeException e) {
            publication = null;
            publicationScheduled = false;
            failure.accept(e);
        }
    }

    /** One executor runnable and at most eight pending slots. New saves replace the same world's old snapshot. */
    private synchronized boolean submit(String slot, Runnable action) {
        if (!work.containsKey(slot) && work.size() >= 8) {
            failure.accept(new IllegalStateException("History IO queue is full"));
            return false;
        }
        work.remove(slot);
        work.put(slot, action);
        if (scheduled) return true;
        scheduled = true;
        try {
            io.execute(this::runOne);
            return true;
        } catch (RuntimeException e) {
            scheduled = false;
            work.clear();
            failure.accept(e);
            return false;
        }
    }

    private void runOne() {
        Runnable action;
        synchronized (this) {
            var first = work.entrySet().iterator();
            if (!first.hasNext()) { scheduled = false; return; }
            action = first.next().getValue();
            first.remove();
        }
        try {
            action.run();
        } finally {
            synchronized (this) {
                scheduled = false;
                if (!work.isEmpty()) {
                    scheduled = true;
                    try { io.execute(this::runOne); }
                    catch (RuntimeException e) { scheduled = false; work.clear(); failure.accept(e); }
                }
            }
        }
    }

    private record Ticket(long epoch, long generation, String key) {}
    private static final class LoadState {
        final Ticket ticket;
        HistoryFiles.Loaded base;
        LoadState(Ticket ticket) { this.ticket = ticket; }
    }
}
