package com.atom.chat.card;

import com.atom.chat.api.card.ShareCard;
import com.atom.chat.api.card.UniversalCardMatcher;
import net.minecraft.text.Text;

/**
 * Mapping-aware compatibility interface for third-party Fabric mods to convert
 * rich Minecraft {@link Text} chat messages directly into {@link ShareCard}s.
 *
 * <p>Register via {@link BuiltInCardAdapter#registerComponentAdapter(ComponentCardAdapter)}.
 * Higher {@link #priority()} adapters run first.
 */
@FunctionalInterface
public interface ComponentCardAdapter {

    /**
     * Inspects an incoming chat {@link Text} and its extracted {@link UniversalCardMatcher.MessageSignals},
     * returning a {@link ShareCard} to replace the text bubble or {@code null} to pass to the next adapter.
     */
    ShareCard adapt(Text component, UniversalCardMatcher.MessageSignals signals);

    /** Higher priority adapters run earlier (default {@code 0}). */
    default int priority() {
        return 0;
    }
}
