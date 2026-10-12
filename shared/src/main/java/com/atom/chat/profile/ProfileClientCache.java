package com.atom.chat.profile;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded, connection-scoped records with throttled misses and monotonic revisions. */
public final class ProfileClientCache {
    private static final int CAPACITY = 512;
    private static final long RETRY_MS = 5000;
    private final Consumer<UUID> request;
    private final LongSupplier clock;
    private final LinkedHashMap<UUID, Slot> entries = new LinkedHashMap<>(32, .75F, true);
    private long epoch;
    private static final class Slot {
        ProfileRecord record;
        long minimumRevision;
        long requestedAt = Long.MIN_VALUE;
    }
    public ProfileClientCache(Consumer<UUID> request, LongSupplier clock) {
        this.request = request;
        this.clock = clock;
    }
    public ProfileRecord get(UUID uuid) {
        if (uuid == null) return ProfileRecord.empty(null);
        Slot slot = entries.computeIfAbsent(uuid, ignored -> new Slot());
        trim();
        if (slot.record != null) return slot.record;
        long now = clock.getAsLong();
        if (slot.requestedAt == Long.MIN_VALUE || now - slot.requestedAt >= RETRY_MS) {
            slot.requestedAt = now;
            request.accept(uuid);
        }
        return ProfileRecord.empty(uuid);
    }
    public void accept(ProfileRecord record) {
        if (record == null || record.uuid() == null || record.revision() < 0
                || record.signature() == null || record.signature().length() > 256)
            throw new IllegalArgumentException("invalid_profile_data");
        if (record.avatar() == null || record.banner() == null)
            throw new IllegalArgumentException("invalid_profile_data");
        ProfileReferences.validate(record.avatar());
        ProfileReferences.validate(record.banner());
        Slot slot = entries.computeIfAbsent(record.uuid(), ignored -> new Slot());
        if (record.revision() >= slot.minimumRevision) {
            slot.record = record;
            slot.minimumRevision = record.revision();
        }
        trim();
    }
    public void invalidate(UUID uuid) {
        Slot slot = entries.get(uuid);
        if (slot != null) {
            slot.record = null;
            slot.requestedAt = Long.MIN_VALUE;
        }
    }
    public void clear() { entries.clear(); epoch++; }
    public long epoch() { return epoch; }
    private void trim() {
        while (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
    }
}
