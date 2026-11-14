package com.atom.chat.history;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class HistorySnapshotTest {
    @Test void selectsBeforeConvertingAndNeverVisitsUnneededConversations() {
        var publicMessages = IntStream.range(0, 500).boxed().toList();
        AtomicInteger groups = new AtomicInteger(), converted = new AtomicInteger();
        List<String> result = HistorySnapshot.capture(publicMessages, List.of("a", "b", "c"), partner -> {
            groups.incrementAndGet();
            return IntStream.range(0, 500).boxed().toList();
        }, (partner, message) -> {
            converted.incrementAndGet();
            return partner + ":" + message;
        });
        assertEquals(1, groups.get());
        assertEquals(500, converted.get());
        assertEquals("c:0", result.get(0));
        assertEquals("c:499", result.get(499));
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test void preservesExistingPublicThenPrivateOrderWhileSelectingTheCombinedTail() {
        List<Integer> publicMessages = IntStream.range(0, 450).boxed().toList();
        var result = HistorySnapshot.capture(publicMessages, List.of("a", "b"),
                partner -> IntStream.range(0, 50).boxed().toList(),
                (partner, message) -> (partner == null ? "public" : partner) + ":" + message);
        assertEquals(500, result.size());
        assertEquals("public:50", result.get(0));
        assertEquals("public:449", result.get(399));
        assertEquals("a:0", result.get(400));
        assertEquals("b:49", result.get(499));
    }

    @Test void loadedBeforeLiveMergeKeepsLiveNewestUnderTheCap() {
        var old = IntStream.range(0, 499).boxed().toList();
        var result = HistorySnapshot.prepend(old, List.of(900, 901));
        assertEquals(500, result.size());
        assertEquals(1, result.get(0));
        assertEquals(List.of(900, 901), result.subList(498, 500));
        assertEquals(List.of(1, 2), HistorySnapshot.prepend(List.of(), List.of(1, 2)));
    }
}
