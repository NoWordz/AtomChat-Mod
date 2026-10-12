package com.atom.chat.reaction;

import com.atom.chat.chat.ChatMessage;

import java.util.*;
import java.util.function.*;

/**
 * Content-keyed reactions.
 *
 * <p>The message identity is derived from what every client can see for itself
 * — the sender's name and the visible body — instead of from a server-issued
 * per-message receipt. That is the same trade the plain-text quote prefix
 * already makes: it works on any server, including offline/LAN/plugin servers
 * that hand player chat to the client as a system line with no signature and no
 * {@code MessageBody}, where a signed fingerprint can never be rebuilt client
 * side. The cost is that two identical messages by the same author share one
 * reaction set, which is accepted.</p>
 *
 * <p>The server never recomputes the key: the reacting client sends the key it
 * derived, and the server stores and broadcasts state under it. A client that
 * never received the message has no row for that key and simply renders nothing
 * — so private-message reactions stay invisible to outsiders exactly the way a
 * quote capsule does.</p>
 */
public final class ReactionClient {
    private ReactionClient() {}
    public static BooleanSupplier supported=()->false;
    public static Consumer<byte[]> sender=bytes->{};
    public static Supplier<UUID> self=()->null;

    private static final LinkedHashMap<UUID,ReactionWire.Snapshot> snapshots=new LinkedHashMap<>();
    /**
     * Per message key, per emoji: when this client first saw that emoji on that
     * message. The chips read this to tell a chip that just arrived (play its
     * entrance) from one that was already there when the row came into view
     * (paint it immediately). Only a first appearance is recorded — a later
     * count change (someone else reacting) must bump the number, not re-enter
     * the chip, so it deliberately does not move this timestamp.
     */
    private static final LinkedHashMap<UUID,LinkedHashMap<String,Long>> arrivals=new LinkedHashMap<>();
    private static String lastError;
    private static long generation;

    public static long generation() { return generation; }
    public static String takeError() { String result=lastError; lastError=null; return result; }

    public static void reset() {
        generation++;
        lastError=null;
        snapshots.clear();
        arrivals.clear();
    }

    private static <K,V> void cap(LinkedHashMap<K,V> map) {
        while(map.size()>4096) { var it=map.keySet().iterator(); it.next(); it.remove(); }
    }

    /**
     * The identity every client computes for a row, or null when the row cannot
     * carry reactions (system line, or missing author/body).
     */
    public static UUID key(ChatMessage row) {
        if(row==null || row.isSystem()) return null;
        String author = row.getProfileName()!=null ? row.getProfileName() : row.getSenderName();
        String body = row.getContentText();
        if(author==null || author.isBlank() || body==null) return null;
        return ReactionFingerprint.contentKey(author, body);
    }

    /**
     * When {@code emoji} was first seen on {@code key}'s message, in epoch ms,
     * or 0 when this client has never seen that pair — in which case a chip
     * must paint statically rather than animate in.
     */
    public static long arrival(UUID key,String emoji) {
        LinkedHashMap<String,Long> row=arrivals.get(key);
        if(row==null) return 0L;
        Long at=row.get(emoji);
        return at==null?0L:at;
    }

    /** True when this client has a live reaction channel; false means no reactions anywhere. */
    public static boolean enabled() { return supported.getAsBoolean(); }

    public static boolean available(ChatMessage row) {
        return enabled() && key(row)!=null;
    }

    public static List<ReactionWire.Entry> entries(ChatMessage row) {
        return entries(key(row));
    }

    /** Key-taking overload so a caller that already computed the key does not hash the row twice. */
    public static List<ReactionWire.Entry> entries(UUID k) {
        if(k==null) return List.of();
        ReactionWire.Snapshot snapshot=snapshots.get(k);
        return snapshot==null ? List.of() : snapshot.entries();
    }

    public static boolean own(ReactionWire.Entry entry) {
        UUID me=self.get();
        return me!=null && entry.members().stream().anyMatch(m->me.equals(m.uuid()));
    }

    public static void toggle(ChatMessage row,String emoji) {
        UUID k=key(row);
        if(k==null || !supported.getAsBoolean() || !ReactionEmoji.allowed(emoji)) return;
        boolean active=entries(row).stream().filter(e->e.emoji().equals(emoji)).noneMatch(ReactionClient::own);
        sender.accept(ReactionWire.action(new ReactionWire.Action(k,emoji,active)));
    }

    public static void accept(byte[] bytes) {
        Object value;
        try { value=ReactionWire.readServer(bytes); } catch(IllegalArgumentException e) { return; }
        if(value instanceof ReactionWire.Snapshot s) {
            ReactionWire.Snapshot old=snapshots.get(s.id());
            if(old==null || s.revision()>old.revision()) {
                snapshots.put(s.id(),s); cap(snapshots);
                recordArrivals(s,System.currentTimeMillis());
            }
        } else if(value instanceof ReactionWire.Rejection r) {
            lastError=r.reason();
        }
    }

    private static void recordArrivals(ReactionWire.Snapshot snapshot,long now) {
        LinkedHashMap<String,Long> row=arrivals.computeIfAbsent(snapshot.id(),k->new LinkedHashMap<>());
        Set<String> present=new HashSet<>();
        for(ReactionWire.Entry entry:snapshot.entries()) {
            present.add(entry.emoji());
            // First appearance only: a count change must pulse the number, not
            // re-enter the chip, so the timestamp is never moved once set.
            row.putIfAbsent(entry.emoji(),now);
        }
        // An emoji the snapshot no longer carries is gone; drop it so a later
        // re-add is a fresh arrival and plays its own entrance.
        row.keySet().retainAll(present);
        cap(arrivals);
    }
}
