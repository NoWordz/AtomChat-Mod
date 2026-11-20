package com.atom.chat.fun;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import java.util.function.BiConsumer;
/** New-row events carry their conversation; null partner denotes the public feed. */
public final class ChatEffectEvents {
    private static BiConsumer<ChatMessage,PlayerRef> listener;
    private ChatEffectEvents() {}
    public static void install(BiConsumer<ChatMessage,PlayerRef> next) { listener=next; }
    public static void uninstall(BiConsumer<ChatMessage,PlayerRef> expected) { if(listener==expected) listener=null; }
    public static void publish(ChatMessage message) { publish(message,null); }
    public static void publish(ChatMessage message,PlayerRef partner) {
        if (message == null || message.isSystem()) return;
        if (Platform.isInstalled() && !message.isIdentityUnknown()) {
            var config = AtomChatConfig.get();
            if (NewYearFireworks.enabled(config)) {
                NewYearFireworks.INSTANCE.onMessage(unlinkedBody(message), true, ChatEffectPresentation.nowMs());
            }
        }
        if (listener != null) listener.accept(message, partner);
    }
    private static String unlinkedBody(ChatMessage message) {
        String body = message.getContentText();
        String matchable = NewYearFireworks.matchableGreeting(body);
        if (matchable == null) return null;
        var rich = message.getContentRich();
        StringBuilder filtered = null;
        int offset = 0;
        for (var run : rich.runs()) {
            int end = offset + run.text().length();
            if (run.style().getClickEvent() != null) {
                if (filtered == null) {
                    if (!rich.getString().equals(body)) return null;
                    filtered = new StringBuilder(matchable);
                }
                for (int i = offset; i < end; i++) filtered.setCharAt(i, '\uFFFC');
            }
            offset = end;
        }
        return filtered == null ? matchable : filtered.toString();
    }
}
