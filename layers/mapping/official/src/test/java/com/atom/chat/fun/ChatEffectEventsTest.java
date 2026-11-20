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

 @Test void fireworksListenToNewRowsWithoutAnOpenScreen() {
  var cfg = com.atom.chat.config.AtomChatConfig.get();
  boolean experimental = cfg.experimentalEffectsEnabled, animation = cfg.animationEnabled, enabled = cfg.enabled;
  cfg.experimentalEffectsEnabled = cfg.animationEnabled = cfg.enabled = true;
  var fireworks = NewYearFireworks.INSTANCE;
  fireworks.reset();
  var partner = PlayerRef.of(UUID.randomUUID(), "Alice");
  var body = Component.literal("新年快樂！");
  var received = new ChatMessage(body, false, false, null, null, UUID.randomUUID(), "Alice", "Alice", "新年快樂！");
  var sent = new ChatMessage(body, true);
  try {
   ChatEffectEvents.publish(new ChatMessage(body, false, true));
   var hiddenLink = Component.literal("Happy New Year").withStyle(style -> style.withClickEvent(
           new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, "https://example.com")));
   ChatEffectEvents.publish(new ChatMessage(hiddenLink, false));
   var linkedPrefix = Component.literal("https://example.com/").withStyle(style -> style.withClickEvent(
           new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, "https://example.com")));
   var splitLink = Component.empty().append(linkedPrefix).append(Component.literal("新年快乐"));
   ChatEffectEvents.publish(new ChatMessage(splitLink, false));
   var quoteMarker = Component.literal("> ").withStyle(style -> style.withClickEvent(
           linkedPrefix.getStyle().getClickEvent()));
   var linkedQuote = Component.empty().append(quoteMarker).append(Component.literal("Happy New Year"));
   ChatEffectEvents.publish(new ChatMessage(linkedQuote, false));
   ChatEffectEvents.publish(new ChatMessage(body, false, false, null, null, null, "Unknown", "", "新年快樂！"));
   ChatStore.get().addLoaded(java.util.List.of(received));
   assertFalse(fireworks.active(ChatEffectPresentation.nowMs()));
   PrivateChatStore.addIncoming(partner, received);
   assertTrue(fireworks.active(ChatEffectPresentation.nowMs()));
   fireworks.reset();
   ChatStore.get().add(sent);
   assertTrue(fireworks.active(ChatEffectPresentation.nowMs()));
  } finally {
   fireworks.reset();
   ChatStore.reset();
   PrivateChatStore.reset();
   cfg.experimentalEffectsEnabled = experimental;
   cfg.animationEnabled = animation;
   cfg.enabled = enabled;
  }
 }
}
