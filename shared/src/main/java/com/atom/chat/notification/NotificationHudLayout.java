package com.atom.chat.notification;

import com.atom.chat.ui.UiLayout;

/** HUD placement is independent of the notification's lifetime and panel geometry. */
public final class NotificationHudLayout {
    public record Placement(UiLayout.Rect rect, float scale) { }
    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;
        public String key() { return "atomchat.settings.notify.position." + name().toLowerCase(java.util.Locale.ROOT); }
        public Anchor next() { return values()[(ordinal() + 1) % values().length]; }
    }
    private NotificationHudLayout() { }
    public static Placement scaled(float width, float height, float baseWidth, float baseHeight,
                                   float margin, Anchor anchor, float offsetX, float offsetY, float requestedScale) {
        float scale = Float.isFinite(requestedScale) ? Math.max(0.5F, Math.min(2.0F, requestedScale)) : 1;
        scale = Math.max(0.01F, Math.min(scale, Math.min(
                Math.max(1, width - margin * 2) / Math.max(1, baseWidth),
                Math.max(1, height - margin * 2) / Math.max(1, baseHeight))));
        return new Placement(rect(width, height, baseWidth * scale, baseHeight * scale,
                margin, anchor, Float.isFinite(offsetX) ? offsetX : 0, Float.isFinite(offsetY) ? offsetY : 0), scale);
    }
    public static boolean visible(boolean world, boolean player, boolean screen, boolean hidden, boolean debug) {
        return world && player && !screen && !hidden && !debug;
    }
    public static UiLayout.Rect rect(float width, float height, float cardWidth, float stackHeight,
                                      float margin, Anchor anchor, float offsetX, float offsetY) {
        float w = Math.max(0, Math.min(cardWidth, width - 2 * Math.min(margin, width / 2)));
        float h = Math.max(0, Math.min(stackHeight, height - 2 * Math.min(margin, height / 2)));
        int index = (anchor == null ? Anchor.TOP_CENTER : anchor).ordinal();
        float x = switch (index % 3) { case 0 -> margin; case 1 -> (width - w) / 2; default -> width - margin - w; };
        float y = switch (index / 3) { case 0 -> margin; case 1 -> (height - h) / 2; default -> height - margin - h; };
        x = Math.max(0, Math.min(Math.max(0, width - w), x + offsetX));
        y = Math.max(0, Math.min(Math.max(0, height - h), y + offsetY));
        return new UiLayout.Rect(x, y, w, h);
    }
}
