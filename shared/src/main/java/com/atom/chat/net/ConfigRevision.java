package com.atom.chat.net;

/** Both legacy saves and the optional management channel share one concurrency revision. */
public final class ConfigRevision {
    private ConfigRevision() {}
    private static final java.util.concurrent.atomic.AtomicInteger VALUE=new java.util.concurrent.atomic.AtomicInteger(1);
    public static int get(){return VALUE.get();}
    public static int incrementAndGet(){return VALUE.incrementAndGet();}
}
