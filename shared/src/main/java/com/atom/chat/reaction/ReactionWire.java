package com.atom.chat.reaction;

import java.io.*;
import java.util.*;

/** Bounded optional-channel protocol. Player identity always comes from the connection. */
public final class ReactionWire {
    public static final int MAX_BYTES = 262144;
    public static final int MAX_KINDS = 20;
    public static final int MAX_MEMBERS = 256;
    public record Member(UUID uuid, String name) {}
    public record Entry(String emoji, List<Member> members) {
        public Entry { members = List.copyOf(members); }
    }
    public record Receipt(UUID id, UUID fingerprint) {}
    public record Snapshot(UUID id, long revision, List<Entry> entries) {
        public Snapshot { entries = List.copyOf(entries); }
    }
    public record Action(UUID id, String emoji, boolean active) {}
    public record Rejection(String reason) {}
    private ReactionWire() {}
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static byte[] encode(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { writer.write(out); }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("reaction_packet_too_large");
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("reaction_encode", e); }
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static String text(DataInputStream in, int limit) throws IOException {
        String value = in.readUTF();
        if (value.length() > limit) throw new IOException("reaction_text_limit");
        return value;
    }
    public static byte[] receipt(Receipt r) {
        return encode(out -> { out.writeByte(0); uuid(out, r.id()); uuid(out, r.fingerprint()); });
    }
    public static byte[] snapshot(Snapshot s) {
        return encode(out -> {
            out.writeByte(1); uuid(out, s.id()); out.writeLong(s.revision()); out.writeByte(s.entries().size());
            for (Entry e : s.entries()) {
                out.writeUTF(e.emoji()); out.writeShort(e.members().size());
                for (Member m : e.members()) { uuid(out, m.uuid()); out.writeUTF(m.name()); }
            }
        });
    }
    public static byte[] rejection(String reason) {
        return encode(out -> { out.writeByte(2); out.writeUTF(reason); });
    }
    public static byte[] action(Action a) {
        return encode(out -> { uuid(out, a.id()); out.writeUTF(a.emoji()); out.writeBoolean(a.active()); });
    }
    public static Action readAction(byte[] bytes) {
        try (DataInputStream in = input(bytes, 128)) {
            Action result = new Action(uuid(in), text(in, 16), in.readBoolean());
            if (in.available() != 0 || !ReactionEmoji.allowed(result.emoji())) throw new IOException("reaction_action");
            return result;
        } catch (IOException e) { throw new IllegalArgumentException("reaction_action", e); }
    }
    private static DataInputStream input(byte[] bytes, int limit) throws IOException {
        if (bytes == null || bytes.length > limit) throw new IOException("reaction_packet_limit");
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
    public static Object readServer(byte[] bytes) {
        try (DataInputStream in = input(bytes, MAX_BYTES)) {
            Object result;
            int kind = in.readUnsignedByte();
            if (kind == 0) result = new Receipt(uuid(in), uuid(in));
            else if (kind == 1) {
                UUID id = uuid(in); long revision = in.readLong(); int count = in.readUnsignedByte();
                if (revision < 0 || count > MAX_KINDS) throw new IOException("reaction_snapshot_limit");
                List<Entry> entries = new ArrayList<>(); Set<String> kinds = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    String emoji = text(in, 16); int members = in.readUnsignedShort();
                    if (!ReactionEmoji.allowed(emoji) || !kinds.add(emoji) || members == 0 || members > MAX_MEMBERS)
                        throw new IOException("reaction_entry_limit");
                    List<Member> people = new ArrayList<>(); Set<UUID> seen = new HashSet<>();
                    for (int n = 0; n < members; n++) {
                        UUID person = uuid(in); String name = text(in, 64);
                        if (!seen.add(person)) throw new IOException("reaction_duplicate_member");
                        people.add(new Member(person, name));
                    }
                    entries.add(new Entry(emoji, people));
                }
                result = new Snapshot(id, revision, entries);
            } else if (kind == 2) result = new Rejection(text(in, 64));
            else throw new IOException("reaction_packet_kind");
            if (in.available() != 0) throw new IOException("reaction_trailing_bytes");
            return result;
        } catch (IOException e) { throw new IllegalArgumentException("reaction_server_packet", e); }
    }
}
