package com.atom.chat.profile;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.LongSupplier;
/** Issued read tokens survive no disconnect; token sequence survives every disconnect. */
public final class ProfileRequestLedger {
    private record Request(UUID uuid, long expires) {}
    private final LinkedHashMap<Long, Request> issued = new LinkedHashMap<>();
    private final LongSupplier clock;
    private long sequence;
    public ProfileRequestLedger(LongSupplier clock) { this.clock = clock; }
    public long issue(UUID uuid) {
        if (uuid == null) throw new IllegalArgumentException("missing_uuid");
        expire();
        long token = -(++sequence);
        issued.put(token, new Request(uuid, clock.getAsLong() + 15000));
        while (issued.size() > 512) issued.remove(issued.keySet().iterator().next());
        return token;
    }
    public boolean accept(long token, UUID uuid) {
        Request request = issued.get(token);
        if (request == null || clock.getAsLong() >= request.expires() || !request.uuid().equals(uuid)) return false;
        issued.remove(token);
        return true;
    }
    public void cancel(long token) { issued.remove(token); }
    public void expire() { issued.entrySet().removeIf(entry -> clock.getAsLong() >= entry.getValue().expires()); }
    public void clear() { issued.clear(); }
}
