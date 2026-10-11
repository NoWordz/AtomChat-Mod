package com.atom.chat.watut;

import com.atom.chat.AtomChat;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Read-only bridge into WATUT (What Are They Up To): AtomChat shows
 * "typing…" in the private chat when the partner is composing a message —
 * QQ-style, and it also replaces the WATUT chat-area hint that the panel
 * otherwise covers.
 *
 * <p>Pure reflection against the installed mod (no compile dependency, no
 * payload receivers to conflict with WATUT's own). Everything is resolved
 * once; when WATUT is absent every query is a cheap false. WATUT only tracks
 * players near the viewer, so a distant partner simply reads as not-typing.
 */
public final class WatutBridge {
    private WatutBridge() {
    }

    private static boolean resolved;
    private static boolean available;
    private static Method getPlayerStatusManagerClient;
    private static Method getStatus;
    private static Method getPlayerChatState;
    private static Object chatTypingState;

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!com.atom.chat.platform.Platform.isModLoaded("watut")) {
            return;
        }
        try {
            Class<?> watutMod = Class.forName("com.corosus.watut.WatutMod");
            Class<?> manager = Class.forName("com.corosus.watut.PlayerStatusManagerClient");
            Class<?> playerStatus = Class.forName("com.corosus.watut.PlayerStatus");
            Class<?> chatState = Class.forName("com.corosus.watut.PlayerStatus$PlayerChatState");
            getPlayerStatusManagerClient = watutMod.getMethod("getPlayerStatusManagerClient");
            getStatus = manager.getMethod("getStatus", UUID.class);
            getPlayerChatState = playerStatus.getMethod("getPlayerChatState");
            chatTypingState = chatState.getField("CHAT_TYPING").get(null);
            available = true;
        } catch (Throwable t) {
            AtomChat.LOGGER.debug(com.atom.chat.diagnostics.AtomChatLog.message("compat", "WATUT bridge unavailable: {}"), t.toString());
        }
    }

    /**
     * True when the given player is currently composing a chat message
     * (WATUT's CHAT_TYPING state), as seen from this client.
     */
    public static boolean isTyping(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        resolve();
        if (!available) {
            return false;
        }
        try {
            Object manager = getPlayerStatusManagerClient.invoke(null);
            if (manager == null) {
                return false;
            }
            Object status = getStatus.invoke(manager, uuid);
            if (status == null) {
                return false;
            }
            return chatTypingState.equals(getPlayerChatState.invoke(status));
        } catch (Throwable t) {
            return false;
        }
    }
}
