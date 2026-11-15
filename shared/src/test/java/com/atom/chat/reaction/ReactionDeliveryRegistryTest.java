package com.atom.chat.reaction;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReactionDeliveryRegistryTest {
    @Test void exceptionalAndNestedSendsRestoreOnlyTheEnclosingDelivery() {
        var registry=new ReactionDeliveryRegistry();
        Object receiver=new Object(), outer=new Object(), inner=new Object();
        UUID first=UUID.randomUUID(), second=UUID.randomUUID();
        try(var scope=registry.scope(receiver,outer,first)) {
            assertThrows(IllegalStateException.class,()->{
                try(var nested=registry.scope(receiver,inner,second)) {
                    assertEquals(second,registry.take(receiver,inner));
                    throw new IllegalStateException("cancelled send");
                }
            });
            assertEquals(first,registry.take(receiver,outer));
        }
        assertNull(registry.take(receiver,outer));
        try(var cancelled=registry.scope(receiver,outer,first)) { }
        assertNull(registry.take(receiver,outer));
    }
    @Test void privateTargetsHaveSeparateIdsEvenIfTheSenderNeverReceivesAnEcho() {
        var targets=new ReactionMessageTargets<>(java.util.List.of("Bob","Carol"));
        var iterator=targets.iterator();
        assertEquals("Bob",iterator.next()); UUID bob=targets.currentId();
        assertEquals("Carol",iterator.next()); UUID carol=targets.currentId();
        assertNotEquals(bob,carol);
    }
    @Test void repeatedNativeMessageStartsANewEventButRecipientsShareAnEvent() {
        var registry = new ReactionDeliveryRegistry();
        UUID fingerprint=UUID.randomUUID(), alice=UUID.randomUUID(), bob=UUID.randomUUID();
        UUID first=registry.nativeId(fingerprint,alice);
        assertEquals(first,registry.nativeId(fingerprint,bob));
        UUID second=registry.nativeId(fingerprint,alice);
        assertNotEquals(first,second);
        assertEquals(second,registry.nativeId(fingerprint,bob));
    }
    @Test void broadcastPreparationIsPerReceiverPerComponentAndConsumedOnce() {
        var registry = new ReactionDeliveryRegistry();
        Object alice=new Object(), bob=new Object(), message=new Object(), other=new Object();
        UUID event=UUID.randomUUID();
        try(var aliceScope=registry.scope(alice,message,event);var bobScope=registry.scope(bob,message,event)) {
        assertNull(registry.take(alice,other));
        assertEquals(event,registry.take(alice,message));
        assertNull(registry.take(alice,message));
        assertEquals(event,registry.take(bob,message));
        }
    }
    @Test void audienceNeverIncludesSomeoneWhoWasNotSentTheMessage() {
        var registry = new ReactionDeliveryRegistry();
        UUID event=UUID.randomUUID(), alice=UUID.randomUUID(), bob=UUID.randomUUID();
        registry.register(event,alice);
        assertTrue(registry.received(event,alice));
        assertFalse(registry.received(event,bob));
        assertFalse(registry.received(UUID.randomUUID(),alice));
    }
}
