package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import io.github.humbleui.skija.Font;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkiaFontWidthIdentityTest {
    @Test void ordinaryThenWiderFontUsesIndependentNativeAdvances() { verify(false); }
    @Test void widerThenOrdinaryFontUsesIndependentNativeAdvances() { verify(true); }

    @Test void closedFontKeysNeverParticipateInLaterNativeValueEquality() throws Exception {
        var view=new com.atom.chat.page.MessageListView(new com.atom.chat.page.MessageListView.Host(){
            public java.util.UUID ownUuid(){return null;}public String ownName(){return "Tester";}public String senderName(com.atom.chat.chat.ChatMessage message){return "Tester";}public long openStart(){return 0;}
        });
        var message=new com.atom.chat.chat.ChatMessage(com.atom.chat.text.RichText.literal("font lifetime").toText(),false,true);
        var wrap=com.atom.chat.page.MessageListView.class.getDeclaredMethod("wrappedLines",com.atom.chat.chat.ChatMessage.class,Font.class,float.class);wrap.setAccessible(true);
        try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(180,40)) {
        for(int i=0;i<160;i++) {
            try(Font font=new Font(FontManager.getDefaultTypeface(),23.75F)) {
                String text="font lifetime "+i;
                assertEquals(font.measureTextWidth(text),RichTextRenderer.measurer(font).measure(text),.001F);
                ObfuscatedText.draw(surface.getCanvas(),font,"AAAA",2,28,-1,i);
                assertFalse(((java.util.List<?>)wrap.invoke(view,message,font,180F)).isEmpty());
                // The caller owns this short-lived font. Clear the existing global blob cache before closing it;
                // retain the chat measurer, obfuscation pool and view caches so their closed-key regression stays exercised.
                SkiaFontRenderer.clearTextShapeCache();
            }
        }
        } finally {view.dispose();}
    }

    private static void verify(boolean reverse) {
        String text = "MMMM World native width " + reverse;
        try (Font ordinary = new Font(FontManager.getDefaultTypeface(), 23.75F);
             Font wider = new Font(FontManager.getDefaultTypeface(), 23.75F).setScaleX(1.5F)) {
            Font first = reverse ? wider : ordinary, second = reverse ? ordinary : wider;
            assertNotEquals(first.measureTextWidth(text), second.measureTextWidth(text));
            assertEquals(first.measureTextWidth(text), SkiaFontRenderer.getStringWidth(first, text), .001F);
            assertEquals(second.measureTextWidth(text), SkiaFontRenderer.getStringWidth(second, text), .001F,
                    "plain labels must measure with the font that will paint them");
            assertEquals(first.measureTextWidth(text), RichTextRenderer.measurer(first).measure(text), .001F);
            assertEquals(second.measureTextWidth(text), RichTextRenderer.measurer(second).measure(text), .001F,
                    "a same-size font must not reuse another face's measured width");
        } finally { SkiaFontRenderer.clearTextShapeCache(); }
    }
}
