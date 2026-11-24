package net.minecraft.client.gui.screen;
import com.atom.chat.chat.*;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.*;
import com.atom.chat.image.ImagePreviewOverlay;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeScreenRedactionTest {
    @BeforeAll static void setup(@TempDir Path path){
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider(){public Path configDir(){return path;}public Path gameDir(){return path;}public boolean isModLoaded(String id){return false;}});
    }
    @BeforeEach void reset(){ChatStore.reset();PrivateChatStore.reset();NativeMessageSync.start();}
    @AfterEach void cleanup(){ChatStore.reset();PrivateChatStore.reset();NativeMessageSync.start();}
    private static void put(Object object,String name,Object value)throws Exception{var field=AtomChatScreen.class.getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static Object get(Object object,String name)throws Exception{var field=AtomChatScreen.class.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    @Test void actualScreenDropsDeletedMenuImageAndReactionAndRebasesArmedReply()throws Exception{assertState(true);}
    @Test void anUnsignedLiteralWithTheSameTextKeepsItsOwnTransientState()throws Exception{assertState(false);}
    private static void assertState(boolean signedTarget)throws Exception {
        byte[] bytes=new byte[256];Arrays.fill(bytes,(byte)6);String signature=NativeMessageMeta.signature(bytes);
        var rich=RichText.literal("secret");var original=new ChatMessage(rich.toText(),false).withNativeMeta(new NativeMessageMeta(signature,true,null,false,0,NativeMessageSync.session()));
        var literal=new ChatMessage(rich.toText(),false);ChatStore.get().add(original);ChatStore.get().add(literal);
        ChatMessage target=signedTarget?original:literal;
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var screen=(AtomChatScreen)unsafe.allocateInstance(AtomChatScreen.class);
        var replies=new ReplySlotStore();replies.arm(target);put(screen,"replySlots",replies);
        var menu=new ContextMenuController<ChatMessage>(new ContextMenuController.Host(){
            public String label(String key){return key;}public boolean isPlayerOnline(PlayerRef player){return false;}public UiLayout.Rect panelRect(){return new UiLayout.Rect(0,0,400,600);}public float frameDt(){return 0;}
            public void drawIcon(Canvas canvas,io.github.humbleui.skija.Path icon,float x,float y,float size,int color){}
        });
        menu.openBubble(ContextMenuController.Subject.of(null,false,target),20,30);put(screen,"contextMenu",menu);put(screen,"reactionTarget",target);
        var preview=new ImagePreviewOverlay();put(screen,"imagePreview",preview);put(screen,"imagePreviewMessage",target);put(screen,"nativeMessageRevision",NativeMessageSync.revision());
        try(var surface=Surface.makeRasterN32Premul(4,4);var image=surface.makeImageSnapshot()) {
            preview.open(image);assertTrue(preview.isActive());
            var redactor=AtomChatScreen.class.getDeclaredMethod("redactNativeState",String.class);redactor.setAccessible(true);
            java.util.function.Consumer<String> listener=key->{try{redactor.invoke(screen,key);}catch(Exception e){throw new RuntimeException(e);}};
            NativeMessageSync.listen(listener);
            try {
                NativeMessageSync.applied(signature,NativeMessageBridge.deletedMarker());
                for(int i=0;i<500;i++)ChatStore.get().add(new ChatMessage(RichText.literal("flood-"+i).toText(),false));
                assertNull(NativeMessageSync.knownDeleted(signature),"the original tombstone has left the raw500 window");
            }finally{NativeMessageSync.unlisten(listener);}
            if(signedTarget){assertNull(menu.currentRow());assertFalse(preview.isActive());assertNull(get(screen,"reactionTarget"));assertTrue(replies.replyTarget().isDeleted());}
            else{assertSame(literal,menu.currentRow());assertTrue(preview.isActive());assertSame(literal,get(screen,"reactionTarget"));assertSame(literal,replies.replyTarget());}
            preview.discard();
        }
    }
}
