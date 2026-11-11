package com.atom.chat.render;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.page.ConversationListPage;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual blocked-row greyscale layer with a translucent card. */
class BlockedCardShadowTest {
    @TempDir Path tmp;

    @Test void greyscalingABlockedRowPreservesItsSoftShadowOutsideTheCard() throws Exception {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return tmp; }
            public Path gameDir() { return tmp; }
            public boolean isModLoaded(String id) { return false; }
        });
        var cfg=AtomChatConfig.get();float oldTint=cfg.cardTint;int oldColor=cfg.cardColor;
        try(var normal=Surface.makeRasterN32Premul(700,260);var blocked=Surface.makeRasterN32Premul(700,260);
            var n=new Bitmap();var b=new Bitmap()) {
            cfg.cardColor=0xFF22252A;cfg.cardTint=.5F;
            var message=new ChatMessage(Component.literal("shadow regression"),false,false,null,null,null,"Alice","Alice","shadow regression");
            var row=new ConversationListPage.Row(ConversationListPage.RowKind.PLAYER,null,message,0,false,true);
            var page=new ConversationListPage(null);
            var plain=ConversationListPage.class.getDeclaredMethod("drawNormalRow",Canvas.class,ConversationListPage.Row.class,float.class,float.class,float.class,float.class);
            var grey=ConversationListPage.class.getDeclaredMethod("drawBlockedRow",Canvas.class,ConversationListPage.Row.class,float.class,float.class,float.class,float.class);
            plain.setAccessible(true);grey.setAccessible(true);
            normal.getCanvas().clear(0);blocked.getCanvas().clear(0);
            plain.invoke(page,normal.getCanvas(),row,100F,80F,500F,0F);
            grey.invoke(page,blocked.getCanvas(),row,100F,80F,500F,0F);
            n.allocN32Pixels(700,260);b.allocN32Pixels(700,260);
            assertTrue(normal.readPixels(n,0,0));assertTrue(blocked.readPixels(b,0,0));
            SoftShadowTest.save(normal,"blocked-card-normal.png");SoftShadowTest.save(blocked,"blocked-card-grey.png");
            int tail=n.getColor(350,170)>>>24;
            int greyTail=b.getColor(350,170)>>>24;
            System.out.printf("blocked card 50%% fill: unfiltered-tail=%d filtered-tail=%d%n",tail,greyTail);
            assertTrue(tail>0,"fixture must observe the production shadow below the card");
            assertEquals(tail,greyTail,"greyscale changes colour, never the shadow alpha or its bounds");
            assertTrue((b.getColor(400,120)>>>24)<255,"regression must exercise a translucent card");
        } finally { cfg.cardTint=oldTint;cfg.cardColor=oldColor; }
    }
}
