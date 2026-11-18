package com.atom.chat.ui;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.reaction.ReactionClient;
import com.atom.chat.reaction.ReactionWire;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReactionProjectionTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @org.junit.jupiter.api.BeforeEach void install() {
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider() {
            public java.nio.file.Path configDir() { return directory; }
            public java.nio.file.Path gameDir() { return directory; }
            public boolean isModLoaded(String id) { return false; }
        });
    }
    @AfterEach void reset() {
        ReactionClient.reset();
        ReactionClient.supported = () -> false;
        ReactionClient.self = () -> null;
    }

    private ChatMessage bind(UUID delivery, int index) {
        var row = new ChatMessage(Text.literal("hello " + index), false);
        UUID fingerprint = UUID.randomUUID();
        ReactionClient.accept(ReactionWire.receipt(new ReactionWire.Receipt(delivery, fingerprint)));
        try (var ignored = ReactionClient.incoming(fingerprint)) { ReactionClient.bind(row); }
        return row;
    }

    private static Method frameMethod() throws Exception {
        Method method = ReactionChips.class.getDeclaredMethod("frame", ChatMessage.class);
        method.setAccessible(true);
        return method;
    }

    @Test void modelChangesAndIdentityAreReflectedOnNextFrame() throws Exception {
        ReactionClient.supported = () -> true;
        UUID me = UUID.randomUUID(), other = UUID.randomUUID(), delivery = UUID.randomUUID();
        ReactionClient.self = () -> me;
        ChatMessage row = bind(delivery, 1);
        ReactionChips chips = new ReactionChips();
        Method frame = frameMethod();
        var member = new ReactionWire.Member(me, "me");
        ReactionClient.accept(ReactionWire.snapshot(new ReactionWire.Snapshot(delivery, 1,
                List.of(new ReactionWire.Entry("😀", List.of(member))))));
        chips.begin(0, 0, 400, 400);
        Object first = frame.invoke(chips, row);
        Method cells = first.getClass().getDeclaredMethod("cells"); cells.setAccessible(true);
        var firstCells = (List<ReactionChipAnim.Chip>) cells.invoke(first);
        assertEquals(1, firstCells.size());
        assertTrue(firstCells.get(0).own());
        ReactionClient.self = () -> other;
        chips.begin(0, 0, 400, 400);
        var changedIdentity = (List<ReactionChipAnim.Chip>) cells.invoke(frame.invoke(chips, row));
        assertFalse(changedIdentity.get(0).own());
        ReactionClient.accept(ReactionWire.snapshot(new ReactionWire.Snapshot(delivery, 2,
                List.of(new ReactionWire.Entry("😀", List.of(member, new ReactionWire.Member(other, "other")))))));
        chips.begin(0, 0, 400, 400);
        var changedCount = (List<ReactionChipAnim.Chip>) cells.invoke(frame.invoke(chips, row));
        assertEquals(2, changedCount.get(0).count());
        assertTrue(changedCount.get(0).own());
        ReactionClient.reset();
        chips.begin(0, 0, 400, 400);
        assertTrue(((List<?>) cells.invoke(frame.invoke(chips, row))).isEmpty());
    }

    @Test void measureProjectionAllocation() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("1".equals(System.getenv("ATOMCHAT_BENCH")));
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        ReactionClient.supported = () -> true;
        ReactionClient.self = UUID::randomUUID;
        List<ChatMessage> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            UUID id = UUID.randomUUID(); rows.add(bind(id, i));
            List<ReactionWire.Entry> entries = new ArrayList<>();
            for (String emoji : List.of("😀", "👍", "❤️", "🎉", "🔥"))
                entries.add(new ReactionWire.Entry(emoji, List.of(new ReactionWire.Member(UUID.randomUUID(), "user"))));
            ReactionClient.accept(ReactionWire.snapshot(new ReactionWire.Snapshot(id, 1, entries)));
        }
        // A stable local identity represents the normal connected-client path.
        UUID self = UUID.randomUUID(); ReactionClient.self = () -> self;
        ReactionChips chips = new ReactionChips(); Method frame = frameMethod();
        long thread = Thread.currentThread().getId();
        for (int n = 0; n < 30; n++) {
            chips.begin(0, 0, 400, 400);
            for (ChatMessage row : rows) frame.invoke(chips, row);
        }
        long before = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
        for (int n = 0; n < 100; n++) {
            chips.begin(0, 0, 400, 400);
            for (ChatMessage row : rows) frame.invoke(chips, row);
        }
        System.out.println("reaction 100 rows x 5 chips x 100 frames: bytes="
                + (bean.getThreadAllocatedBytes(thread) - before) + " nanos=" + (System.nanoTime() - start));
    }
}
