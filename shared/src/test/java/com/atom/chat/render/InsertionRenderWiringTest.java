package com.atom.chat.render;
import com.atom.chat.font.FontManager;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.MessageListWiring;
import com.atom.chat.chat.*;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InsertionRenderWiringTest {
    @org.junit.jupiter.api.BeforeAll static void setup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider() {
            public java.nio.file.Path configDir(){return dir;}
            public java.nio.file.Path gameDir(){return dir;}
            public boolean isModLoaded(String id){return false;}
        });
    }
    @SuppressWarnings("unchecked") private static MessageListWiring.Host<ClickableSpan> actualScreenHost() throws Exception {
        Class<?> screen;
        try{screen=Class.forName("com.atom.chat.screen.AtomChatScreen");}
        catch(ClassNotFoundException fabric){screen=Class.forName("net.minecraft.client.gui.screen.AtomChatScreen");}
        var inner=new ArrayList<String>();
        try(var bytes=screen.getResourceAsStream("/"+screen.getName().replace('.','/')+".class")){
            new org.objectweb.asm.ClassReader(bytes).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9){
                @Override public void visitInnerClass(String name,String outer,String shortName,int access){inner.add(name.replace('/','.'));}
            },org.objectweb.asm.ClassReader.SKIP_CODE);
        }
        for(String name:inner){
            Class<?> candidate=Class.forName(name);
            if(!MessageListWiring.Host.class.isAssignableFrom(candidate))continue;
            for(var ctor:candidate.getDeclaredConstructors())if(ctor.getParameterCount()==1 && ctor.getParameterTypes()[0]==screen){
                ctor.setAccessible(true);return (MessageListWiring.Host<ClickableSpan>)ctor.newInstance(new Object[]{null});
            }
        }
        throw new AssertionError("No actual screen message wiring host");
    }
    @Test void insertionOnlySpanSurvivesRealPaintAndExistingClickDragWiring() throws Exception {
        var component = RichText.literal("insert me").toText();
        var rich = RichText.of(component.copy().setStyle(component.getStyle().withInsertion("wire payload")));
        var font = FontManager.font(14);
        var spans = new ArrayList<ClickableSpan>();
        try (var surface = Surface.makeRasterN32Premul(220, 50)) {
            RichTextRenderer.drawLines(surface.getCanvas(), font, RichTextRenderer.wrapFor(rich,font,200),
                    5,25,20,-1,spans,true);
        }
        assertEquals(1,spans.size());
        var span=spans.get(0);
        assertNull(span.style().getClickEvent());
        assertFalse(span.style().isUnderlined());
        assertEquals("wire payload",span.style().getInsertion());
        var actual=actualScreenHost();
        var wiring=new MessageListWiring<ClickableSpan>(new MessageListWiring.Host<>() {
            public UUID ownUuid(){return null;}
            public String ownName(){return "Local";}
            public String messageSenderName(ChatMessage m){return "Sender";}
            public long openStart(){return 0;}
            public List<ChatMessage> worldSnapshot(){return List.of();}
            public List<ChatMessage> privateMessages(PlayerRef p){return List.of();}
            public WorldFeedFilter worldFeed(){return new WorldFeedFilter();}
            public ClickableSpan clickableSpanAt(float x,float y){return span;}
            public boolean hasClickEvent(ClickableSpan s){return actual.hasClickEvent(s);}
        });
        wiring.armClickAt(10,25);
        assertTrue(wiring.shouldFireClick(wiring.takePending(),10,25));
        wiring.armClickAt(10,25);wiring.markMoved();
        assertFalse(wiring.shouldFireClick(wiring.takePending(),10,25));
    }
}
