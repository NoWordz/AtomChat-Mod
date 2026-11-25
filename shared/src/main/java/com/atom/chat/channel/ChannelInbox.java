package com.atom.chat.channel;

import java.util.*;
import java.util.function.Predicate;

/** Ephemeral, connection-scoped inbox. No public/private chat store dependency. */
public final class ChannelInbox {
    public static final ChannelInbox INSTANCE = new ChannelInbox();
    private static final long LEASE = 5_000_000_000L;

    public record Row(ChannelWire.Entry entry, int unread) {}
    public record Message(UUID id, UUID author, String name, String text) {}

    private static final class Feed {
        ChannelWire.Entry entry;
        final ArrayDeque<Message> messages = new ArrayDeque<>();
        int unread;
        Feed(ChannelWire.Entry entry) { this.entry = entry; }
    }

    private final Map<ChannelWire.Key, Feed> feeds = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> deliveries = new LinkedHashSet<>();
    private final Set<UUID> retired = new HashSet<>();
    private Object connection;
    private UUID session, nonce;
    private ChannelWire.Key open;
    private long sequence, deadline, revision;

    public synchronized void connection(Object current) {
        if (connection == current) return;
        connection = current;
        retired.clear();
        session = nonce = null;
        clear();
    }

    public synchronized ChannelWire.Hello offer(Object origin, ChannelWire.Offer offer, long now) {
        if (origin == null || origin != connection || retired.contains(offer.session())) return null;
        if (!offer.session().equals(session)) {
            if (retired.size() >= 128) return null;
            if (session != null) retired.add(session);
            session = offer.session();
            nonce = UUID.randomUUID();
            clear();
        }
        expire(now);
        if (nonce == null) nonce = UUID.randomUUID();
        return deadline == 0 ? new ChannelWire.Hello(session, nonce) : null;
    }

    public synchronized ChannelWire.Stop disable(Object origin, ChannelWire.Offer offer) {
        if (origin == null || origin != connection || !offer.session().equals(session) || nonce == null) return null;
        var stop = new ChannelWire.Stop(session, nonce);
        nonce = null;
        clear();
        return stop;
    }

    public synchronized boolean snapshot(Object origin, ChannelWire.Snapshot snapshot, long now) {
        if (!matches(origin, snapshot.session(), snapshot.nonce(), snapshot.sequence())) return false;
        expire(now);
        sequence = snapshot.sequence();
        deadline = now + LEASE;
        Set<ChannelWire.Key> allowed = new HashSet<>();
        for (var entry : snapshot.channels()) {
            allowed.add(entry.key());
            Feed feed = feeds.get(entry.key());
            if (feed == null) {
                feeds.put(entry.key(), new Feed(entry));
                revision++;
            } else if (!entry.equals(feed.entry)) {
                feed.entry = entry;
                revision++;
            }
        }
        if (feeds.keySet().retainAll(allowed)) revision++;
        if (open != null && !allowed.contains(open)) open = null;
        return true;
    }

    public synchronized boolean message(Object origin, ChannelWire.Delivery delivery, String visible,
                                        Predicate<ChannelWire.Delivery> blocked, long now) {
        expire(now);
        if (!matches(origin, delivery.session(), delivery.nonce(), delivery.sequence())) return false;
        sequence = delivery.sequence();
        Feed feed = feeds.get(delivery.key());
        if (feed == null || visible == null || visible.isBlank() || blocked.test(delivery)
                || !deliveries.add(delivery.deliveryId())) return false;
        if (deliveries.size() > 512) deliveries.remove(deliveries.iterator().next());
        feed.messages.addLast(new Message(delivery.deliveryId(), delivery.author(), delivery.name(), visible));
        if (feed.messages.size() > 120) feed.messages.removeFirst();
        if (!delivery.key().equals(open)) feed.unread = Math.min(100, feed.unread + 1);
        revision++;
        return true;
    }

    public synchronized boolean open(ChannelWire.Key key, long now) {
        expire(now);
        Feed feed = feeds.get(key);
        if (feed == null) return false;
        open = key;
        if (feed.unread != 0) revision++;
        feed.unread = 0;
        return true;
    }

    public synchronized void close(ChannelWire.Key key) {
        if (Objects.equals(open, key)) open = null;
    }

    public synchronized List<Row> rows(long now) {
        expire(now);
        return feeds.values().stream().map(feed -> new Row(feed.entry, feed.unread)).toList();
    }

    public synchronized List<Message> messages(ChannelWire.Key key, long now) {
        expire(now);
        Feed feed = feeds.get(key);
        return feed == null ? List.of() : List.copyOf(feed.messages);
    }

    public synchronized long revision(long now) {
        expire(now);
        return revision;
    }

    private boolean matches(Object origin, UUID session, UUID nonce, long sequence) {
        return origin != null && origin == connection && session.equals(this.session)
                && nonce.equals(this.nonce) && sequence > this.sequence;
    }

    private void expire(long now) {
        if (deadline != 0 && now - deadline >= 0) {
            deadline = 0;
            feeds.clear();
            open = null;
            revision++;
        }
    }

    private void clear() {
        deadline = sequence = 0;
        open = null;
        feeds.clear();
        deliveries.clear();
        revision++;
    }
}
