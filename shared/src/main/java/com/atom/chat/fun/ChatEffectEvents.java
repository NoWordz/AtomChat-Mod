package com.atom.chat.fun;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.PlayerRef;
import java.util.function.BiConsumer;
/** New-row events carry their conversation; null partner denotes the public feed. */
public final class ChatEffectEvents {
    private static BiConsumer<ChatMessage,PlayerRef> listener;
    private ChatEffectEvents() {}
    public static void install(BiConsumer<ChatMessage,PlayerRef> next) { listener=next; }
    public static void uninstall(BiConsumer<ChatMessage,PlayerRef> expected) { if(listener==expected) listener=null; }
    public static void publish(ChatMessage message) { publish(message,null); }
    public static void publish(ChatMessage message,PlayerRef partner) {
        if(message!=null && !message.isSystem() && listener!=null) listener.accept(message,partner);
    }
}
