package com.atom.chat.diagnostics;

import com.atom.chat.AtomChat;

/** Debug-only, bounded state traces. Never accepts typed characters, message text or image URLs. */
public final class FaultTrace {
    public enum Kind { KEY, CHAR, UPLOAD, THEME, FOCUS }
    private static final long[] last = new long[Kind.values().length];
    private static long uploadWindow;
    private static int uploadBudget;
    private FaultTrace() { }
    public static boolean claim(Kind kind) {
        if (!FrameProfile.enabled()) return false;
        long now=System.nanoTime();
        synchronized(last) {
            if(kind==Kind.UPLOAD) {
                if(now-uploadWindow>=10_000_000_000L || uploadWindow==0) {uploadWindow=now;uploadBudget=32;}
                return uploadBudget>0 && uploadBudget-->0;
            }
            if(now-last[kind.ordinal()]<1_000_000_000L) return false;
            last[kind.ordinal()]=now;
            return true;
        }
    }
    public static void input(boolean character, String consumer) {
        Kind kind=character?Kind.CHAR:Kind.KEY;
        if(claim(kind)) AtomChat.LOGGER.info("[AtomChat][fault] event={} consumer={}",kind,consumer);
    }
    public static void upload(long request, String stage, long epoch) {
        if(claim(Kind.UPLOAD)) AtomChat.LOGGER.info("[AtomChat][fault] upload={} stage={} epoch={}",request,stage,epoch);
    }
    public static void theme(String name, boolean saved) {
        if(claim(Kind.THEME)) AtomChat.LOGGER.info("[AtomChat][fault] theme={} saved={}",name,saved);
    }
    public static void focus(String page, boolean composer, boolean field, boolean readOnly, boolean modal, boolean tutorial) {
        if(claim(Kind.FOCUS)) AtomChat.LOGGER.info("[AtomChat][fault] page={} focus={} field={} readonly={} modal={} tutorial={}",
                page,composer,field,readOnly,modal,tutorial);
    }
}
