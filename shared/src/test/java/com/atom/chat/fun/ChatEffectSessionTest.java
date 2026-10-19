package com.atom.chat.fun;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatEffectSessionTest {
 @Test void lifetimeBusyAndClear() {
  var s=new ChatEffectSession();
  assertFalse(s.active(0)); assertTrue(s.trigger(ChatEffectType.HEART,100));
  assertFalse(s.trigger(ChatEffectType.CAKE,200)); assertEquals(ChatEffectType.HEART,s.type());
  assertTrue(s.active(3099)); assertFalse(s.active(3100));
  assertTrue(s.trigger(ChatEffectType.CAKE,3100)); s.clear(); assertFalse(s.active(3101));
  assertTrue(s.trigger(ChatEffectType.FIREWORK,3101));
 }
 @Test void particleBoundsAndDeterminism() {
  for(var t:ChatEffectType.values()) {
   var a=ChatEffectParticles.frame(t,0.5f,17); var b=ChatEffectParticles.frame(t,0.5f,17);
   assertEquals(a,b); assertTrue(a.size()<=32); assertFalse(a.isEmpty());
   for(var p:a) { assertTrue(Float.isFinite(p.x())); assertTrue(Float.isFinite(p.y())); assertTrue(p.alpha()>=0&&p.alpha()<=1); }
  }
 }
 @Test void goodnightStarsStayInsideTheContentAreaAndStayCheap() {
  var particles=ChatEffectParticles.frame(ChatEffectType.STAR,0.5f,17);
  assertTrue(particles.size()<=12);
  assertEquals(particles,ChatEffectParticles.frame(ChatEffectType.STAR,0.5f,17));
  for(var p:particles) {
   assertTrue(p.x()>=0&&p.x()<=1); assertTrue(p.y()>=0&&p.y()<=1);
   assertTrue(p.alpha()>=0&&p.alpha()<=.36f);
  }
 }
}

