package com.atom.chat.reaction;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.MessageMerge;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReactionClientIdentityTest {
    @AfterEach void reset() { ReactionClient.reset(); }
    private ChatMessage row() { return new ChatMessage(Component.literal("<Alice> hello"),false,false,null,null,null,"Alice","Alice","hello"); }
    @Test void identicalMessagesOnlyShowTheReactionOnTheirOwnDelivery() {
        UUID fingerprint=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
        var a=row(); var b=row();
        ReactionClient.accept(ReactionWire.receipt(new ReactionWire.Receipt(first,fingerprint)));
        ReactionClient.accept(ReactionWire.receipt(new ReactionWire.Receipt(second,fingerprint)));
        try(var scope=ReactionClient.incoming(fingerprint)) { ReactionClient.bind(a); }
        try(var scope=ReactionClient.incoming(fingerprint)) { ReactionClient.bind(b); }
        ReactionClient.accept(ReactionWire.snapshot(new ReactionWire.Snapshot(second,1,List.of(new ReactionWire.Entry("😀",List.of(new ReactionWire.Member(UUID.randomUUID(),"Bob")))))));
        assertTrue(ReactionClient.entries(a).isEmpty());
        assertEquals(1,ReactionClient.entries(b).size());
        assertNotEquals(ReactionClient.key(a),ReactionClient.key(b));
        assertEquals(second,ReactionClient.key(b.withDuplicateCount(2)));
        assertFalse(MessageMerge.canMerge(a,b));
    }
    @Test void delayedNativeChatCannotBorrowTheFollowingImmediateDelivery() {
        UUID fingerprint=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
        var a=row();var b=row();java.util.function.BooleanSupplier delayed;
        ReactionClient.accept(ReactionWire.receipt(new ReactionWire.Receipt(first,fingerprint)));
        try(var scope=ReactionClient.incoming(fingerprint)) { delayed=ReactionClient.defer(()->{ReactionClient.bind(a);return true;}); }
        ReactionClient.accept(ReactionWire.receipt(new ReactionWire.Receipt(second,fingerprint)));
        try(var scope=ReactionClient.incoming(fingerprint)) { ReactionClient.bind(b); }
        assertTrue(delayed.getAsBoolean());
        assertEquals(first,ReactionClient.key(a));assertEquals(second,ReactionClient.key(b));
    }
    @Test void resetRevokesQueuedCallbacksAndTextAloneDoesNotGrantAnIdentity() {
        UUID fingerprint=UUID.randomUUID();var a=row();java.util.function.BooleanSupplier delayed;
        ReactionClient.accept(ReactionWire.receipt(new ReactionWire.Receipt(UUID.randomUUID(),fingerprint)));
        try(var scope=ReactionClient.incoming(fingerprint)) { delayed=ReactionClient.defer(()->{ReactionClient.bind(a);return true;}); }
        ReactionClient.reset();delayed.getAsBoolean();assertNull(ReactionClient.key(a));assertNull(ReactionClient.key(row()));
    }
    @Test void queuedOldConnectionReceiptCannotPoisonTheNextConnection() {
        Object oldConnection=new Object(), newConnection=new Object();
        UUID fingerprint=UUID.randomUUID(), stale=UUID.randomUUID(), fresh=UUID.randomUUID();
        ReactionClient.reset();
        ReactionClient.acceptFrom(oldConnection,newConnection,ReactionWire.receipt(new ReactionWire.Receipt(stale,fingerprint)));
        ReactionClient.acceptFrom(newConnection,newConnection,ReactionWire.receipt(new ReactionWire.Receipt(fresh,fingerprint)));
        var message=row();
        try(var scope=ReactionClient.incoming(fingerprint)) { ReactionClient.bind(message); }
        assertEquals(fresh,ReactionClient.key(message));
    }
}
