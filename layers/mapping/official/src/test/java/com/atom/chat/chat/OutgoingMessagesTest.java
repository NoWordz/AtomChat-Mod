package com.atom.chat.chat;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OutgoingMessagesTest {
    private static final UUID SELF=UUID.fromString("11111111-2222-3333-4444-555555555555");
    @BeforeEach void reset() { ChatStore.reset(); PrivateChatStore.reset(); OutgoingMessages.clear(); }
    @AfterEach void cleanup() { reset(); }
    private ChatMessage msg(UUID uuid,String name,String body) {
        return new ChatMessage(Component.literal(name+": "+body),true,false,null,null,uuid,name,"Self",body);
    }
    @Test void serverEchoReplacesOriginalBubbleAndKeepsIdAndTimestamp() {
        var local=msg(SELF,"Self","hello"); OutgoingMessages.sent(local,null,"hello"); ChatStore.get().add(local);
        long generation=ChatStore.generation();
        assertTrue(OutgoingMessages.reconcile(msg(SELF,"[VIP] Self","HELLO"),null,true,"hello"));
        assertEquals(1,ChatStore.get().snapshot().size()); var corrected=ChatStore.get().snapshot().get(0);
        assertEquals(local.getId(),corrected.getId()); assertEquals(local.getTimestamp(),corrected.getTimestamp());
        assertEquals("[VIP] Self",corrected.getSenderName()); assertEquals("HELLO",corrected.getContentText());
        assertEquals(OutgoingMessages.State.CONFIRMED,corrected.deliveryState()); assertTrue(ChatStore.generation()>generation);
    }
    @Test void sameTextFromAnotherPlayerOrWeakAttributionCannotConsumeTheSend() {
        var local=msg(SELF,"Self","hello");OutgoingMessages.sent(local,null,"hello");ChatStore.get().add(local);
        assertFalse(OutgoingMessages.reconcile(msg(UUID.randomUUID(),"Other","hello"),null,true,null));
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"Self","hello"),null,false,null));
        assertEquals(OutgoingMessages.State.PENDING,local.deliveryState());
        assertTrue(OutgoingMessages.reconcile(msg(SELF,"Self","hello"),null,true,null));
    }
    @Test void opaqueRewriteAndWrongConversationRemainUnconfirmed() {
        var local=msg(SELF,"Self","hello");OutgoingMessages.sent(local,null,"hello");ChatStore.get().add(local);
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"Self","unrelated"),null,true,null));
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"Self","hello"),PlayerRef.of(null,"Alice"),true,null));
        OutgoingMessages.expire(System.currentTimeMillis()+OutgoingMessages.TIMEOUT_MS+1);
        assertEquals(OutgoingMessages.State.UNCONFIRMED,local.deliveryState());
        assertNotEquals(OutgoingMessages.State.FAILED,local.deliveryState());
    }
    @Test void identicalOutstandingSendsDoNotIdentifyAUniqueBubble() {
        var one=msg(SELF,"Self","same");var two=msg(SELF,"Self","same");
        OutgoingMessages.sent(one,null,"same");ChatStore.get().add(one);
        OutgoingMessages.sent(two,null,"same");ChatStore.get().add(two);
        assertEquals(2,ChatStore.get().snapshot().size());
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"[A] Self","same"),null,true,null));
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"[A] Self","rewrite"),null,true,"same"));
        assertEquals(one.getId(),ChatStore.get().snapshot().get(0).getId());
        assertEquals(OutgoingMessages.State.PENDING,ChatStore.get().snapshot().get(0).deliveryState());
        assertEquals(OutgoingMessages.State.PENDING,ChatStore.get().snapshot().get(1).deliveryState());
    }
    @Test void sameConversationNameWithConflictingUuidsDoesNotAcknowledgeAPrivateSend() {
        var original=PlayerRef.of(UUID.randomUUID(),"Alice");
        var other=PlayerRef.of(UUID.randomUUID(),"Alice");
        var local=msg(SELF,"Self","secret");
        OutgoingMessages.sent(local,original,"secret");PrivateChatStore.addOutgoing(original,local);
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"Self","secret"),other,true,null));
        assertEquals(OutgoingMessages.State.PENDING,local.deliveryState());
    }
    @Test void aSendLostToCapacityCanStillMakeACurrentMatchAmbiguous() {
        var first=msg(SELF,"Self","same");
        OutgoingMessages.sent(first,null,"same");ChatStore.get().add(first);
        for(int i=0;i<31;i++) OutgoingMessages.sent(msg(SELF,"Self","other "+i),null,"other "+i);
        var latest=msg(SELF,"Self","same");
        OutgoingMessages.sent(latest,null,"same");ChatStore.get().add(latest);
        assertEquals(OutgoingMessages.State.UNCONFIRMED,first.deliveryState());
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"Self","same"),null,true,"same"));
        assertEquals(OutgoingMessages.State.PENDING,latest.deliveryState());
    }
    @Test void unmatchedIncomingCannotMergeAwayThePendingRow() {
        var local=msg(SELF,"Self","same");OutgoingMessages.sent(local,null,"same");
        assertFalse(MessageMerge.canMerge(local,msg(SELF,"Self","same")));
        OutgoingMessages.expire(System.currentTimeMillis()+OutgoingMessages.TIMEOUT_MS+1);
        assertFalse(MessageMerge.canMerge(local,msg(SELF,"Self","same")));
    }
    @Test void sequentialConfirmedRepeatsFoldPublicAndPrivateWithoutLosingDeliveryState() {
        var previous = MessageMerge.antiSpamEnabledSupplier;
        MessageMerge.antiSpamEnabledSupplier = () -> true;
        try {
            for (var partner : Arrays.asList(null, PlayerRef.of(null,"Alice"))) {
                ChatMessage first = null;
                for (int i = 0; i < 3; i++) {
                    var local = msg(SELF,"Self","same");
                    if (i == 0) first = local;
                    OutgoingMessages.sent(local,partner,"same");
                    if (partner == null) ChatStore.get().add(local);
                    else PrivateChatStore.addOutgoing(partner,local);
                    assertTrue(OutgoingMessages.reconcile(msg(SELF,"Self","same"),partner,true,"same"));
                }
                var rows = partner == null ? ChatStore.get().snapshot() : PrivateChatStore.messages(partner);
                assertEquals(1,rows.size());
                assertEquals(3,rows.get(0).getDuplicateCount());
                assertEquals(first.getId(),rows.get(0).getId());
                assertEquals(OutgoingMessages.State.CONFIRMED,rows.get(0).deliveryState());
            }
        } finally {
            MessageMerge.antiSpamEnabledSupplier = previous;
        }
    }

    @Test void confirmationBindsTheHudAndStoredCopyToTheirOwnReactionDelivery() {
        var previous = MessageMerge.antiSpamEnabledSupplier;
        MessageMerge.antiSpamEnabledSupplier = () -> true;
        var fingerprint = UUID.randomUUID();
        try {
            for (int i = 0; i < 2; i++) {
                var local = msg(SELF,"Self","same");
                var received = msg(SELF,"Self","same");
                var delivery = UUID.randomUUID();
                OutgoingMessages.sent(local,null,"same");
                ChatStore.get().add(local);
                var receipt = new com.atom.chat.reaction.ReactionWire.Receipt(delivery,fingerprint);
                com.atom.chat.reaction.ReactionClient.accept(com.atom.chat.reaction.ReactionWire.receipt(receipt));
                try (var scope = com.atom.chat.reaction.ReactionClient.incoming(fingerprint)) {
                    assertTrue(OutgoingMessages.reconcile(received,null,true,"same"));
                }
                assertEquals(i + 1, ChatStore.get().snapshot().size());
                assertEquals(delivery, com.atom.chat.reaction.ReactionClient.key(received));
                assertEquals(delivery, com.atom.chat.reaction.ReactionClient.key(ChatStore.get().snapshot().get(i)));
            }
        } finally {
            com.atom.chat.reaction.ReactionClient.reset();
            MessageMerge.antiSpamEnabledSupplier = previous;
        }
    }

    @Test void privateEchoUpdatesOnlyItsOwnConversation() {
        var partner=PlayerRef.of(null,"Alice");var local=msg(SELF,"Self","secret");
        OutgoingMessages.sent(local,partner,"secret");PrivateChatStore.addOutgoing(partner,local);
        assertFalse(OutgoingMessages.reconcile(msg(SELF,"Self","secret"),null,true,null));
        assertTrue(OutgoingMessages.reconcile(msg(SELF,"Self","secret"),partner,true,null));
        assertEquals(1,PrivateChatStore.messages(partner).size());
        assertEquals(local.getId(),PrivateChatStore.messages(partner).get(0).getId());
    }
    @Test void explicitOriginalBodyWinsOverAnEarlierSendMatchingTheRewrittenText() {
        var a=msg(SELF,"Self","A");var b=msg(SELF,"Self","B");
        OutgoingMessages.sent(a,null,"A");ChatStore.get().add(a);OutgoingMessages.sent(b,null,"B");ChatStore.get().add(b);
        assertTrue(OutgoingMessages.reconcile(msg(SELF,"Self","A"),null,true,"B"));
        assertEquals(OutgoingMessages.State.PENDING,ChatStore.get().snapshot().get(0).deliveryState());
        assertEquals(OutgoingMessages.State.CONFIRMED,ChatStore.get().snapshot().get(1).deliveryState());
        assertEquals(b.getId(),ChatStore.get().snapshot().get(1).getId());
    }
    @Test void lateButExplicitlyCorrelatedEchoStillCorrectsTheOriginalRow() {
        var local=msg(SELF,"Self","sent");OutgoingMessages.sent(local,null,"sent");ChatStore.get().add(local);
        OutgoingMessages.expire(System.currentTimeMillis()+OutgoingMessages.TIMEOUT_MS+1);
        assertEquals(OutgoingMessages.State.UNCONFIRMED,local.deliveryState());
        assertTrue(OutgoingMessages.reconcile(msg(SELF,"Self","rewritten"),null,true,"sent"));
        assertEquals(local.getId(),ChatStore.get().snapshot().get(0).getId());
        assertEquals("rewritten",ChatStore.get().snapshot().get(0).getContentText());
    }
}
