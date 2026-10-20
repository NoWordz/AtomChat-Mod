package com.atom.chat.page;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.theme.AppearancePresetStore;
import com.atom.chat.ui.ScrollController;
import io.github.humbleui.skija.Surface;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MessageResponsiveLayoutTest {
    @TempDir Path directory;
    @BeforeEach void install() {
        Platform.install(new Platform.Provider() {
            public Path configDir(){return directory;}
            public Path gameDir(){return directory;}
            public boolean isModLoaded(String id){return false;}
        });
    }

    private MessageListView view(ChatMessage msg, io.github.humbleui.skija.Image image) {
        return new MessageListView(new MessageListView.Host() {
            public UUID ownUuid(){return new UUID(0,1);}
            public String ownName(){return "Self";}
            public String senderName(ChatMessage m){return "A long player name";}
            public long openStart(){return msg.getTimestamp()+1;}
        },(url,visible)->image);
    }

    @Test void narrowTextUsesTheAvailableLaneAndMeasuredLinesMatchItsHit() {
        var cfg=AtomChatConfig.get();var saved=AppearancePresetStore.Snapshot.capture(cfg);
        int clock=cfg.timestampIntervalMinutes;var avatarStyle=cfg.avatarStyle;
        try(Surface surface=Surface.makeRasterN32Premul(850,1800)) {
            cfg.timestampIntervalMinutes=0;
            for(var avatar:AtomChatConfig.AvatarStyle.values()) for(boolean own:new boolean[]{false,true}) {
                cfg.avatarStyle=avatar;
                var msg=new ChatMessage(Component.literal("窄面板中的长消息应充分利用可用宽度，避免没有必要的频繁换行。".repeat(3)),own);
                var view=view(msg,null);
                try {
                    var scroll=new ScrollController();
                    view.draw(surface.getCanvas(),10,10,280,1600,List.of(msg),scroll);
                    var hit=view.hits().get(0);
                    assertTrue(hit.bubbleWidth()>280-com.atom.chat.ui.UiTokens.s(106),"the drawn text bubble must exceed the old fixed-width limit: "+hit.bubbleWidth());
                    assertTrue(hit.bubbleX()>=10 && hit.bubbleX()+hit.bubbleWidth()<=290,"both directions stay inside the row");
                    var lines=view.textLinesForHit(hit);
                    float last=lines.get(lines.size()-1).y()+lines.get(lines.size()-1).height();
                    assertTrue(last<=hit.bubbleBottom()+1,"selection lines and drawn bubble share the wrap width");
                    assertTrue(lines.get(0).y()>=hit.bubbleY()-1);
                    var measure=MessageListView.class.getDeclaredMethod("messageBodyHeight",ChatMessage.class,float.class,boolean.class);
                    measure.setAccessible(true);
                    assertEquals((float)measure.invoke(view,msg,280F,false),hit.bottom()-hit.y(),.01F,"scroll measurement must match the drawn height");
                    view.draw(surface.getCanvas(),10,10,800,1600,List.of(msg),new ScrollController());
                    assertTrue(view.hits().get(0).bubbleWidth()<=640,"wide rows keep a readable line-width ceiling");
                } catch(ReflectiveOperationException e){throw new AssertionError(e);}
                finally{view.dispose();}
            }
        } finally{saved.applyTo(cfg);cfg.timestampIntervalMinutes=clock;cfg.avatarStyle=avatarStyle;}
    }

    @Test void imageAspectAndClicksUseTheSameResponsiveLane() {
        var cfg=AtomChatConfig.get();var saved=AppearancePresetStore.Snapshot.capture(cfg);
        boolean images=cfg.imageMessagesEnabled;int clock=cfg.timestampIntervalMinutes;
        try(var source=Surface.makeRasterN32Premul(600,200);var target=Surface.makeRasterN32Premul(400,800)) {
            source.getCanvas().clear(0xFF00FF00);
            try(var image=source.makeImageSnapshot()) {
                cfg.imageMessagesEnabled=true;cfg.timestampIntervalMinutes=0;
                for(boolean own:new boolean[]{false,true}) for(boolean loaded:new boolean[]{false,true}) {
                    var msg=new ChatMessage(Component.literal("[[CICode,url=atomchat-test://responsive,w=600,h=200]]"),own);
                    var view=view(msg,loaded?image:null);
                    try {
                        view.draw(target.getCanvas(),10,10,280,700,List.of(msg),new ScrollController());
                        var hit=view.hits().get(0);
                        assertTrue(hit.bubbleWidth()>185,"images must not retain the old extra width deduction");
                        assertEquals(3F,hit.bubbleWidth()/(hit.bubbleBottom()-hit.bubbleY()),.001F);
                        assertTrue(hit.bubbleX()>=10 && hit.bubbleX()+hit.bubbleWidth()<=290);
                        var tapped=view.imageHitAt(hit.bubbleX()+hit.bubbleWidth()/2,hit.bubbleY()+5);
                        if(loaded)assertNotNull(tapped);else assertNull(tapped);
                        assertNull(view.imageHitAt(hit.bubbleX()-2,hit.bubbleY()+5));
                    } finally{view.dispose();}
                }
            }
        } finally{saved.applyTo(cfg);cfg.imageMessagesEnabled=images;cfg.timestampIntervalMinutes=clock;}
    }

    @Test void veryNarrowDuplicateSelectionMatchesDrawnLines() {
        var cfg=AtomChatConfig.get();int clock=cfg.timestampIntervalMinutes;
        try(var surface=Surface.makeRasterN32Premul(400,2400)) {
            cfg.timestampIntervalMinutes=0;
            var msg=new ChatMessage(Component.literal("iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii"),false).withDuplicateCount(1000);
            var view=view(msg,null);
            try {
                view.draw(surface.getCanvas(),10,10,150,2300,List.of(msg),new ScrollController());
                var hit=view.hits().get(0);var lines=view.textLinesForHit(hit);
                float drawnHeight=hit.bubbleBottom()-hit.bubbleY()-com.atom.chat.ui.UiTokens.BUBBLE_PAD_Y;
                assertEquals(drawnHeight,lines.size()*lines.get(0).height(),.01F,"selection must retain the drawn line breaks even below 25px");
            } finally{view.dispose();}
        } finally{cfg.timestampIntervalMinutes=clock;}
    }

    @Test void duplicateCountsRemainInsideTheRowForTextAndImages() {
        var cfg=AtomChatConfig.get();int clock=cfg.timestampIntervalMinutes;boolean images=cfg.imageMessagesEnabled;
        try(var target=Surface.makeRasterN32Premul(400,1000)) {
            cfg.timestampIntervalMinutes=0;cfg.imageMessagesEnabled=true;
            for(boolean own:new boolean[]{false,true}) for(boolean picture:new boolean[]{false,true}) {
                String body=picture?"[[CICode,url=atomchat-test://duplicate,w=600,h=200]]":"重复消息的正文仍然应当清楚可读。".repeat(4);
                var msg=new ChatMessage(Component.literal(body),own).withDuplicateCount(1000);
                var view=view(msg,null);
                try {
                    view.draw(target.getCanvas(),10,10,280,900,List.of(msg),new ScrollController());
                    var hit=view.hits().get(0);
                    var font=com.atom.chat.font.FontManager.font(com.atom.chat.ui.UiTokens.FONT_NAME);
                    float label=com.atom.chat.render.SkiaFontRenderer.getStringWidth(font,"x1000");
                    float edge=own?hit.bubbleX()-label-com.atom.chat.ui.UiTokens.s(6)
                            :hit.bubbleX()+hit.bubbleWidth()+label+com.atom.chat.ui.UiTokens.s(6);
                    assertTrue(own?edge>=10:edge<=290,"the complete duplicate counter must fit beside its bubble");
                } finally{view.dispose();}
            }
        } finally{cfg.timestampIntervalMinutes=clock;cfg.imageMessagesEnabled=images;}
    }
}
