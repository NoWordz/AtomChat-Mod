package com.atom.chat.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryFilesTest {
    @TempDir Path dir;

    @Test void readsNewestFiveHundredValidEntriesAndSkipsBrokenTail() throws Exception {
        Path file = dir.resolve("history.jsonl");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 650; i++) lines.add(HistoryStore.toLine(entry(i)));
        lines.add("{\"ts\":650,");
        Files.write(file, lines, StandardCharsets.UTF_8);
        var loaded = HistoryFiles.read(file);
        assertTrue(loaded.exists());
        assertEquals(500, loaded.entries().size());
        assertEquals(150, loaded.entries().get(0).timestamp);
        assertEquals(649, loaded.entries().get(499).timestamp);
        assertThrows(UnsupportedOperationException.class, () -> loaded.entries().clear());
    }

    @Test void boundsBytesAndLineLengthWithoutLosingValidTailOrUnicode() throws Exception {
        Path file = dir.resolve("huge.jsonl");
        try (var out = Files.newOutputStream(file)) {
            out.write("x".repeat(5 * 1024 * 1024).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.write(("{\"ts\":1,\"content\":\"" + "a".repeat(70 * 1024) + "\"}\n").getBytes(StandardCharsets.UTF_8));
            out.write("{\"ts\":2,\"content\":\"中文尾行😀\"}\n{broken".getBytes(StandardCharsets.UTF_8));
        }
        var loaded = HistoryFiles.read(file);
        assertEquals(1, loaded.entries().size());
        assertEquals("中文尾行😀", loaded.entries().get(0).contentText);
        assertEquals(2, loaded.entries().get(0).timestamp);
    }

    @Test void missingEmptyCrLfAndUnterminatedValidLastLineRemainReadable() throws Exception {
        Path file = dir.resolve("file.jsonl");
        assertFalse(HistoryFiles.read(file).exists());
        Files.writeString(file, "");
        assertTrue(HistoryFiles.read(file).exists());
        assertTrue(HistoryFiles.read(file).entries().isEmpty());
        Files.writeString(file, "{\"ts\":1}\r\n\r\n{\"ts\":2}", StandardCharsets.UTF_8);
        assertEquals(List.of(1L, 2L), HistoryFiles.read(file).entries().stream().map(e -> e.timestamp).toList());
    }

    @Test void invalidatedWriteCannotReplaceExistingFileAndDoesNotLeaveTemporaryFiles() throws Exception {
        Path file = dir.resolve("history.jsonl");
        Files.writeString(file, "original");
        assertFalse(HistoryFiles.write(file, List.of(entry(1)), () -> false));
        assertEquals("original", Files.readString(file));
        try (var files = Files.list(dir)) { assertEquals(1, files.count()); }
    }

    @Test void writeKeepsNewestBoundedTailAndRoundTripsImmutableDtos() throws Exception {
        Path file = dir.resolve("history.jsonl");
        List<HistoryStore.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 600; i++) entries.add(entry(i));
        assertTrue(HistoryFiles.write(file, entries, () -> true));
        var loaded = HistoryFiles.read(file).entries();
        assertEquals(500, loaded.size());
        assertEquals(100, loaded.get(0).timestamp);
        assertEquals(599, loaded.get(499).timestamp);
        assertTrue(Files.size(file) <= 4L * 1024 * 1024);
    }

    @Test void invalidUtf8TailIsSkippedAndLargeWritesStayWithinTheReadWindow() throws Exception {
        Path file = dir.resolve("history.jsonl");
        byte[] valid = "{\"ts\":1}\n".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = java.util.Arrays.copyOf(valid, valid.length + 1);
        bytes[bytes.length - 1] = (byte) 0xc3;
        Files.write(file, bytes);
        assertEquals(List.of(1L), HistoryFiles.read(file).entries().stream().map(e -> e.timestamp).toList());
        List<HistoryStore.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 500; i++) entries.add(new HistoryStore.Entry(i, false, null, false,
                false, null, null, null, null, null, "x".repeat(60 * 1024), null));
        assertTrue(HistoryFiles.write(file, entries, () -> true));
        assertTrue(Files.size(file) <= 4L * 1024 * 1024);
        var loaded = HistoryFiles.read(file).entries();
        assertTrue(loaded.size() < 100);
        assertEquals(499, loaded.get(loaded.size() - 1).timestamp);
    }

    static HistoryStore.Entry entry(long time) {
        return new HistoryStore.Entry(time, false, null, false, false, null, null,
                null, "sender", "profile", "line " + time, null);
    }
}
