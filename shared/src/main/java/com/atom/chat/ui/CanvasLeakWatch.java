package com.atom.chat.ui;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import io.github.humbleui.skija.Canvas;

/**
 * The canvas save-stack leak probe (D25).
 *
 * <p><b>Why this class exists.</b> The probe is three rules that used to be a
 * private shell method: only report when something actually leaked, only when
 * the debug flag is on, and never log every occurrence (the first one, then
 * every two-hundredth) — a real leak fires once per frame, and a per-frame stack
 * trace is how a one-line cause becomes an unreadable log. Those rules are
 * asserted offline now instead of being remembered.
 *
 * <p><b>Bucket.</b> Shared single source: {@code Canvas} is Skija's, the debug
 * gate is the shared config and the logger is the mod's own.
 */
public final class CanvasLeakWatch {

    private static int occurrences;

    private CanvasLeakWatch() {
    }

    /**
     * Records one draw call's save-stack difference. Called by the shell after
     * every layered draw and by {@code NotificationJumpService} through the same
     * measurement, so both keep counting into one occurrence series.
     */
    public static void probe(String where, int saveCountBefore, Canvas canvas) {
        int leaked = canvas.getSaveCount() - saveCountBefore;
        if (leaked == 0 || !AtomChatConfig.get().debug) {
            return;
        }
        occurrences++;
        if (shouldLog(occurrences)) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "Leak probe: {} leaked {} stack entries (occurrence {})"),
                    where, leaked, occurrences);
        }
    }

    /**
     * The log throttle: the first leak is always reported, then every
     * two-hundredth. A leak that only ever happens once must not be swallowed,
     * and one that happens every frame must not flood.
     */
    static boolean shouldLog(int occurrence) {
        return occurrence == 1 || occurrence % 200 == 0;
    }

    /** How many leaks have been observed (tests assert the gates through this). */
    public static int occurrences() {
        return occurrences;
    }

    static void resetForTest() {
        occurrences = 0;
    }
}
