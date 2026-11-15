package com.atom.chat.reaction;

import java.util.*;

/** Server-thread delivery bookkeeping; no text, clocks or receiver-local counters identify a message. */
public final class ReactionDeliveryRegistry {
    private static final int LIMIT=4096;
    private record Prepared(Object component, UUID id, boolean overlay) { }
    private static final class NativeEvent {
        final UUID id=UUID.randomUUID();
        final Set<UUID> recipients=new HashSet<>();
    }
    private final ThreadLocal<Map<Object,Prepared>> prepared=new ThreadLocal<>();
    private final LinkedHashMap<UUID,NativeEvent> nativeEvents=new LinkedHashMap<>();
    private final LinkedHashMap<UUID,Set<UUID>> audiences=new LinkedHashMap<>();

    public Scope scope(Object receiver,Object component,UUID id) {
        return scope(receiver,component,id,false);
    }
    public Scope scope(Object receiver,Object component,UUID id,boolean overlay) {
        Map<Object,Prepared> pending=prepared.get();
        if(pending==null) { pending=new IdentityHashMap<>(); prepared.set(pending); }
        Prepared previous=pending.put(receiver,new Prepared(component,id,overlay));
        return new Scope(receiver,previous,pending);
    }
    public final class Scope implements AutoCloseable {
        private final Object receiver;
        private final Prepared previous;
        private final Map<Object,Prepared> pending;
        private Scope(Object receiver,Prepared previous,Map<Object,Prepared> pending) {
            this.receiver=receiver;this.previous=previous;this.pending=pending;
        }
        @Override public void close() {
            if(previous==null) pending.remove(receiver); else pending.put(receiver,previous);
            if(pending.isEmpty()) prepared.remove(); else prepared.set(pending);
        }
    }
    public UUID take(Object receiver,Object component) {
        return take(receiver,component,false);
    }
    public UUID take(Object receiver,Object component,boolean overlay) {
        Map<Object,Prepared> pending=prepared.get();
        Prepared entry=pending==null ? null : pending.get(receiver);
        if(entry==null || entry.component()!=component || entry.overlay()!=overlay) return null;
        pending.remove(receiver);
        return entry.id();
    }
    public synchronized UUID nativeId(UUID fingerprint,UUID receiver) {
        NativeEvent event=nativeEvents.get(fingerprint);
        if(event==null || event.recipients.contains(receiver)) {
            event=new NativeEvent();
            nativeEvents.put(fingerprint,event);
            trim(nativeEvents);
        }
        event.recipients.add(receiver);
        return event.id;
    }
    public synchronized void register(UUID id,UUID receiver) {
        audiences.computeIfAbsent(id,ignored->new HashSet<>()).add(receiver);
        trim(audiences);
    }
    public synchronized boolean received(UUID id,UUID receiver) {
        Set<UUID> audience=audiences.get(id);
        return audience!=null && audience.contains(receiver);
    }
    private static void trim(LinkedHashMap<?,?> map) {
        while(map.size()>LIMIT) { var it=map.keySet().iterator();it.next();it.remove(); }
    }
}
