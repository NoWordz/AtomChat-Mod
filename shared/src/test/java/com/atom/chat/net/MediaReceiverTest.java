package com.atom.chat.net;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MediaReceiverTest {
    @Test void oldStateDataMissingAndResultCannotAffectNewConnectionAfterDelayedDelivery() {
        var session = new MediaUploadSchedulerTest.Fixture().scheduler;
        var queue = new MediaUploadSchedulerTest.QueueExecutor();
        Object first = new Object(), next = new Object();
        var active = new AtomicReference<Object>(first);
        var current = new AtomicReference<Object>(first);
        var enabled = new AtomicBoolean(true);
        var resultCalls = new AtomicInteger();
        var fetches = new MediaFetchRequests(session, Runnable::run, id -> {});
        MediaReceiver.enqueue(first, session, queue::execute, active::get, current::get, () -> enabled.set(false));
        MediaReceiver.enqueue(first, session, queue::execute, active::get, current::get,
                () -> fetches.onData("same-id", 0, 2, new byte[]{1}));
        MediaReceiver.enqueue(first, session, queue::execute, active::get, current::get,
                () -> fetches.onMissing("same-id"));
        MediaReceiver.enqueue(first, session, queue::execute, active::get, current::get, resultCalls::incrementAndGet);
        session.reset(true);
        active.set(next);
        current.set(next);
        var pending = fetches.request(session.connectionEpoch(), "same-id");
        queue.runAll();
        assertTrue(enabled.get());
        assertFalse(pending.isDone());
        assertEquals(0, resultCalls.get());
        MediaReceiver.enqueue(next, session, queue::execute, active::get, current::get,
                () -> fetches.onData("same-id", 0, 2, new byte[]{8, 9}));
        queue.runAll();
        assertArrayEquals(new byte[]{8, 9}, pending.join(), "old partial data must not poison the new buffer");
    }

    @Test void originalContextStillRejectsOldPacketEvenIfLoaderAlreadyDelayedItsHandlerUntilAfterJoin() {
        var session = new MediaUploadSchedulerTest.Fixture().scheduler;
        var queue = new MediaUploadSchedulerTest.QueueExecutor();
        Object origin = new Object(), next = new Object();
        session.reset(true);
        var effects = new AtomicInteger();
        MediaReceiver.enqueue(origin, session, queue::execute, () -> next, () -> next, effects::incrementAndGet);
        queue.runAll();
        assertEquals(0, effects.get());
    }

    @Test void epochAndBothTransportIdentitiesMustStillMatchAtExecution() {
        var session = new MediaUploadSchedulerTest.Fixture().scheduler;
        var queue = new MediaUploadSchedulerTest.QueueExecutor();
        Object connection = new Object();
        var current = new AtomicReference<Object>(connection);
        var effects = new AtomicInteger();
        MediaReceiver.enqueue(connection, session, queue::execute, () -> connection, current::get, effects::incrementAndGet);
        session.reset(true); // Even reuse of the same transport cannot adopt a previous epoch's packet.
        queue.runAll();
        assertEquals(0, effects.get());
        MediaReceiver.enqueue(connection, session, queue::execute, () -> connection, current::get, effects::incrementAndGet);
        current.set(new Object());
        queue.runAll();
        assertEquals(0, effects.get());
        MediaReceiver.enqueue(null, session, queue::execute, () -> null, () -> null, effects::incrementAndGet);
        queue.runAll();
        assertEquals(0, effects.get());
    }
}
