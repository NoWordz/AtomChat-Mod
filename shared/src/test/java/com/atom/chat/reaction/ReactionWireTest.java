package com.atom.chat.reaction;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class ReactionWireTest {
    @Test void snapshotsKeepPeopleAndRevision() {
        var state=new ReactionWire.Snapshot(UUID.randomUUID(),7,List.of(new ReactionWire.Entry("❤️",List.of(new ReactionWire.Member(UUID.randomUUID(),"Alice")))));
        assertEquals(state,ReactionWire.readServer(ReactionWire.snapshot(state)));
    }
    @Test void receiptAndActionRoundTrip() {
        var receipt=new ReactionWire.Receipt(UUID.randomUUID(),UUID.randomUUID());
        assertEquals(receipt,ReactionWire.readServer(ReactionWire.receipt(receipt)));
        var action=new ReactionWire.Action(receipt.id(),"😀",true);
        assertEquals(action,ReactionWire.readAction(ReactionWire.action(action)));
    }
    @Test void rejectsUnknownEmojiTrailingDataAndOversizedPackets() {
        var id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->ReactionWire.readAction(ReactionWire.action(new ReactionWire.Action(id,"not-emoji",true))));
        byte[] good=ReactionWire.action(new ReactionWire.Action(id,"😀",true));
        assertThrows(IllegalArgumentException.class,()->ReactionWire.readAction(Arrays.copyOf(good,good.length+1)));
        assertThrows(IllegalArgumentException.class,()->ReactionWire.readServer(new byte[ReactionWire.MAX_BYTES+1]));
    }
    private static ReactionWire.Snapshot legacySnapshot(int count) {
        List<ReactionWire.Entry> entries=new ArrayList<>();
        for(int i=0;i<count;i++) {
            entries.add(new ReactionWire.Entry(ReactionEmoji.ALL.get(i),
                    List.of(new ReactionWire.Member(UUID.randomUUID(),"Player"+i))));
        }
        return new ReactionWire.Snapshot(UUID.randomUUID(),1L,entries);
    }
    @Test void legacySnapshotsDecodeButRetainOnlyTheFirstFiveKinds() {
        for(int count:new int[]{6,20}) {
            var legacy=legacySnapshot(count);
            var decoded=(ReactionWire.Snapshot)ReactionWire.readServer(ReactionWire.snapshot(legacy));
            assertEquals(new ReactionWire.Snapshot(legacy.id(),legacy.revision(),
                    legacy.entries().subList(0,5)),decoded);
        }
    }
    @Test void snapshotsOverTheLegacyTwentyKindLimitAreRejected() {
        assertThrows(IllegalArgumentException.class,
                ()->ReactionWire.readServer(ReactionWire.snapshot(legacySnapshot(21))));
    }
    @Test void discardedLegacyKindsAreStillValidatedAndTrailingBytesAreRejected() {
        var legacy=legacySnapshot(6);
        List<ReactionWire.Entry> entries=new ArrayList<>(legacy.entries());
        entries.set(5,new ReactionWire.Entry(entries.get(0).emoji(),entries.get(5).members()));
        assertThrows(IllegalArgumentException.class,()->ReactionWire.readServer(ReactionWire.snapshot(
                new ReactionWire.Snapshot(legacy.id(),legacy.revision(),entries))));
        entries.set(5,new ReactionWire.Entry(ReactionEmoji.ALL.get(5),List.of()));
        assertThrows(IllegalArgumentException.class,()->ReactionWire.readServer(ReactionWire.snapshot(
                new ReactionWire.Snapshot(legacy.id(),legacy.revision(),entries))));
        byte[] packet=ReactionWire.snapshot(legacy);
        assertThrows(IllegalArgumentException.class,
                ()->ReactionWire.readServer(Arrays.copyOf(packet,packet.length+1)));
    }
    @Test void unsignedIdentityUsesWirePrecisionAndNotSessionReconstruction() {
        UUID author=UUID.randomUUID(); Instant nano=Instant.parse("2026-10-12T00:00:00.123456789Z");
        assertEquals(ReactionFingerprint.of(author,null,nano,0,"hi"),ReactionFingerprint.of(author,null,Instant.ofEpochMilli(nano.toEpochMilli()),0,"hi"));
        assertNotEquals(ReactionFingerprint.of(author,null,nano,0,"hi"),ReactionFingerprint.of(author,null,nano,0,"other"));
    }
    @Test void signedIdentityDoesNotDependOnDecorationOrTimestamp() {
        UUID author=UUID.randomUUID(); byte[] signature={1,2,3};
        assertEquals(ReactionFingerprint.of(author,signature,Instant.EPOCH,0,"plain"),ReactionFingerprint.of(author,signature,Instant.now(),9,"decorated"));
        assertNotEquals(ReactionFingerprint.of(author,signature,Instant.EPOCH,0,"plain"),ReactionFingerprint.of(UUID.randomUUID(),signature,Instant.EPOCH,0,"plain"));
    }
}
