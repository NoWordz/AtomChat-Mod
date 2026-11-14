package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingAttachmentsTest {
    private static final NavPage WORLD = NavPage.of(AppPage.WORLD_CHAT);
    private static final class Composer implements DraftStore.ComposerTextPort {
        String text="";
        public String get(){return text;}
        public void set(String value){text=value;}
        public void moveCursorToStart(){}
    }
    @Test void fullDraftRetainsTheCompleteResultUntilThereIsRoom() {
        var pending=new PendingAttachments();var composer=new Composer();composer.text="x".repeat(250);
        var ticket=pending.reserve(WORLD);assertTrue(ticket.complete("[[CICode,url=test]]"));
        assertEquals(0,pending.drain(WORLD,composer));assertTrue(ticket.pending());
        composer.text="draft";
        assertEquals(1,pending.drain(WORLD,composer));assertEquals("draft [[CICode,url=test]]",composer.text);
        assertFalse(ticket.pending());
    }
    @Test void resultCannotLandInAnotherConversationOrAfterConnectionReset() {
        var pending=new PendingAttachments();var composer=new Composer();
        var ticket=pending.reserve(WORLD);ticket.complete("image");
        assertEquals(0,pending.drain(NavPage.privateChat(PlayerRef.of(null,"Alice")),composer));
        assertEquals(0,pending.drain(NavPage.of(AppPage.SETTINGS),composer));
        pending.clear();assertFalse(ticket.complete("late"));
        assertEquals(0,pending.drain(WORLD,composer));assertEquals("",composer.text);
    }
    @Test void reservationsIncludeUnfinishedUploadsAndCancelReleasesCapacity() {
        var pending=new PendingAttachments();
        var first=pending.reserve(WORLD);
        for(int i=1;i<16;i++)assertNotNull(pending.reserve(WORLD));
        assertNull(pending.reserve(WORLD));first.cancel();assertNotNull(pending.reserve(WORLD));
    }
    @Test void unrepresentableCodesCannotBlockALaterSendableAttachment() {
        var pending=new PendingAttachments();
        assertFalse(pending.reserve(WORLD).complete("x".repeat(257)));
        assertEquals(0,pending.size());
        assertTrue(pending.reserve(WORLD).complete("x".repeat(256)));
        var composer=new Composer();assertEquals(1,pending.drain(WORLD,composer));
        assertEquals(256,composer.text.length());assertEquals(0,pending.size());
        assertTrue(pending.reserve(WORLD).complete("x".repeat(255)));
        composer.text="";assertEquals(1,pending.drain(WORLD,composer));
    }
    @Test void closingWithTextRetentionOffKeepsThePendingUploadButDisconnectClearsIt() {
        DraftSessionStore.reset();
        var ticket=DraftSessionStore.attachments().reserve(WORLD);
        DraftSessionStore.saveForScreen(new DraftStore(),AppPage.WORLD_CHAT,null,new Composer(),false,DraftSessionStore.generation());
        assertTrue(ticket.complete("image"));
        var reopened=new Composer();assertEquals(1,DraftSessionStore.attachments().drain(WORLD,reopened));
        assertEquals("image",reopened.text);
        var late=DraftSessionStore.attachments().reserve(WORLD);DraftSessionStore.reset();
        assertFalse(late.complete("late"));
    }
}
