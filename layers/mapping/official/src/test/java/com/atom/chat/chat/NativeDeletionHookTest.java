package com.atom.chat.chat;
import com.atom.chat.text.RichText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeDeletionHookTest {
    @BeforeAll static void setup(@TempDir Path path){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider(){public Path configDir(){return path;}public Path gameDir(){return path;}public boolean isModLoaded(String id){return false;}});
    }
    @BeforeEach void reset(){ChatStore.reset();PrivateChatStore.reset();NativeMessageSync.start();}
    @AfterEach void cleanup(){ChatStore.reset();NativeMessageSync.start();}
    private static Class<?> handlerType;
    private static Class<?> handlerType()throws Exception {
        if(handlerType!=null)return handlerType;
        if(!System.getProperty("atomchat.golden.target","").contains("neoforge"))
            return handlerType=Class.forName("com.atom.chat.mixin.ChatHudMixin");
        for(var method:ChatComponent.class.getDeclaredMethods())if(method.getName().equals("atomchat$afterNativeSet"))return handlerType=ChatComponent.class;
        // NeoForge's JUnit launcher can own the Mixin package without enabling its client environment.
        // Execute the compiled handler unchanged under a test-only name, and separately probe vanilla's real set.
        String source="com/atom/chat/mixin/ChatHudMixin",target="com/atom/chat/testing/NativeDeletionHandlerFixture";
        class FixtureLoader extends ClassLoader {
            FixtureLoader(){super(NativeDeletionHookTest.class.getClassLoader());}
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
                if(!name.replace('.','/').startsWith(target))return super.findClass(name);
                String resource=source+name.replace('.','/').substring(target.length());
                try(var input=NativeDeletionHookTest.class.getResourceAsStream("/"+resource+".class")) {
                    var writer=new org.objectweb.asm.ClassWriter(0);
                    var remapper=new org.objectweb.asm.commons.Remapper(){@Override public String map(String value){return value.equals(source)||value.startsWith(source+"$")?target+value.substring(source.length()):value;}};
                    new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.commons.ClassRemapper(writer,remapper),0);
                    var bytes=writer.toByteArray();return defineClass(name,bytes,0,bytes.length);
                }catch(java.io.IOException error){throw new ClassNotFoundException(name,error);}
            }
            Class<?> loadFixture()throws ClassNotFoundException{return findClass(target.replace('/','.'));}
        }
        System.out.println("Native deletion evidence: real vanilla method + compiled handler fixture; client Mixin transformer is inactive in this unit launcher");
        return handlerType=new FixtureLoader().loadFixture();
    }
    private static Object handler()throws Exception {
        var type=handlerType();
        return type==ChatComponent.class?type.getConstructor(Minecraft.class).newInstance(new Object[]{null}):type.getDeclaredConstructor().newInstance();
    }
    private static Object allocate(Class<?> type)throws Exception {var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);}
    private static void field(Object owner,Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static MessageSignature signature(){byte[] bytes=new byte[256];Arrays.fill(bytes,(byte)3);return new MessageSignature(bytes);}
    @Test void actualVanillaMethodKeepsFiftyNineTicksAndPerformsItsRealSetAtSixty()throws Exception {
        var client=(Minecraft)allocate(Minecraft.class);
        var gui=(Gui)allocate(Gui.class);
        field(client,Minecraft.class,"gui",gui);
        var hud=(ChatComponent)allocate(ChatComponent.class);
        field(hud,ChatComponent.class,"minecraft",client);
        var original=new GuiMessage(100,Component.literal("secret"),signature(),GuiMessageTag.chatNotSecure());
        var entries=new ArrayList<>(List.of(original));field(hud,ChatComponent.class,"allMessages",entries);
        field(hud,ChatComponent.class,"trimmedMessages",new ArrayList<>());
        var method=ChatComponent.class.getDeclaredMethod("deleteMessageOrDelay",MessageSignature.class);method.setAccessible(true);
        field(gui,Gui.class,"tickCount",159);
        assertNotNull(method.invoke(hud,signature()));assertSame(original,entries.get(0));
        field(gui,Gui.class,"tickCount",160);
        try{method.invoke(hud,signature());}catch(java.lang.reflect.InvocationTargetException refreshNeedsClient){
            assertInstanceOf(NullPointerException.class,refreshNeedsClient.getCause(),"only the post-set GUI refresh lacks a live window");
        }
        assertNotSame(original,entries.get(0));assertNull(entries.get(0).signature());assertFalse(entries.get(0).content().getString().contains("secret"));
        assertEquals(GuiMessageTag.system(),entries.get(0).tag());
        var target=new org.objectweb.asm.tree.ClassNode();
        try(var bytes=ChatComponent.class.getResourceAsStream("/"+ChatComponent.class.getName().replace('.','/')+".class")){new org.objectweb.asm.ClassReader(bytes).accept(target,0);}
        long sets=target.methods.stream().filter(m->m.name.equals("deleteMessageOrDelay")).flatMap(m->java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false))
                .filter(i->i instanceof org.objectweb.asm.tree.MethodInsnNode call && call.owner.equals("java/util/ListIterator") && call.name.equals("set")).count();
        assertEquals(1,sets,"the registered redirect targets the actual unique native replacement call");
    }
    @Test void actualRedirectHandlerSynchronizesOnlyAfterIteratorSetHasSucceeded()throws Exception {
        var rich=RichText.literal("secret");var message=new ChatMessage(rich.toText(),false).withNativeMeta(new NativeMessageMeta(NativeMessageMeta.signature(signature().bytes()),true,null,false,0,NativeMessageSync.session()));
        ChatStore.get().add(message);
        var original=new GuiMessage(0,rich.toText(),signature(),null);
        var marker=new GuiMessage(0,NativeMessageBridge.deletedMarker().toText(),null,GuiMessageTag.system());
        var hook=handlerType().getDeclaredMethod("atomchat$afterNativeSet",ListIterator.class,Object.class,MessageSignature.class);hook.setAccessible(true);
        var handler=handler();
        var blocked=Collections.unmodifiableList(List.of(original)).listIterator();blocked.next();
        var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->hook.invoke(handler,blocked,marker,signature()));
        assertInstanceOf(UnsupportedOperationException.class,error.getCause());assertFalse(ChatStore.get().rawSnapshot().get(0).isDeleted());
        var nativeEntries=new ArrayList<>(List.of(original));var iterator=nativeEntries.listIterator();iterator.next();
        hook.invoke(handler,iterator,marker,signature());assertSame(marker,nativeEntries.get(0));assertTrue(ChatStore.get().rawSnapshot().get(0).isDeleted());
    }
    @Test void delayedNativeDeletionStillCoordinatesWhenFloodEvictsItsEntryBeforeTheDeadline()throws Exception {
        var signature=signature();var rich=RichText.literal("secret");
        var original=new ChatMessage(rich.toText(),false).withNativeMeta(new NativeMessageMeta(NativeMessageMeta.signature(signature.bytes()),true,null,false,100,NativeMessageSync.session()));
        ChatStore.get().add(original);
        NativeMessageSync.request(original.nativeMeta().signature(),110,true);assertEquals(0,NativeMessageSync.pendingCount());
        // Vanilla's queued retry now finds no native entry; invoke the real RETURN handler on that path.
        var handler=handler();field(handler,handlerType(),"allMessages",new ArrayList<GuiMessage>());
        var hook=handlerType().getDeclaredMethod("atomchat$missingRetainedEntry",MessageSignature.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class);hook.setAccessible(true);
        hook.invoke(handler,signature,new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Object>("retry",false));
        assertEquals(1,NativeMessageSync.pendingCount());NativeMessageSync.tick(159,key->false);assertFalse(ChatStore.get().rawSnapshot().get(0).isDeleted());
        NativeMessageSync.tick(160,key->false);assertTrue(ChatStore.get().rawSnapshot().get(0).isDeleted());assertEquals(0,NativeMessageSync.pendingCount());
    }
}
