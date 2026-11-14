package com.atom.chat.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class MediaFetchRequestsTest {
    @Test void oldQueuedFetchCannotBeSentOnNewConnectionOrRemoveItsReplacement() {
        var uploads = new MediaUploadSchedulerTest.Fixture().scheduler;
        var client = new MediaUploadSchedulerTest.QueueExecutor();
        List<String> sent = new ArrayList<>();
        var fetches = new MediaFetchRequests(uploads, client, sent::add);
        var old = fetches.request(uploads.connectionEpoch(), "image");
        synchronized (uploads) {
            uploads.reset(true);
            fetches.reset();
        }
        var current = fetches.request(uploads.connectionEpoch(), "image");
        fetches.cancel("image", old);
        client.runAll();
        assertEquals(List.of("image"), sent);
        assertEquals("disconnected", assertThrows(CompletionException.class, old::join).getCause().getMessage());
        fetches.onData("image", 0, 2, new byte[]{3});
        assertFalse(current.isDone());
        fetches.onData("image", 1, 2, new byte[]{4});
        assertArrayEquals(new byte[]{3, 4}, current.join());
    }

    @Test void staleEpochAtAdmissionAndCancelledSendNeverReachTransport() {
        var uploads = new MediaUploadSchedulerTest.Fixture().scheduler;
        long oldEpoch = uploads.connectionEpoch();
        uploads.reset(true);
        var client = new MediaUploadSchedulerTest.QueueExecutor();
        List<String> sent = new ArrayList<>();
        var fetches = new MediaFetchRequests(uploads, client, sent::add);
        var stale = fetches.request(oldEpoch, "old");
        assertTrue(stale.isCompletedExceptionally());
        var cancelled = fetches.request(uploads.connectionEpoch(), "cancelled");
        fetches.cancel("cancelled", cancelled);
        client.runAll();
        assertTrue(sent.isEmpty());
        assertEquals("cancelled", assertThrows(CompletionException.class, cancelled::join).getCause().getMessage());
    }

    @Test void malformedMissingAndRejectedRequestsCompleteOnce() {
        var uploads = new MediaUploadSchedulerTest.Fixture().scheduler;
        var fetches = new MediaFetchRequests(uploads, Runnable::run, id -> {});
        var malformed = fetches.request(uploads.connectionEpoch(), "bad");
        fetches.onData("bad", 1, 2, new byte[]{1});
        fetches.onMissing("bad");
        assertTrue(malformed.isCompletedExceptionally());
        var missing = fetches.request(uploads.connectionEpoch(), "gone");
        fetches.onMissing("gone");
        fetches.onData("gone", 0, 1, new byte[]{1});
        assertTrue(missing.isCompletedExceptionally());
        var rejected = new MediaFetchRequests(uploads, r -> { throw new java.util.concurrent.RejectedExecutionException(); }, id -> {});
        assertTrue(rejected.request(uploads.connectionEpoch(), "x").isCompletedExceptionally());
    }
}
