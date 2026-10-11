package com.atom.chat.fun;
/** One three-second burst. A busy session drops triggers, never queues them. */
public final class ChatEffectSession {
    public static final long DURATION_MS = 3000;
    private ChatEffectType type;
    private long started;
    public boolean trigger(ChatEffectType next, long nowMs) {
        if(next==null || active(nowMs)) return false;
        type=next; started=nowMs; return true;
    }
    public boolean active(long nowMs) {
        if(type==null) return false;
        if(nowMs<started || nowMs-started>=DURATION_MS) { clear(); return false; }
        return true;
    }
    public float progress(long nowMs) { return active(nowMs) ? (nowMs-started)/(float)DURATION_MS : 1; }
    public ChatEffectType type() { return type; }
    public long seed() { return started; }
    public void clear() { type=null; }
}
