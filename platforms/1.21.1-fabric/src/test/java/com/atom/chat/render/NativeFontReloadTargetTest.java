package com.atom.chat.render;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeFontReloadTargetTest {
 @Test void publishAndInvalidationSelectOnlyTheCompletedApplyOverload() throws Exception {
  var mixin=new ClassNode();var target=new ClassNode();
  try(var in=getClass().getResourceAsStream("/com/atom/chat/mixin/NativeFontManagerMixin.class")){new ClassReader(in).accept(mixin,0);}
  try(var in=getClass().getResourceAsStream("/net/minecraft/client/font/FontManager.class")){new ClassReader(in).accept(target,0);}
  for(String handler:List.of("atomchat$publish","atomchat$invalidate")) {
   var method=mixin.methods.stream().filter(m->m.name.equals(handler)).findFirst().orElseThrow();
   var inject=method.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
   boolean checked=false;
   for(int i=0;i<inject.values.size();i+=2)if(inject.values.get(i).equals("method"))for(Object value:(List<?>)inject.values.get(i+1)) {
    String selector=value.toString();if(!selector.startsWith("reload"))continue;checked=true;
    var matches=target.methods.stream().filter(m->selector.equals(m.name)||selector.equals(m.name+m.desc)).toList();
    assertEquals(1,matches.size(),handler+" must select one reload overload");
    assertTrue(matches.get(0).desc.endsWith(")V"));assertEquals(2,Type.getArgumentTypes(matches.get(0).desc).length);
   }
   assertTrue(checked);
  }
 }
}
