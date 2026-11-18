package com.atom.chat.settings.contract;

import com.atom.lib.design.atomchat.AtomChatControlStyle;
import com.atom.lib.frontend.StrictPageSession;

/** Approved numeric style parameters used by both settings number fields. */
final class SettingsFrameColors {
    private SettingsFrameColors() {}

    static AtomChatControlStyle numberAppearance(StrictPageSession.FrameBindings frame) {
        int base = color(frame, "cardBase");
        return new AtomChatControlStyle((float) parameter(frame, "cornerRadius"), false,
                color(frame, "accent"), color(frame, "textSecondary"), 1, base, base, color(frame, "textPrimary"));
    }

    static double parameter(StrictPageSession.FrameBindings frame, String name) {
        Double value = frame.parameters().get(name);
        if (value == null) throw new IllegalArgumentException("MISSING_COMPONENT_PARAMETER " + name);
        return value;
    }

    static int color(StrictPageSession.FrameBindings frame, String name) {
        double value = parameter(frame, name);
        if (value < 0 || value > 0xffffffffL || Math.rint(value) != value) {
            throw new IllegalArgumentException("INVALID_COMPONENT_COLOR " + name);
        }
        return (int) (long) value;
    }
}
