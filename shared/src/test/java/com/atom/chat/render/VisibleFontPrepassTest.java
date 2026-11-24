package com.atom.chat.render;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.page.MessageListView;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.ScrollController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VisibleFontPrepassTest {
 @BeforeAll static void setup(@TempDir Path root) {
  Platform.install(new Platform.Provider(){public Path configDir(){return root;}public Path gameDir(){return root;}public boolean isModLoaded(String id){return false;}});
 }
 @Test void hiddenHistoryIsNotRequestedAndScrollingOrReloadChangesTheVisibleSet() {
  var requested=new HashSet<ChatMessage>();
  var view=new MessageListView(new MessageListView.Host(){
   public UUID ownUuid(){return null;}public String ownName(){return "u";}public long openStart(){return 0;}
   public String senderName(ChatMessage m){requested.add(m);return "u";}
  });
  var messages=new ArrayList<ChatMessage>();
  for(int i=0;i<500;i++)messages.add(new ChatMessage(com.atom.chat.text.RichText.literal("row "+i).toText(),false,false));
  var frozen=List.copyOf(messages);var scroll=new ScrollController();
  try {
   NativeFontRenderer.beginFrame();view.requestFonts(320,140,frozen,scroll);
   assertTrue(requested.contains(messages.get(499)));assertFalse(requested.contains(messages.get(0)));
   requested.clear();scroll.scrollTo(0,false);NativeFontRenderer.invalidate();NativeFontRenderer.beginFrame();
   view.requestFonts(320,140,frozen,scroll);
   assertTrue(requested.contains(messages.get(0)));assertFalse(requested.contains(messages.get(499)));
  } finally {view.dispose();NativeFontRenderer.endFrame();}
 }
 @Test void aDeletionRevisionAndDisposalReleaseTheUnpaintedPreparedRows() throws Exception {
  var message=new ChatMessage(com.atom.chat.text.RichText.literal("old body").toText(),false,false);
  String signature=com.atom.chat.chat.NativeMessageMeta.signature(new byte[256]);
  message.withNativeMeta(new com.atom.chat.chat.NativeMessageMeta(signature,true,null,false,0,com.atom.chat.chat.NativeMessageSync.session()));
  var store=com.atom.chat.chat.ChatStore.get();store.add(message);
  var view=new MessageListView(new MessageListView.Host(){public UUID ownUuid(){return null;}public String ownName(){return "u";}public String senderName(ChatMessage m){return "u";}public long openStart(){return 0;}});
  try {
   view.requestFonts(320,140,List.of(message),new ScrollController());
   assertEquals(1,com.atom.chat.chat.NativeMessageSync.applied(signature,com.atom.chat.text.RichText.literal("deleted")));
   var replacement=store.rawSnapshot().stream().filter(m->m.getId()==message.getId()).findFirst().orElseThrow();
   assertTrue(replacement.isDeleted());assertNotSame(message,replacement);
   view.redactNativeMessages();assertReleased(view);
   view.requestFonts(320,140,List.of(replacement),new ScrollController());view.dispose();assertReleased(view);
  } finally {view.dispose();store.reset();}
 }
 private static void assertReleased(MessageListView view) throws Exception {
  for(String name:List.of("preparedMessages","preparedScroll")) {var f=MessageListView.class.getDeclaredField(name);f.setAccessible(true);assertNull(f.get(view),name);}
 }

}
