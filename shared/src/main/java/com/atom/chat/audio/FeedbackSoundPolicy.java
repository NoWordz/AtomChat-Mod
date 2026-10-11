package com.atom.chat.audio;
/** Pure settings and action priority rules. */
public final class FeedbackSoundPolicy {
    private FeedbackSoundPolicy() {}
    public static float volume(boolean master,boolean type,float value) {
        return master && type && Float.isFinite(value) ? Math.max(0,Math.min(1,value)) : 0;
    }
    public static FeedbackCue choose(FeedbackCue current,FeedbackCue next) {
        if(current==null) return next;
        return next!=null && next.ordinal()>current.ordinal() ? next : current;
    }
}
