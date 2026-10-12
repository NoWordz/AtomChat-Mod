package com.atom.chat.profile;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded, connection-scoped records with throttled misses and monotonic revisions. */
public final class ProfileClientCache {
    private static final int CAPACITY = 512;
    private static final long RETRY_MS = 5000;
    private static final long ROLE_REFRESH_MS = 30000;
    private final Consumer<UUID> request;
    private final LongSupplier clock;
    private final LinkedHashMap<UUID, Slot> entries = new LinkedHashMap<>(32, .75F, true);
    private long epoch;
    private static final class Slot {
        ProfileRecord record;
        long minimumRevision;
        long requestedAt = Long.MIN_VALUE;
        boolean roleRequested;
        Boolean operator;
        long roleReceivedAt = Long.MIN_VALUE;
        long roleToken;
    }
    public ProfileClientCache(Consumer<UUID> request, LongSupplier clock) {
        this.request = request;
        this.clock = clock;
    }
    public ProfileRecord get(UUID uuid) {
        if (uuid == null) return ProfileRecord.empty(null);
        Slot slot = entries.computeIfAbsent(uuid, ignored -> new Slot());
        trim();
        long now = clock.getAsLong();
        long interval = slot.record == null ? RETRY_MS : ROLE_REFRESH_MS;
        if ((slot.record == null || slot.roleRequested)
                && (slot.requestedAt == Long.MIN_VALUE || now - slot.requestedAt >= interval)) {
            slot.requestedAt = now;
            request.accept(uuid);
        }
        return slot.record != null ? slot.record : ProfileRecord.empty(uuid);
    }
    /** Unknown and expired server answers stay hidden. */
    public Boolean operator(UUID uuid) {
        if (uuid == null) return null;
        Slot slot = entries.computeIfAbsent(uuid, ignored -> new Slot());
        slot.roleRequested = true;
        trim();
        get(uuid);
        long now = clock.getAsLong();
        return slot.roleReceivedAt != Long.MIN_VALUE
                && now - slot.roleReceivedAt < ROLE_REFRESH_MS
                ? slot.operator : null;
    }
    public void acceptOperator(UUID uuid, long token, Boolean operator) {
        if (uuid == null || token >= 0) throw new IllegalArgumentException("invalid_profile_role");
        Slot slot = entries.computeIfAbsent(uuid, ignored -> new Slot());
        // New read tokens are more negative; older replies cannot restore stale roles.
        if (slot.roleToken != 0 && token >= slot.roleToken) return;
        slot.roleToken = token;
        slot.operator = operator;
        slot.roleReceivedAt = clock.getAsLong();
        trim();
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
            slot.operator = null;
            slot.roleReceivedAt = Long.MIN_VALUE;
        }
    }
    public void clear() { entries.clear(); epoch++; }
    public long epoch() { return epoch; }
    private void trim() {
        while (entries.size() > CAPACITY) entries.remove(entries.keySet().iterator().next());
    }
}
