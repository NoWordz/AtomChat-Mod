package com.atom.chat.chat;
import com.atom.chat.text.RichText;
import com.atom.chat.mixin.ChatHudMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Text;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeDeletionHookTest {
    @BeforeAll static void setup(@TempDir Path path){
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider(){public Path configDir(){return path;}public Path gameDir(){return path;}public boolean isModLoaded(String id){return false;}});
    }
    @BeforeEach void reset(){ChatStore.reset();PrivateChatStore.reset();NativeMessageSync.start();}
    @AfterEach void cleanup(){ChatStore.reset();NativeMessageSync.start();}
    private static Object allocate(Class<?> type)throws Exception {var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);}
    private static void field(Object owner,Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static MessageSignatureData signature(){byte[] bytes=new byte[256];Arrays.fill(bytes,(byte)3);return new MessageSignatureData(bytes);}
    @Test void actualVanillaMethodKeepsFiftyNineTicksAndPerformsItsRealSetAtSixty()throws Exception {
        var client=(MinecraftClient)allocate(MinecraftClient.class);
        var gui=(InGameHud)allocate(InGameHud.class);
        field(client,MinecraftClient.class,"inGameHud",gui);
        var hud=(ChatHud)allocate(ChatHud.class);
        field(hud,ChatHud.class,"client",client);
        var original=new ChatHudLine(100,Text.literal("secret"),signature(),MessageIndicator.notSecure());
        var entries=new ArrayList<>(List.of(original));field(hud,ChatHud.class,"messages",entries);
        field(hud,ChatHud.class,"visibleMessages",new ArrayList<>());
        var method=ChatHud.class.getDeclaredMethod("queueForRemoval",MessageSignatureData.class);method.setAccessible(true);
        field(gui,InGameHud.class,"ticks",159);
        assertNotNull(method.invoke(hud,signature()));assertSame(original,entries.get(0));
        field(gui,InGameHud.class,"ticks",160);
        try{method.invoke(hud,signature());}catch(java.lang.reflect.InvocationTargetException refreshNeedsClient){
            assertInstanceOf(NullPointerException.class,refreshNeedsClient.getCause(),"only the post-set GUI refresh lacks a live window");
        }
        assertNotSame(original,entries.get(0));assertNull(entries.get(0).signature());assertFalse(entries.get(0).content().getString().contains("secret"));
        assertEquals(MessageIndicator.system(),entries.get(0).indicator());
        var target=new org.objectweb.asm.tree.ClassNode();
        try(var bytes=ChatHud.class.getResourceAsStream("/"+ChatHud.class.getName().replace('.','/')+".class")){new org.objectweb.asm.ClassReader(bytes).accept(target,0);}
        long sets=target.methods.stream().filter(m->m.name.equals("queueForRemoval")).flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                .filter(i->i instanceof org.objectweb.asm.tree.MethodInsnNode call && call.owner.equals("java/util/ListIterator") && call.name.equals("set")).count();
        assertEquals(1,sets,"the registered redirect targets the actual unique native replacement call");
    }
    @Test void actualRedirectHandlerSynchronizesOnlyAfterIteratorSetHasSucceeded()throws Exception {
        var rich=RichText.literal("secret");var message=new ChatMessage(rich.toText(),false).withNativeMeta(new NativeMessageMeta(NativeMessageMeta.signature(signature().data()),true,null,false,0,NativeMessageSync.session()));
        ChatStore.get().add(message);
        var original=new ChatHudLine(0,rich.toText(),signature(),null);
        var marker=new ChatHudLine(0,NativeMessageBridge.deletedMarker().toText(),null,MessageIndicator.system());
        var hook=ChatHudMixin.class.getDeclaredMethod("atomchat$afterNativeSet",ListIterator.class,Object.class,MessageSignatureData.class);hook.setAccessible(true);
        var handler=new ChatHudMixin();
        var blocked=Collections.unmodifiableList(List.of(original)).listIterator();blocked.next();
        var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->hook.invoke(handler,blocked,marker,signature()));
        assertInstanceOf(UnsupportedOperationException.class,error.getCause());assertFalse(ChatStore.get().rawSnapshot().get(0).isDeleted());
        var nativeEntries=new ArrayList<>(List.of(original));var iterator=nativeEntries.listIterator();iterator.next();
        hook.invoke(handler,iterator,marker,signature());assertSame(marker,nativeEntries.get(0));assertTrue(ChatStore.get().rawSnapshot().get(0).isDeleted());
    }
    @Test void delayedNativeDeletionStillCoordinatesWhenFloodEvictsItsEntryBeforeTheDeadline()throws Exception {
        var signature=signature();var rich=RichText.literal("secret");
        var original=new ChatMessage(rich.toText(),false).withNativeMeta(new NativeMessageMeta(NativeMessageMeta.signature(signature.data()),true,null,false,100,NativeMessageSync.session()));
        ChatStore.get().add(original);
        NativeMessageSync.request(original.nativeMeta().signature(),110,true);assertEquals(0,NativeMessageSync.pendingCount());
        // Vanilla's queued retry now finds no native entry; invoke the real RETURN handler on that path.
        var handler=new ChatHudMixin();field(handler,ChatHudMixin.class,"messages",new ArrayList<ChatHudLine>());
        var hook=ChatHudMixin.class.getDeclaredMethod("atomchat$missingRetainedEntry",MessageSignatureData.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class);hook.setAccessible(true);
        hook.invoke(handler,signature,new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Object>("retry",false));
        assertEquals(1,NativeMessageSync.pendingCount());NativeMessageSync.tick(159,key->false);assertFalse(ChatStore.get().rawSnapshot().get(0).isDeleted());
        NativeMessageSync.tick(160,key->false);assertTrue(ChatStore.get().rawSnapshot().get(0).isDeleted());assertEquals(0,NativeMessageSync.pendingCount());
    }
}
