package com.atom.chat.fun;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
class ChatEffectMatcherTest {
 @Test void aliases() {
  var m = new ChatEffectMatcher();
  for (String s : new String[]{"我喜欢你！","我爱你❤️","I like you","I LIKE U!","I love you","I love u","love you","love u"}) assertEquals(Optional.of(ChatEffectType.HEART), m.match(s), s);
  for (String s : new String[]{"祝你生日快乐！","happy birthday","Happy  Bday!","HBD"}) assertEquals(Optional.of(ChatEffectType.CAKE), m.match(s), s);
  for (String s : new String[]{"新年快乐！","happy new year","HNY"}) assertEquals(Optional.of(ChatEffectType.FIREWORK), m.match(s), s);
 }
 @Test void boundariesAndExcludedTokens() {
  var m = new ChatEffectMatcher();
  for (String s : new String[]{"我爱你们","生日快乐呀","hnything","hny.com","xxHBD","https://example.com/hny","`hbd`","/say HNY","[[CICode,url=hbd]]","「引用 @Bob: 生日快乐」hello"}) assertTrue(m.match(s).isEmpty(), s);
  assertEquals(Optional.of(ChatEffectType.CAKE),m.match("「引用 @Bob: 我爱你」生日快乐！"));
  assertEquals(Optional.of(ChatEffectType.CAKE),m.match("hbd then I love u"));
 }
 @Test void goodnightAcceptsNaturalPrefixesButKeepsWordBoundaries() {
  var m = new ChatEffectMatcher();
  for (String s : new String[]{"晚安！","祝你晚安","Good night","GOOD  NIGHT!","goodnight"}) {
   assertEquals(Optional.of(ChatEffectType.STAR), m.match(s), s);
  }
  for (String s : new String[]{"晚安模式","good nightmare","/say good night",
      "`good night`","https://example.com/晚安","「引用 @Bob: 晚安」"}) {
   assertTrue(m.match(s).isEmpty(), s);
  }
 }
}

