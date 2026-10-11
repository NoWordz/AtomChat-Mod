package com.atom.chat.diagnostics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AtomChatLogTest {
 @Test void prefixesLinesAndKeepsPlaceholders() {
  assertEquals("[AtomChat][notify] play {}",AtomChatLog.message("notify","[notify] play {}"));
  assertEquals("[AtomChat][render] a\n[AtomChat][render] b",AtomChatLog.message("render","a\nb"));
  assertEquals("[AtomChat][notify] play {}",AtomChatLog.message("notify","[AtomChat][notify] play {}"));
 }

 @Test void julSupplierRemainsLazy() {
  var calls=new java.util.concurrent.atomic.AtomicInteger();
  var template=AtomChatLog.message("capture",()->{ calls.incrementAndGet();return "old"; });
  assertEquals(0,calls.get());assertEquals("[AtomChat][capture] old",template.get());assertEquals(1,calls.get());
 }

 @Test void repeatedRealPrefixIsNormalized() {
  assertEquals("[AtomChat][notify] bad",AtomChatLog.message("notify","[AtomChat][notify] [AtomChat][notify] bad"));
 }
}

