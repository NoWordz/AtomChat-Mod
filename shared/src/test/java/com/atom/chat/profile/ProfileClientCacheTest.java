package com.atom.chat.profile;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class ProfileClientCacheTest {
    private final UUID player = UUID.randomUUID();
    @Test void requestsOnceThenRetriesAndNegativeResponseStopsRetry() {
        var requests = new ArrayList<UUID>();
        var time = new AtomicLong();
        var cache = new ProfileClientCache(requests::add, time::get);
        cache.get(player); cache.get(player);
        assertEquals(1, requests.size());
        time.set(5000); cache.get(player);
        assertEquals(2, requests.size());
        cache.accept(ProfileRecord.empty(player));
        time.set(10000); cache.get(player);
        assertEquals(2, requests.size());
    }
    @Test void newerRevisionWinsAndChangeTriggersFreshRequest() {
        var requests = new ArrayList<UUID>();
        var cache = new ProfileClientCache(requests::add, () -> 0);
        cache.accept(new ProfileRecord(player, "new", "", "", 2));
        cache.accept(new ProfileRecord(player, "old", "", "", 1));
        assertEquals("new", cache.get(player).signature());
        cache.invalidate(player); cache.get(player);
        assertEquals(1, requests.size());
    }
    @Test void disconnectChangesEpochAndDoesNotLeakAnotherServersDocument() {
        var requests = new ArrayList<UUID>();
        var cache = new ProfileClientCache(requests::add, () -> 0);
        cache.accept(new ProfileRecord(player, "private-to-server-a", "", "", 1));
        long old = cache.epoch(); cache.clear();
        assertNotEquals(old, cache.epoch());
        assertEquals("", cache.get(player).signature());
        assertEquals(1, requests.size());
    }
}
