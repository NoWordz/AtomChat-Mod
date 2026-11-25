package com.atom.chat.channel;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChannelInboxTest {
    private final ChannelInbox inbox = new ChannelInbox();
    private final Object connection = new Object();
    private final UUID session = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final ChannelWire.Key alpha = new ChannelWire.Key("east", "alpha");
    private final ChannelWire.Key west = new ChannelWire.Key("west", "alpha");
    private static final long START = 10;

    @Test void sameTextAndDisplayNameStayInTheirOwnFeedsAndRevocationPurgesHistoryAndUnread() {
        inbox.connection(connection);
        var hello = inbox.offer(connection, new ChannelWire.Offer(session), START);
        UUID nonce = hello.nonce();
        assertFalse(inbox.message(connection, message(session, nonce, 1, alpha), "same", m -> false, START));
        assertTrue(inbox.snapshot(connection, snapshot(nonce, 2, alpha, west), START));
        assertTrue(inbox.message(connection, message(session, nonce, 3, alpha), "same", m -> false, START));
        assertTrue(inbox.message(connection, message(session, nonce, 4, west), "same", m -> false, START));
        assertEquals(1, inbox.messages(alpha, START).size());
        assertEquals(1, inbox.messages(west, START).size());
        assertEquals(List.of(1, 1), inbox.rows(START).stream().map(ChannelInbox.Row::unread).toList());
        assertTrue(inbox.open(west, START));
        assertTrue(inbox.snapshot(connection, snapshot(nonce, 5, alpha), START));
        assertEquals(List.of(), inbox.messages(west, START));
        assertFalse(inbox.open(west, START));
        assertFalse(inbox.message(connection, message(session, nonce, 6, west), "same", m -> false, START));
    }

    @Test void oldConnectionSessionDuplicateAndHiddenAuthorAreRejected() {
        inbox.connection(connection);
        var hello = inbox.offer(connection, new ChannelWire.Offer(session), START);
        var nonce = hello.nonce();
        assertFalse(inbox.snapshot(new Object(), snapshot(nonce, 1, alpha), START));
        assertTrue(inbox.snapshot(connection, snapshot(nonce, 2, alpha), START));
        var visible = message(session, nonce, 3, alpha);
        assertTrue(inbox.message(connection, visible, "same", m -> false, START));
        assertFalse(inbox.message(connection,
                new ChannelWire.Delivery(session, nonce, 4, alpha, visible.deliveryId(), author, "Alex", "{}"),
                "same", m -> false, START));
        assertFalse(inbox.message(connection, message(session, nonce, 5, alpha), "same", m -> true, START));
        var next = UUID.randomUUID();
        assertNotNull(inbox.offer(connection, new ChannelWire.Offer(next), START));
        assertNull(inbox.offer(connection, new ChannelWire.Offer(session), START));
        assertFalse(inbox.snapshot(connection, snapshot(nonce, 9, alpha), START));
        inbox.connection(new Object());
        assertEquals(List.of(), inbox.rows(START));
    }

    @Test void staleSnapshotDoesNotKeepAuthority() {
        inbox.connection(connection);
        var hello = inbox.offer(connection, new ChannelWire.Offer(session), START);
        assertTrue(inbox.snapshot(connection, snapshot(hello.nonce(), 1, alpha), START));
        long afterDeadline = START + 5_000_000_000L;
        assertEquals(List.of(), inbox.rows(afterDeadline));
        assertFalse(inbox.message(connection, message(session, hello.nonce(), 2, alpha), "late", m -> false, afterDeadline));
    }

    private ChannelWire.Snapshot snapshot(UUID nonce, long seq, ChannelWire.Key... keys) {
        return new ChannelWire.Snapshot(session, nonce, seq,
                java.util.Arrays.stream(keys).map(key -> new ChannelWire.Entry(key, "Same")).toList());
    }

    private ChannelWire.Delivery message(UUID session, UUID nonce, long seq, ChannelWire.Key key) {
        return new ChannelWire.Delivery(session, nonce, seq, key, UUID.randomUUID(), author, "Alex", "{}");
    }
}
