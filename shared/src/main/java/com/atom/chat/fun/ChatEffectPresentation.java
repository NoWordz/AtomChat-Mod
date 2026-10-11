package com.atom.chat.fun;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import java.util.function.BiPredicate;
import java.util.function.BiConsumer;
/** Owns the open screen's subscription and burst state, independent of layout. */
public final class ChatEffectPresentation {
    private final ChatEffectSession session=new ChatEffectSession();
    private final ChatEffectMatcher matcher=new ChatEffectMatcher();
    private final BiPredicate<ChatMessage,PlayerRef> visible;
    private final BiConsumer<ChatMessage,PlayerRef> listener=this::onMessage;
    private ChatMessage burstMessage;
    private PlayerRef burstPartner;
    public ChatEffectPresentation(BiPredicate<ChatMessage,PlayerRef> visible) { this.visible=visible; }
    public static long nowMs() { return System.nanoTime()/1_000_000L; }
    private boolean enabled() {
        var cfg=AtomChatConfig.get();
        return cfg.funEffectsEnabled && cfg.animationEnabled;
    }
    public void install() { ChatEffectEvents.install(listener); }
    public void close() { ChatEffectEvents.uninstall(listener); clear(); }
    private void clear() { session.clear(); burstMessage=null; burstPartner=null; }
    public ChatEffectSession session() {
        if(!enabled() || (burstMessage!=null && !visible.test(burstMessage,burstPartner))) clear();
        return session;
    }
    private void onMessage(ChatMessage message,PlayerRef partner) {
        if(!enabled() || !visible.test(message,partner)) return;
        matcher.match(message.getContentText()).ifPresent(type -> {
            if(session().trigger(type,nowMs())) {
                burstMessage=message; burstPartner=partner;
                if(AtomChatConfig.get().debug)
                    com.atom.chat.AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("fun","trigger type={}"),type);
            }
        });
    }
}
