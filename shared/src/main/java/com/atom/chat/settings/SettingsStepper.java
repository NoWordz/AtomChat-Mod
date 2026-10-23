package com.atom.chat.settings;

import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;

import java.util.HashMap;
import java.util.Map;

/** Pure cycle and geometry shared by the setting selector's paint and hit paths. */
public final class SettingsStepper {
    private SettingsStepper() { }

    public static boolean supports(String action) {
        return "avatar_style".equals(action) || "page_nav_style".equals(action)
                || "teleport_mode".equals(action);
    }

    public static String teleport(String current, int direction) {
        int index = switch (current == null ? "auto" : current) {
            case "tp" -> 1;
            case "tpa" -> 2;
            default -> 0;
        };
        return new String[]{"auto", "tp", "tpa"}[Math.floorMod(index + direction, 3)];
    }

    public record Geometry(UiLayout.Rect previous, UiLayout.Rect value, UiLayout.Rect next) {
        public int directionAt(float x, float y) {
            if (previous.w() > 0 && previous.contains(x, y)) return -1;
            if (next.w() > 0 && next.contains(x, y)) return 1;
            return 0;
        }
    }

    /** One arrow owns one hover animation; action ids keep CHAT's selectors isolated. */
    public record ArrowHover(String actionId, int direction) {
        public ArrowHover {
            if (actionId == null || actionId.isEmpty()) {
                throw new IllegalArgumentException("actionId");
            }
            if (direction != -1 && direction != 1) {
                throw new IllegalArgumentException("direction");
            }
        }
    }

    /**
     * Per-arrow hover weights. Only the arrow under the pointer is active; all
     * other keys settle back independently, including the opposite arrow in
     * the same row and the other selector on the CHAT page.
     */
    public static final class HoverState {
        private final Map<ArrowHover, Float> weights = new HashMap<>();

        public void advance(ArrowHover active, float dtMs, boolean animated) {
            if (active != null) {
                weights.putIfAbsent(active, 0.0F);
            }
            var iterator = weights.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                float target = entry.getKey().equals(active) ? 1.0F : 0.0F;
                float next = UiMotion.expApproach(entry.getValue(), target, dtMs,
                        animated ? UiMotion.HOVER_MS : 0.0F);
                if (next == 0.0F && target == 0.0F) {
                    iterator.remove();
                } else {
                    entry.setValue(next);
                }
            }
        }

        public float weight(String actionId, int direction) {
            return weights.getOrDefault(new ArrowHover(actionId, direction), 0.0F);
        }

        public void clear() {
            weights.clear();
        }
    }

    public static float preferredWidth(float textWidth) {
        return Math.min(UiTokens.SETTINGS_STEPPER_VALUE_MAX, Math.max(0, textWidth))
                + 2 * (UiTokens.SETTINGS_STEPPER_ARROW + UiTokens.SETTINGS_STEPPER_GAP);
    }

    public static Geometry layout(UiLayout.Rect row, float textWidth, boolean stacked) {
        float pad = Math.min(UiTokens.SETTINGS_ROW_PAD, row.w() / 4);
        float available = Math.max(0, row.w() - pad * 2);
        float button = Math.min(UiTokens.SETTINGS_STEPPER_ARROW, available / 2);
        float gap = Math.min(UiTokens.SETTINGS_STEPPER_GAP, Math.max(0, (available - button * 2) / 2));
        float valueW = Math.min(Math.min(UiTokens.SETTINGS_STEPPER_VALUE_MAX, Math.max(0, textWidth)),
                Math.max(0, available - 2 * (button + gap)));
        float right = row.right() - pad;
        float cy = stacked ? row.bottom() - UiTokens.SETTINGS_ROW_PAD - button / 2
                : row.y() + row.h() / 2;
        var next = new UiLayout.Rect(right - button, cy - button / 2, button, button);
        var value = new UiLayout.Rect(next.x() - gap - valueW, next.y(), valueW, button);
        var previous = new UiLayout.Rect(value.x() - gap - button, next.y(), button, button);
        return new Geometry(previous, value, next);
    }
}
