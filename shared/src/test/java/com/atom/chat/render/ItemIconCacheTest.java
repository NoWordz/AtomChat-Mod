package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemIconCacheTest {
    @Test
    void visibleCachedIconsAreRequestedAgainButOnlyOncePerFrame() {
        var cache = new ItemIconRenderer.IconCache<String, String>(2, value -> {});
        assertNull(cache.request("clock"));
        assertEquals(List.of("clock"), cache.takeRequests());
        cache.put("clock", "first frame");
        assertEquals("first frame", cache.request("clock"));
        assertEquals("first frame", cache.request("clock"));
        assertEquals(List.of("clock"), cache.takeRequests());
        assertTrue(cache.takeRequests().isEmpty());
    }

    @Test
    void excessiveDistinctRequestsAreBoundedWithoutDroppingEarlierVisibleIcons() {
        var cache = new ItemIconRenderer.IconCache<Integer, Integer>(128, value -> {});
        for (int i = 0; i < 10_000; i++) cache.request(i);
        var requests = cache.takeRequests();
        assertEquals(128, requests.size());
        assertEquals(0, requests.get(0));
        assertEquals(127, requests.get(127));
        cache.request(9999);
        assertEquals(List.of(9999), cache.takeRequests());
    }

    @Test
    void evictionUsesRecentDrawsAndReleasesEachResourceExactlyOnce() {
        var released = new ArrayList<String>();
        var cache = new ItemIconRenderer.IconCache<String, String>(2, released::add);
        cache.put("a", "image a");
        cache.put("b", "image b");
        cache.request("a");
        cache.put("c", "image c");
        assertEquals(List.of("image b"), released);
        assertNull(cache.request("b"));
        assertEquals("image a", cache.request("a"));
        cache.clear();
        assertEquals(3, released.size());
        assertTrue(released.containsAll(List.of("image a", "image b", "image c")));
        assertTrue(cache.takeRequests().isEmpty());
        cache.clear();
        assertEquals(3, released.size());
    }

    @Test
    void replacingAResourceReleasesThePreviousGeneration() {
        var released = new ArrayList<String>();
        var cache = new ItemIconRenderer.IconCache<String, String>(1, released::add);
        cache.put("item", "old");
        cache.put("item", "new");
        assertEquals(List.of("old"), released);
        assertEquals("new", cache.request("item"));
        cache.clear();
        assertEquals(List.of("old", "new"), released);
    }
}
