package com.atom.chat.reaction;

import com.atom.chat.chat.ChatMessage;

import java.util.*;
import java.util.function.*;

/** Reactions bind to server delivery IDs; identical display text never defines identity. */
public final class ReactionClient {
    private ReactionClient() {}
    public static BooleanSupplier supported=()->false;
    public static BooleanSupplier expandedSupported=()->false;
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
    private static volatile long generation;
    /**
     * Bumped on every accepted snapshot. Readers that cache geometry derived
     * from chip counts (the message list's content height) watch this instead
     * of re-deriving the counts every frame.
     */
    private static volatile long revision;

    public static long generation() { return generation; }
    public static long revision() { return revision; }
    public static String takeError() { String result=lastError; lastError=null; return result; }

    public static void reset() {
        generation++;
        lastError=null;
        snapshots.clear();
        arrivals.clear();
        KEYS.clear();
        receipts.clear();
        pendingReceipts=0;
        activeDelivery.remove();
        revision++;
    }

    private static <K,V> void cap(LinkedHashMap<K,V> map) {
        while(map.size()>4096) { var it=map.keySet().iterator(); it.next(); it.remove(); }
    }

    private static final LinkedHashMap<Long,UUID> KEYS=new LinkedHashMap<>();
    private static final LinkedHashMap<UUID,ArrayDeque<UUID>> receipts=new LinkedHashMap<>();
    private static int pendingReceipts;
    private static final ThreadLocal<UUID> activeDelivery=new ThreadLocal<>();

    public static UUID key(ChatMessage row) {
        return row==null || row.isSystem() ? null : KEYS.get(row.getId());
    }

    public static void bind(ChatMessage row) {
        UUID id=activeDelivery.get();
        if(id!=null && row!=null && !row.isSystem()) { KEYS.put(row.getId(),id); cap(KEYS); }
    }

    public static Delivery incoming(UUID fingerprint) {
        ArrayDeque<UUID> pending=receipts.get(fingerprint);
        UUID id=pending==null ? null : pending.pollFirst();
        if(id!=null) pendingReceipts--;
        if(pending!=null && pending.isEmpty()) receipts.remove(fingerprint);
        return new Delivery(id);
    }

    public static final class Delivery implements AutoCloseable {
        private final UUID previous=activeDelivery.get();
        private final long epoch=generation;
        private Delivery(UUID id) { if(id==null) activeDelivery.remove(); else activeDelivery.set(id); }
        @Override public void close() {
            if(epoch!=generation || previous==null) activeDelivery.remove(); else activeDelivery.set(previous);
        }
    }

    public static BooleanSupplier defer(BooleanSupplier callback) {
        UUID id=activeDelivery.get(); long epoch=generation;
        return ()->{ try(var ignored=new Delivery(epoch==generation ? id : null)) { return callback.getAsBoolean(); } };
    }

    private static void receipt(ReactionWire.Receipt receipt) {
        receipts.computeIfAbsent(receipt.fingerprint(),ignored->new ArrayDeque<>()).addLast(receipt.id());
        pendingReceipts++;
        while(pendingReceipts>4096) {
            var first=receipts.entrySet().iterator(); var entry=first.next();
            entry.getValue().removeFirst(); pendingReceipts--;
            if(entry.getValue().isEmpty()) first.remove();
        }
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
        if(com.atom.chat.onboarding.TutorialSandbox.active()){com.atom.chat.onboarding.TutorialSandbox.action();return;}
        UUID k=key(row);
        if(k==null || !supported.getAsBoolean() || !ReactionEmoji.allowed(emoji)) return;
        if (!ReactionEmoji.legacy(emoji) && !expandedSupported.getAsBoolean()) {
            lastError = "update_for_emoji";
            return;
        }
        boolean active=entries(row).stream().filter(e->e.emoji().equals(emoji)).noneMatch(ReactionClient::own);
        sender.accept(ReactionWire.action(new ReactionWire.Action(k,emoji,active)));
    }

    public static void accept(byte[] bytes) {
        Object value;
        try { value=ReactionWire.readServer(bytes); } catch(IllegalArgumentException e) { return; }
        if(value instanceof ReactionWire.Receipt r) {
            receipt(r);
        } else if(value instanceof ReactionWire.Snapshot s) {
            ReactionWire.Snapshot old=snapshots.get(s.id());
            if(old==null || s.revision()>old.revision()) {
                snapshots.put(s.id(),s); cap(snapshots);
                recordArrivals(s,System.currentTimeMillis());
                revision++;
            }
        } else if(value instanceof ReactionWire.Rejection r) {
            lastError=r.reason();
        }
    }

    public static void acceptFrom(Object origin,Object current,byte[] bytes) {
        if(origin!=null && origin==current) accept(bytes);
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
