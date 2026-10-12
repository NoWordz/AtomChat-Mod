package com.atom.chat.reaction;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ReactionLedgerTest {
    private final UUID player=UUID.randomUUID();
    private final Object connection=new Object();
    private final UUID key=UUID.randomUUID();

    @Test void unseenKeySelfRegisters() {
        ReactionLedger ledger=new ReactionLedger();
        // No delivery handshake: the reacting client supplies the key, so a never
        // seen key simply starts an empty entry.
        assertEquals("",ledger.apply(player,connection,"Alice",new ReactionWire.Action(key,"😀",true),0));
        assertEquals(player,ledger.snapshot(key).entries().get(0).members().get(0).uuid());
    }
    @Test void repeatedSetIsIdempotentAndRemovalDropsEmptyKind() {
        ReactionLedger ledger=new ReactionLedger();
        var add=new ReactionWire.Action(key,"😀",true);
        assertEquals("",ledger.apply(player,connection,"Alice",add,0)); long revision=ledger.revision(key);
        assertEquals("",ledger.apply(player,connection,"Alice",add,1100)); assertEquals(revision,ledger.revision(key));
        assertEquals("",ledger.apply(player,connection,"Alice",new ReactionWire.Action(key,"😀",false),2200));
        assertTrue(ledger.snapshot(key).entries().isEmpty());
    }
    @Test void sameContentKeySharesStateAcrossClients() {
        ReactionLedger ledger=new ReactionLedger();
        UUID bob=UUID.randomUUID(); Object bobConn=new Object();
        assertEquals("",ledger.apply(player,connection,"Alice",new ReactionWire.Action(key,"😀",true),0));
        assertEquals("",ledger.apply(bob,bobConn,"Bob",new ReactionWire.Action(key,"❤️",true),1100));
        assertEquals(2,ledger.snapshot(key).entries().size());
    }
    @Test void rateLimitIsPerConnection() {
        ReactionLedger ledger=new ReactionLedger();
        for(int i=0;i<8;i++) assertEquals("",ledger.apply(player,connection,"Alice",new ReactionWire.Action(key,"😀",true),0));
        assertEquals("rate_limited",ledger.apply(player,connection,"Alice",new ReactionWire.Action(key,"😀",true),0));
    }
    @Test void messageLimitIsFiveDistinctKindsAcrossAllPlayers() {
        ReactionLedger ledger=new ReactionLedger();
        for(int i=0;i<5;i++) {
            UUID who=UUID.randomUUID();
            assertEquals("",ledger.apply(who,new Object(),"P",new ReactionWire.Action(key,ReactionEmoji.ALL.get(i),true),0));
        }
        assertEquals(5,ledger.snapshot(key).entries().size());
        assertEquals("per_message_limit",ledger.apply(UUID.randomUUID(),new Object(),"P",new ReactionWire.Action(key,ReactionEmoji.ALL.get(5),true),0));
        assertEquals(5,ledger.snapshot(key).entries().size());
    }

    @Test void repeatedEmojiFromAnotherPlayerDoesNotUseAnotherKindSlot() {
        ReactionLedger ledger=new ReactionLedger();
        for(int i=0;i<5;i++) {
            UUID who=UUID.randomUUID();
            assertEquals("",ledger.apply(who,new Object(),"P",new ReactionWire.Action(key,ReactionEmoji.ALL.get(i),true),0));
        }
        UUID sixthPlayer=UUID.randomUUID();
        assertEquals("",ledger.apply(sixthPlayer,new Object(),"Q",new ReactionWire.Action(key,ReactionEmoji.ALL.get(0),true),0));
        assertEquals(5,ledger.snapshot(key).entries().size());
        assertEquals(2,ledger.snapshot(key).entries().get(0).members().size());
    }

    @Test void recreatedKeyGetsNewerRevisionAfterEvictionEvenForNoOpRemoval() {
        ReactionLedger ledger=new ReactionLedger();
        assertEquals("",ledger.apply(player,new Object(),"Alice",new ReactionWire.Action(key,"😀",true),0));
        long previousRevision=ledger.revision(key);
        for(int i=0;i<4096;i++) {
            UUID otherKey=new UUID(0L,i+1L);
            assertEquals("",ledger.apply(UUID.randomUUID(),new Object(),"P",
                    new ReactionWire.Action(otherKey,"😀",true),0));
        }
        assertEquals(-1L,ledger.revision(key),"the original key was evicted");

        assertEquals("",ledger.apply(player,new Object(),"Alice",new ReactionWire.Action(key,"😀",false),0));
        assertTrue(ledger.snapshot(key).revision()>previousRevision,
                "the empty recreated snapshot must supersede the client's old state");
    }
    @Test void contentKeyDependsOnAuthorAndBodyOnly() {
        assertEquals(ReactionFingerprint.contentKey("Alice","hi"),ReactionFingerprint.contentKey("Alice","hi"));
        assertNotEquals(ReactionFingerprint.contentKey("Alice","hi"),ReactionFingerprint.contentKey("Bob","hi"));
        assertNotEquals(ReactionFingerprint.contentKey("Alice","hi"),ReactionFingerprint.contentKey("Alice","other"));
    }
}
