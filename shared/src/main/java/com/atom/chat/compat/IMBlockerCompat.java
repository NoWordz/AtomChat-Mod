package com.atom.chat.compat;

import com.atom.chat.AtomChat;

import java.lang.reflect.Method;

/**
 * Port of e33chat's IMBlocker bridge (2.3.6). IMBlocker normally flips the
 * IME to English while a command is being typed by listening to vanilla
 * ChatScreen's chat-field change listener — a hook that never fires here
 * because the hidden EditBox drives the Skia composer instead.
 *
 * <p>The bridge mirrors the same effect by reflection: it calls
 * {@code MinecraftTextFieldWidget#setPreferredEnglishState(boolean)} on the
 * chat field whenever the text gains or loses its leading "/". Nothing
 * compiles against IMBlocker classes — when the mod is absent the lookup
 * fails once and this becomes a no-op.
 */
public final class IMBlockerCompat {
    private IMBlockerCompat() {
    }

    private static boolean resolved;
    private static boolean available;
    private static Method setPreferredEnglishState;

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> iface = Class.forName(
                    "io.github.reserveword.imblocker.common.gui.MinecraftTextFieldWidget");
            setPreferredEnglishState = iface.getMethod("setPreferredEnglishState", boolean.class);
            available = true;
        } catch (Throwable t) {
            // IMBlocker is not installed — degrade to no-op.
            AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message("compat", "IMBlocker not present, IME state sync disabled: {}"), t.toString());
        }
    }

    /**
     * Asks IMBlocker (if installed) to set the IME conversion state of the
     * chat field: English while typing a command, native otherwise. No-op
     * when IMBlocker is absent or the call fails.
     */
    public static void setCommandMode(Object textField, boolean command) {
        resolve();
        if (!available || textField == null) {
            return;
        }
        try {
            setPreferredEnglishState.invoke(textField, command);
        } catch (Throwable t) {
            AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message("compat", "IMBlocker sync failed: {}"), t.toString());
        }
    }
}
