package com.atom.chat.channel;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Wire shared by the Bukkit bridge and all clients. Every delivery carries its own complete component. */
public final class ChannelWire {
    public static final String PATH = "atomchat:trchat_channel";
    public static final int MAX_BYTES = 30_000;
    public static final int MAX_CHANNELS = 64;
    private static final int VERSION = 1;

    private ChannelWire() {}

    public record Key(String backend, String channel) {
        public Key {
            if (!backend.matches("[a-z0-9][a-z0-9_.-]{0,31}")
                    || !channel.matches("[a-z0-9][a-z0-9_.-]{0,47}")) throw new IllegalArgumentException("channel key");
        }
    }
    public record Entry(Key key, String label) {
        public Entry { bounded(label, 96); }
    }
    public sealed interface Frame permits Offer, Hello, Snapshot, Delivery, Stop {}
    public record Offer(UUID session) implements Frame {}
    public record Hello(UUID session, UUID nonce) implements Frame {}
    public record Stop(UUID session, UUID nonce) implements Frame {}
    public record Snapshot(UUID session, UUID nonce, long sequence, List<Entry> channels) implements Frame {
        public Snapshot {
            channels = List.copyOf(channels);
            if (channels.size() > MAX_CHANNELS || channels.stream().map(Entry::key).distinct().count() != channels.size()) {
                throw new IllegalArgumentException("channel list");
            }
        }
    }
    public record Delivery(UUID session, UUID nonce, long sequence, Key key, UUID deliveryId,
                           UUID author, String name, String component) implements Frame {
        public Delivery {
            bounded(name, 64);
            bounded(component, 24_000);
            if (name.isBlank() || author.equals(new UUID(0, 0)) || component.isBlank()) {
                throw new IllegalArgumentException("missing author or component");
            }
        }
    }

    public static byte[] encode(Frame frame) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            if (frame instanceof Offer offer) {
                out.writeByte(1);
                writeUuid(out, offer.session());
            } else if (frame instanceof Hello hello) {
                out.writeByte(2);
                connection(out, hello.session(), hello.nonce());
            } else if (frame instanceof Snapshot snapshot) {
                out.writeByte(3);
                connection(out, snapshot.session(), snapshot.nonce());
                out.writeLong(snapshot.sequence());
                out.writeByte(snapshot.channels().size());
                for (Entry entry : snapshot.channels()) {
                    key(out, entry.key());
                    out.writeUTF(entry.label());
                }
            } else if (frame instanceof Delivery delivery) {
                out.writeByte(4);
                connection(out, delivery.session(), delivery.nonce());
                out.writeLong(delivery.sequence());
                key(out, delivery.key());
                writeUuid(out, delivery.deliveryId());
                writeUuid(out, delivery.author());
                out.writeUTF(delivery.name());
                out.writeUTF(delivery.component());
            } else if (frame instanceof Stop stop) {
                out.writeByte(5);
                connection(out, stop.session(), stop.nonce());
            }
            return bytes.size() <= MAX_BYTES ? bytes.toByteArray() : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    public static Frame decode(byte[] bytes) {
        if (bytes == null || bytes.length < 2 || bytes.length > MAX_BYTES) return null;
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != VERSION) return null;
            Frame frame = switch (in.readUnsignedByte()) {
                case 1 -> new Offer(uuid(in));
                case 2 -> new Hello(uuid(in), uuid(in));
                case 3 -> snapshot(in);
                case 4 -> new Delivery(uuid(in), uuid(in), in.readLong(), key(in), uuid(in), uuid(in),
                        in.readUTF(), in.readUTF());
                case 5 -> new Stop(uuid(in), uuid(in));
                default -> null;
            };
            return in.available() == 0 ? frame : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Snapshot snapshot(DataInputStream in) throws IOException {
        UUID session = uuid(in), nonce = uuid(in);
        long sequence = in.readLong();
        int size = in.readUnsignedByte();
        if (size > MAX_CHANNELS) throw new IOException("channel count");
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(key(in), in.readUTF()));
        return new Snapshot(session, nonce, sequence, entries);
    }

    private static void key(DataOutputStream out, Key key) throws IOException {
        out.writeUTF(key.backend());
        out.writeUTF(key.channel());
    }

    private static Key key(DataInputStream in) throws IOException {
        return new Key(in.readUTF(), in.readUTF());
    }

    private static void connection(DataOutputStream out, UUID session, UUID nonce) throws IOException {
        writeUuid(out, session);
        writeUuid(out, nonce);
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void bounded(String value, int limit) {
        if (value.length() > limit) throw new IllegalArgumentException("field length");
    }
}
