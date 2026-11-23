package com.atom.chat.api.card;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Server- &amp; client-safe extension registry for card callbacks and chat-to-card adapters.
 *
 * <p>Zero Skija or client imports so dedicated servers can register or inspect adapters safely.
 * For custom Skija rendering on the client, see {@link CardRenderRegistry}.
 */
public final class CardRegistry {

    /** Context passed to {@link ShareCard.ActionKind#CLIENT_CALLBACK} listeners. */
    public record ActionCallbackContext(ShareCard card, CardState state, ShareCard.CardAction action) {
    }

    /**
     * Cross-loader signal adapter that receives normalized message text, translation keys,
     * click actions and hover flags to produce a {@link ShareCard}. Higher {@link #priority()} runs first.
     */
    @FunctionalInterface
    public interface SignalCardAdapter {
        ShareCard adapt(UniversalCardMatcher.MessageSignals signals);

        default int priority() {
            return 0;
        }
    }

    /**
     * Optional plain-text adapter that turns an incoming chat line into a {@link ShareCard}.
     * Mapping-aware mods can inspect raw text or attach platform adapters separately.
     */
    @FunctionalInterface
    public interface TextCardAdapter {
        ShareCard adapt(String senderName, String rawText);
    }

    private static final Map<String, Consumer<ActionCallbackContext>> CALLBACKS = new ConcurrentHashMap<>();
    private static final List<SignalCardAdapter> SIGNAL_ADAPTERS = new CopyOnWriteArrayList<>();
    private static final List<TextCardAdapter> TEXT_ADAPTERS = new CopyOnWriteArrayList<>();

    private CardRegistry() {
    }

    public static void registerCallback(String actionId, Consumer<ActionCallbackContext> handler) {
        if (actionId != null && !actionId.isBlank() && handler != null) {
            CALLBACKS.put(actionId, handler);
        }
    }

    public static boolean dispatchCallback(ActionCallbackContext ctx) {
        if (ctx == null || ctx.action() == null) return false;
        Consumer<ActionCallbackContext> exact = CALLBACKS.get(ctx.action().id());
        if (exact != null) {
            exact.accept(ctx);
            return true;
        }
        Consumer<ActionCallbackContext> byValue = CALLBACKS.get(ctx.action().value());
        if (byValue != null) {
            byValue.accept(ctx);
            return true;
        }
        return false;
    }

    public static void registerSignalAdapter(SignalCardAdapter adapter) {
        if (adapter == null) return;
        synchronized (SIGNAL_ADAPTERS) {
            SIGNAL_ADAPTERS.add(adapter);
            SIGNAL_ADAPTERS.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        }
    }

    public static void unregisterSignalAdapter(SignalCardAdapter adapter) {
        if (adapter != null) {
            SIGNAL_ADAPTERS.remove(adapter);
        }
    }

    public static ShareCard adaptSignals(UniversalCardMatcher.MessageSignals signals) {
        if (signals == null) return null;
        for (SignalCardAdapter adapter : SIGNAL_ADAPTERS) {
            try {
                ShareCard card = adapter.adapt(signals);
                if (card != null) return card;
            } catch (RuntimeException ignored) {
                // Keep chat capture resilient to third-party adapter faults.
            }
        }
        return null;
    }

    public static void registerTextAdapter(TextCardAdapter adapter) {
        if (adapter != null) {
            TEXT_ADAPTERS.add(adapter);
        }
    }

    public static ShareCard adaptText(String senderName, String rawText) {
        for (TextCardAdapter adapter : TEXT_ADAPTERS) {
            try {
                ShareCard card = adapter.adapt(senderName, rawText);
                if (card != null) return card;
            } catch (RuntimeException ignored) {
                // Keep chat capture resilient to third-party adapter faults.
            }
        }
        return null;
    }
}
