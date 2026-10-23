package com.atom.chat.notification;

/** Only a host switch fades; the queue continues to own the original expiry time. */
public final class NotificationHostFade {
    public enum Host { HUD, PANEL }
    public static final long DURATION_MS = com.atom.chat.ui.UiMotion.NOTIFICATION_HOST_FADE_MS;
    private Host previous;
    private long changed;
    public float alpha(Host host, long now, boolean animated) {
        if (!animated || previous == null) {
            previous = host;
            changed = now - DURATION_MS;
        } else if (previous != host) {
            previous = host;
            changed = now;
        }
        float t = Math.max(0, Math.min(1, (now - changed) / (float) DURATION_MS));
        return 1 - (1 - t) * (1 - t);
    }
    public void reset() { previous = null; }
}
