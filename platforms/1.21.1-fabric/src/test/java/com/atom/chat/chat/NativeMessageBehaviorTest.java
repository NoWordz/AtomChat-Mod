package com.atom.chat.chat;
import com.atom.chat.text.RichText;
import com.atom.chat.platform.Platform;
import com.atom.chat.history.HistoryStore;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.fun.ChatEffectEvents;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.HoverEvent;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class NativeMessageBehaviorTest {
    private static final UUID ALICE=UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final PlayerRef PEER=PlayerRef.of(ALICE,"Alice");
    private static final String SECRET="signed-original-secret";
    @BeforeAll static void setup(@TempDir Path path){
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        Platform.install(new Platform.Provider(){public Path configDir(){return path;}public Path gameDir(){return path;}public boolean isModLoaded(String id){return false;}});
    }
    @BeforeEach void reset(){ChatStore.reset();PrivateChatStore.reset();OutgoingMessages.clear();NativeMessageSync.start();MessageMerge.antiSpamEnabledSupplier=()->true;}
    @AfterEach void cleanup(){ChatStore.reset();PrivateChatStore.reset();OutgoingMessages.clear();NativeMessageSync.start();MessageMerge.antiSpamEnabledSupplier=()->false;}
    static String signature(int seed){byte[] bytes=new byte[256];Arrays.fill(bytes,(byte)seed);return NativeMessageMeta.signature(bytes);}
    private static ChatMessage message(String body,int seed,int tick,boolean own){
        var rich=RichText.literal(body);
        return new ChatMessage(rich.toText(),own,false,null,null,ALICE,"Alice","Alice",body,RichText.literal("Alice"),rich)
                .withNativeMeta(new NativeMessageMeta(seed<0?null:signature(seed),true,null,false,tick,NativeMessageSync.session()));
    }
    @Test void echoAttachesServerMetaBeforeRawReplacementAndDisplayCopiesKeepIt(){
        var local=message("echo",-1,0,true);ChatStore.get().add(local);OutgoingMessages.sent(local,null,"echo");
        var received=message("echo",1,100,true);
        assertTrue(OutgoingMessages.reconcile(received,null,true,"echo"));
        var stored=ChatStore.get().rawSnapshot().get(0);
        assertEquals(local.getId(),stored.getId());assertEquals(local.getTimestamp(),stored.getTimestamp());
        assertEquals(received.nativeMeta(),stored.nativeMeta());assertEquals(100,stored.nativeMeta().addedTick());
        assertEquals(stored.nativeMeta(),stored.withDuplicateCount(3).nativeMeta());
        assertEquals(1,NativeMessageSync.applied(signature(1),NativeMessageBridge.deletedMarker()));
        assertTrue(ChatStore.get().rawSnapshot().get(0).isDeleted());
    }
    @Test void exactSignatureDeletesEveryRawPresentationWithoutDeletingUnsignedOrSameTextOtherSignatures(){
        var a=message("same",1,0,false);var b=message("same",2,1,false);var unsigned=message("same",-1,2,false);
        ChatStore.get().add(a);ChatStore.get().add(b);ChatStore.get().add(unsigned);
        PrivateChatStore.addIncoming(PEER,a);
        int publicUnread=ChatStore.publicUnread(),privateUnread=PrivateChatStore.unread(PEER);
        AtomicInteger effects=new AtomicInteger();java.util.function.BiConsumer<ChatMessage,PlayerRef> listener=(m,p)->effects.incrementAndGet();ChatEffectEvents.install(listener);
        try {
            assertEquals(2,NativeMessageSync.applied(signature(1),NativeMessageBridge.deletedMarker()));
            assertEquals(0,NativeMessageSync.applied(signature(1),NativeMessageBridge.deletedMarker()));
            var raw=ChatStore.get().rawSnapshot();assertEquals(3,raw.size());assertEquals(a.getId(),raw.get(0).getId());
            assertTrue(raw.get(0).isDeleted());assertFalse(raw.get(1).isDeleted());assertFalse(raw.get(2).isDeleted());
            assertTrue(PrivateChatStore.rawMessages(PEER).get(0).isDeleted());
            assertFalse(MessageMerge.canMerge(raw.get(0),raw.get(1)));
            assertEquals(publicUnread,ChatStore.publicUnread());assertEquals(privateUnread,PrivateChatStore.unread(PEER));assertEquals(0,effects.get());
            assertNotNull(NativeMessageSync.knownDeleted(signature(1)));
        } finally {ChatEffectEvents.uninstall(listener);}
    }
    @Test void nativeMissingEntryStillWaitsSixtyTicksFromItsActualAddition(){
        var old=message(SECRET,1,100,false);ChatStore.get().add(old);
        for(int i=0;i<120;i++)ChatStore.get().add(message("flood-"+i,-1,101,false));
        NativeMessageSync.request(signature(1),110,false);
        assertEquals(1,NativeMessageSync.pendingCount());NativeMessageSync.tick(159,key->false);
        assertFalse(ChatStore.get().rawSnapshot().get(0).isDeleted());
        NativeMessageSync.tick(160,key->false);assertTrue(ChatStore.get().rawSnapshot().get(0).isDeleted());assertEquals(0,NativeMessageSync.pendingCount());
        assertEquals(121,ChatStore.get().rawSnapshot().size());
    }
    @Test void nativePresenceDefersToVanillaAndEvictionOrDisconnectRetiresCoordination(){
        ChatStore.get().add(message(SECRET,1,100,false));NativeMessageSync.request(signature(1),200,true);
        assertEquals(0,NativeMessageSync.pendingCount());assertFalse(ChatStore.get().rawSnapshot().get(0).isDeleted());
        NativeMessageSync.request(signature(1),110,false);
        for(int i=0;i<500;i++)ChatStore.get().add(message("flood-"+i,-1,111,false));
        NativeMessageSync.tick(200,key->false);assertEquals(0,NativeMessageSync.pendingCount());
        var loaded=message(SECRET,2,100000,false).withNativeMeta(new NativeMessageMeta(signature(2),true,null,false,100000,NativeMessageSync.session()-1));
        NativeMessageSync.loaded(loaded,20);ChatStore.get().add(loaded);
        NativeMessageSync.request(signature(2),21,false);NativeMessageSync.tick(79,key->false);assertFalse(loaded.isDeleted());
        NativeMessageSync.start();assertEquals(0,NativeMessageSync.pendingCount());
    }
    @Test void removalRebuildsAllBodyQuoteTooltipAndNameActionPayloadsAndNormalizesDiskDto(){
        var hover=new HoverEvent(HoverEvent.Action.SHOW_TEXT,Text.literal(SECRET));
        var sender=RichText.of(Text.literal("Alice").setStyle(Style.EMPTY.withColor(0x123456).withInsertion(SECRET).withHoverEvent(hover)));
        var body=RichText.of(Text.literal(SECRET).append(Text.literal("nested-"+SECRET).setStyle(Style.EMPTY.withHoverEvent(hover))));
        var modified=NativeMessageBridge.tag(MessageIndicator.modified(SECRET));
        var original=new ChatMessage(body.toText(),false,false,"Alice",SECRET,ALICE,"Alice","Alice",SECRET,sender,body)
                .withNativeMeta(new NativeMessageMeta(signature(1),true,modified,false,0,NativeMessageSync.session()));
        var clean=original.asDeleted(NativeMessageBridge.deletedMarker());
        assertEquals(original.getId(),clean.getId());assertEquals(original.getTimestamp(),clean.getTimestamp());
        assertFalse(clean.getRawText().contains(SECRET));assertFalse(clean.getContentText().contains(SECRET));assertFalse(clean.getContentRich().getString().contains(SECRET));
        assertNull(clean.getQuoteName());assertNull(clean.getQuoteText());assertTrue(clean.getQuoteTextRich().isEmpty());
        for(var run:clean.getSenderRich().runs()){assertNull(run.style().getHoverEvent());assertNull(run.style().getClickEvent());assertNull(run.style().getInsertion());}
        var entry=new HistoryStore.Entry(1,false,null,false,false,"Alice",SECRET,ALICE.toString(),"Alice","Alice",SECRET,
                "{\"text\":\""+SECRET+"\"}","{\"text\":\"Alice\",\"color\":\"#123456\",\"insertion\":\""+SECRET+"\"}","{\"text\":\""+SECRET+"\"}",clean.nativeMeta());
        String encoded=HistoryStore.toLine(entry);assertFalse(encoded.contains(SECRET));
        var decoded=HistoryStore.fromLine(encoded);assertNotNull(decoded);assertTrue(decoded.nativeMeta.deleted());assertNull(decoded.quoteText);assertNull(decoded.bodyComponentJson);assertTrue(decoded.senderComponentJson.contains("#123456"));assertFalse(decoded.senderComponentJson.contains("insertion"));
        var malicious=encoded.replace("\"comp\":{", "\"bodyComp\":{\"text\":\""+SECRET+"\"},\"comp\":{");
        assertFalse(HistoryStore.toLine(HistoryStore.fromLine(malicious)).contains(SECRET));
    }
    @Test void actualNativeTagsPreserveNullSecureDecisionColorsIconsAndModifiedTooltip(){
        var secure=NativeMessageBridge.capture(null,null);assertTrue(secure.captured());assertNull(secure.tag());assertFalse(NativeMessageMeta.UNKNOWN.captured());
        var original=MessageIndicator.modified(SECRET);var copied=NativeMessageBridge.tag(original);
        assertEquals(original.indicatorColor(),copied.color());assertEquals(original.icon().name(),copied.icon().name());
        assertEquals(original.text().getString(),NativeMessageBridge.tooltip(copied).getString());assertTrue(copied.tooltipJson().contains(SECRET));
        var entry=new HistoryStore.Entry(1,false,null,false,false,null,null,null,"Alice","Alice","body",null,null,null,new NativeMessageMeta(signature(1),true,copied,false,0,1));
        assertEquals(entry.nativeMeta,HistoryStore.fromLine(HistoryStore.toLine(entry)).nativeMeta);
        var malformed=HistoryStore.fromLine("{\"ts\":1,\"content\":\"kept\",\"sig\":{},\"tag\":false}");
        assertNotNull(malformed);assertEquals("kept",malformed.contentText);assertNull(malformed.nativeMeta.signature());
        var legacy=HistoryStore.fromLine("{\"ts\":1,\"content\":\"legacy\"}");assertNotNull(legacy);assertEquals(NativeMessageMeta.UNKNOWN,legacy.nativeMeta);
    }
    private static com.atom.chat.page.MessageListView view(){return new com.atom.chat.page.MessageListView(new com.atom.chat.page.MessageListView.Host(){
        public UUID ownUuid(){return null;}public String ownName(){return "Local";}public String senderName(ChatMessage m){return "Alice";}public long openStart(){return Long.MAX_VALUE;}
    });}
    @SuppressWarnings("unchecked") private static List<NotificationBanner.Active> banners()throws Exception{var field=NotificationBanner.class.getDeclaredField("banners");field.setAccessible(true);return (List<NotificationBanner.Active>)field.get(NotificationBanner.INSTANCE);}
    @Test void sameIdDeletionInvalidatesRealViewSelectionHitWrappedNameAndNotificationState()throws Exception {
        NotificationBanner.INSTANCE.reset();
        var nativeTag=NativeMessageBridge.tag(MessageIndicator.modified(SECRET));
        var original=message(SECRET,1,0,false).withNativeMeta(new NativeMessageMeta(signature(1),true,nativeTag,false,0,NativeMessageSync.session()));
        ChatStore.get().add(original);NotificationBanner.INSTANCE.enqueue(NotificationBanner.Type.MENTION,original.getSenderRich(),original.getContentRich(),original);
        long born=banners().get(0).born();
        var view=view();var scroll=new com.atom.chat.ui.ScrollController();
        try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(440,600);var pixels=new io.github.humbleui.skija.Bitmap()) {
            view.draw(surface.getCanvas(),10,10,400,580,ChatStore.get().snapshot(),scroll);
            var hit=view.hits().get(0);var line=view.textLinesForHit(hit).get(0);
            view.beginSelection(hit,line,line.x());
            view.dragSelection(line.x()+com.atom.chat.render.RichTextRenderer.width(com.atom.chat.font.FontManager.font(com.atom.lib.design.atomchat.UiTokens.FONT_BODY),line.richLine()),line.y()+line.height()/2);
            assertTrue(view.copySelection().contains(SECRET));
            float barX=hit.bubbleX()-com.atom.lib.design.atomchat.UiTokens.INLINE_ITEM_GAP-com.atom.lib.design.atomchat.UiTokens.s(1);
            var tagSpan=view.clickableSpanAt(barX,hit.bubbleY()+1).orElseThrow();
            assertTrue(tagSpan.style().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT).getString().contains(SECRET));
            assertEquals(1,NativeMessageSync.applied(signature(1),NativeMessageBridge.deletedMarker()));
            assertEquals("",view.copySelection());assertTrue(view.hits().isEmpty());assertTrue(view.clickableSpanAt(barX,hit.bubbleY()+1).isEmpty());
            assertEquals(1,banners().size());assertEquals(born,banners().get(0).born());assertFalse(banners().get(0).content().getString().contains(SECRET));
            assertTrue(banners().get(0).message().isDeleted());
            surface.getCanvas().clear(0);view.draw(surface.getCanvas(),10,10,400,580,ChatStore.get().snapshot(),scroll);
            var changed=view.hits().get(0);assertFalse(view.textLinesForHit(changed).get(0).text().contains(SECRET));
            pixels.allocN32Pixels(440,600);assertTrue(surface.readPixels(pixels,0,0));
            int x=Math.round(changed.bubbleX()-com.atom.lib.design.atomchat.UiTokens.INLINE_ITEM_GAP-com.atom.lib.design.atomchat.UiTokens.s(1));
            int yy=Math.round(changed.bubbleY()+changed.bubbleBottom())/2;
            assertEquals(0xFFD0D0D0,pixels.getColor(x,yy),"deleted marker uses the actual native system indicator color");
        } finally {view.dispose();NotificationBanner.INSTANCE.reset();}
    }
    @Test void echoReplacementWithStableIdDoesNotReuseOldWrapOrImageClassification(){
        var original=message("before original body",-1,0,false);var replacement=original.withServerEcho(message("after",1,20,false));
        assertEquals(original.getId(),replacement.getId());var view=view();var scroll=new com.atom.chat.ui.ScrollController();
        try(var surface=io.github.humbleui.skija.Surface.makeRasterN32Premul(440,600)){
            view.draw(surface.getCanvas(),10,10,400,580,List.of(original),scroll);
            assertTrue(view.textLinesForHit(view.hits().get(0)).get(0).text().contains("before"));
            view.draw(surface.getCanvas(),10,10,400,580,List.of(replacement),scroll);
            assertEquals("after",view.textLinesForHit(view.hits().get(0)).get(0).text());
        } finally {view.dispose();}
    }
    @Test void rawDecisionDifferencesSurviveDisplayFoldingWithoutComparingSignaturePresence(){
        var secure=message("same",1,0,false);
        var modified=message("same",2,1,false).withNativeMeta(new NativeMessageMeta(signature(2),true,NativeMessageBridge.tag(MessageIndicator.modified("same")),false,1,NativeMessageSync.session()));
        var unknown=new ChatMessage(RichText.literal("same").toText(),false,false,null,null,ALICE,"Alice","Alice","same",RichText.literal("Alice"),RichText.literal("same"));
        assertFalse(MessageMerge.canMerge(secure,modified));assertFalse(MessageMerge.canMerge(secure,unknown));
        var sameDecision=message("same",3,2,false);assertTrue(MessageMerge.canMerge(secure,sameDecision));
    }
    @Test void armedReplyAndSavedSnapshotsAreCleanedByExactSignatureWhileLiteralUnsignedQuotesRemain(){
        var signed=message(SECRET,1,0,false);var literal=message(SECRET,-1,0,false);
        ChatStore.get().add(signed);ChatStore.get().add(literal);
        var replies=new ReplySlotStore();var world=com.atom.chat.nav.NavPage.of(com.atom.chat.nav.AppPage.WORLD_CHAT);
        replies.arm(signed);replies.syncReplySlot(com.atom.chat.nav.AppPage.WORLD_CHAT,world);var parked=replies.snapshot();
        NativeMessageSync.applied(signature(1),NativeMessageBridge.deletedMarker());
        assertTrue(replies.replyTarget().isDeleted());assertFalse(replies.replyForNav(world,false).getContentText().contains(SECRET));
        replies.restore(parked);assertTrue(replies.replyTarget().isDeleted());
        replies.arm(literal);assertSame(literal,replies.replyTarget());assertTrue(replies.replyTarget().getContentText().contains(SECRET));
    }
    @Test void anUnsignedLiteralQuoteIsNotRedactedByMatchingItsDisplayedText(){
        var target=message(SECRET,1,0,false);
        var quote=new ChatMessage(RichText.literal("「引用 @Alice: "+SECRET+"」 reply").toText(),false,false,"Alice",SECRET,
                ALICE,"Alice","Alice","reply",RichText.literal("Alice"),RichText.literal("reply"));
        ChatStore.get().add(target);ChatStore.get().add(quote);
        assertEquals(1,NativeMessageSync.applied(signature(1),NativeMessageBridge.deletedMarker()));
        assertSame(quote,ChatStore.get().rawSnapshot().get(1));assertEquals(SECRET,quote.getQuoteText());assertFalse(quote.isDeleted());
    }
}
