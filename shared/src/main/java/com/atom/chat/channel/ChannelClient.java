package com.atom.chat.channel;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Each platform passes the current play handler and only dispatches on its game thread. */
public final class ChannelClient {
    private ChannelClient() {}

    public static void receive(Object connection, byte[] bytes, boolean enabled, Consumer<byte[]> reply,
                               Function<String, String> visible, Predicate<ChannelWire.Delivery> blocked) {
        if (connection == null) return;
        var inbox = ChannelInbox.INSTANCE;
        inbox.connection(connection);
        long now = System.nanoTime();
        var frame = ChannelWire.decode(bytes);
        if (frame instanceof ChannelWire.Offer offer) {
            var response = enabled ? inbox.offer(connection, offer, now) : inbox.disable(connection, offer);
            if (response != null) reply.accept(ChannelWire.encode(response));
        } else if (enabled && frame instanceof ChannelWire.Snapshot snapshot) {
            inbox.snapshot(connection, snapshot, now);
        } else if (enabled && frame instanceof ChannelWire.Delivery delivery) {
            inbox.message(connection, delivery, visible.apply(delivery.component()), blocked, now);
        }
    }
}
