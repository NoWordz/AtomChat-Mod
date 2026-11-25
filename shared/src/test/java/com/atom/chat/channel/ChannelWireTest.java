package com.atom.chat.channel;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChannelWireTest {
    @Test void carriesOneComponentWithTheCurrentSessionAndStableScopedIdentity() {
        var session = UUID.randomUUID();
        var nonce = UUID.randomUUID();
        var key = new ChannelWire.Key("east", "alpha");
        var frame = new ChannelWire.Delivery(session, nonce, 2, key, UUID.randomUUID(), UUID.randomUUID(),
                "Alex", "{\"text\":\"same: : 文字\"}");
        assertEquals(frame, ChannelWire.decode(ChannelWire.encode(frame)));
        var other = new ChannelWire.Key("west", "alpha");
        var snapshot = new ChannelWire.Snapshot(session, nonce, 1,
                List.of(new ChannelWire.Entry(key, "Same"), new ChannelWire.Entry(other, "Same")));
        byte[] wire = ChannelWire.encode(snapshot);
        assertEquals(snapshot, ChannelWire.decode(wire));
        assertNull(ChannelWire.decode(java.util.Arrays.copyOf(wire, wire.length + 1)));
        assertNull(ChannelWire.decode(java.util.Arrays.copyOf(wire, wire.length - 1)));
    }
}
