package com.atom.chat.fun;
import com.atom.chat.chat.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;
class ChatEffectEventsTest {
 @org.junit.jupiter.api.BeforeAll static void installTestPlatform() {
  com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider() {
   public java.nio.file.Path configDir() { return java.nio.file.Path.of("build/effect-test-runtime/config"); }
   public java.nio.file.Path gameDir() { return java.nio.file.Path.of("build/effect-test-runtime/game"); }
   public boolean isModLoaded(String id) { return false; }
  });
 }

 private ChatMessage message(boolean system) {
  return new ChatMessage(Component.literal("生日快乐！"),false,system,null,null,UUID.randomUUID(),"Alice","Alice","生日快乐！");
 }
 @Test void newRowsPublishHistoryAndSystemDoNot() {
  var n=new AtomicInteger(); BiConsumer<ChatMessage,PlayerRef> listener=(m,p)->n.incrementAndGet();
  ChatEffectEvents.install(listener);
  try {
   ChatStore.reset(); PrivateChatStore.reset();
   ChatStore.get().addLoaded(List.of(message(false))); assertEquals(0,n.get());
   ChatStore.get().add(message(true)); assertEquals(0,n.get());
   ChatStore.get().add(message(false)); assertEquals(1,n.get());
   var partner=PlayerRef.of(UUID.randomUUID(),"Alice");
   PrivateChatStore.addLoaded(partner,List.of(message(false))); assertEquals(1,n.get());
   PrivateChatStore.addIncoming(partner,message(false)); assertEquals(2,n.get());
  } finally { ChatEffectEvents.uninstall(listener); ChatStore.reset(); PrivateChatStore.reset(); }
 }
 @Test void staleTeardownCannotRemoveCurrentSubscriber() {
  var n=new AtomicInteger(); BiConsumer<ChatMessage,PlayerRef> old=(m,p)->{}; BiConsumer<ChatMessage,PlayerRef> current=(m,p)->n.incrementAndGet();
  ChatEffectEvents.install(old); ChatEffectEvents.install(current); ChatEffectEvents.uninstall(old);
  try { ChatEffectEvents.publish(message(false)); assertEquals(1,n.get()); }
  finally { ChatEffectEvents.uninstall(current); }
  ChatEffectEvents.publish(message(false)); assertEquals(1,n.get());
 }

 @Test void backgroundPrivateDoesNotTriggerPublicPresentation() {
  var cfg=com.atom.chat.config.AtomChatConfig.get();
  boolean fun=cfg.funEffectsEnabled, motion=cfg.animationEnabled;
  cfg.funEffectsEnabled=true;cfg.animationEnabled=true;
  var presentation=new ChatEffectPresentation((m,p)->p==null);
  presentation.install();
  try {
   var partner=PlayerRef.of(UUID.randomUUID(),"Background");
   PrivateChatStore.addIncoming(partner,message(false));
   assertFalse(presentation.session().active(ChatEffectPresentation.nowMs()),"background conversation triggered public panel");
  } finally { presentation.close();PrivateChatStore.reset();cfg.funEffectsEnabled=fun;cfg.animationEnabled=motion; }
 }

 @Test void partnerAndPageChangesAreIsolatedAndClearOldBurst() {
  var cfg=com.atom.chat.config.AtomChatConfig.get();
  boolean fun=cfg.funEffectsEnabled,motion=cfg.animationEnabled;
  cfg.funEffectsEnabled=true;cfg.animationEnabled=true;
  var shown=new java.util.concurrent.atomic.AtomicReference<PlayerRef>();
  var presentation=new ChatEffectPresentation((m,p)->java.util.Objects.equals(p,shown.get()));
  presentation.install();
  try {
   ChatEffectEvents.publish(message(false)); assertTrue(presentation.session().active(ChatEffectPresentation.nowMs()));
   var a=PlayerRef.of(UUID.randomUUID(),"A"); var b=PlayerRef.of(UUID.randomUUID(),"B");
   shown.set(a);assertFalse(presentation.session().active(ChatEffectPresentation.nowMs()));
   ChatEffectEvents.publish(message(false),b);assertFalse(presentation.session().active(ChatEffectPresentation.nowMs()));
   ChatEffectEvents.publish(message(false),a);assertTrue(presentation.session().active(ChatEffectPresentation.nowMs()));
   cfg.funEffectsEnabled=false;assertFalse(presentation.session().active(ChatEffectPresentation.nowMs()));
  } finally { presentation.close();cfg.funEffectsEnabled=fun;cfg.animationEnabled=motion; }
 }
}
