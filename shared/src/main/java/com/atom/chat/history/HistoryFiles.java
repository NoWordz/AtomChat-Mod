package com.atom.chat.history;

import com.atom.chat.history.HistoryStore.Entry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Bounded JSONL disk work. Minecraft components/registries never enter this class. */
public final class HistoryFiles {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_LINE_BYTES = 64 * 1024;

    private HistoryFiles() {}

    public record Loaded(boolean exists, List<Entry> entries) {
        public Loaded { entries = List.copyOf(entries); }
    }

    /** Seek directly to a bounded tail, then parse backwards until 500 valid entries are found. */
    public static Loaded read(Path file) throws IOException {
        byte[] bytes;
        long start;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            start = Math.max(0, size - MAX_BYTES);
            bytes = new byte[(int) (size - start)];
            channel.position(start);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new IOException("History changed while reading");
            }
        } catch (NoSuchFileException missing) {
            return new Loaded(false, List.of());
        }
        List<Entry> newestFirst = new ArrayList<>();
        int end = bytes.length;
        for (int i = bytes.length - 1; i >= -1 && newestFirst.size() < HistorySnapshot.MAX_ENTRIES; i--) {
            if (i >= 0 && bytes[i] != '\n') continue;
            // The first fragment may begin inside a UTF-8 sequence or an oversized JSON object.
            if (i == -1 && start > 0) break;
            int lineStart = i + 1;
            int lineEnd = end;
            if (lineEnd > lineStart && bytes[lineEnd - 1] == '\r') lineEnd--;
            int length = lineEnd - lineStart;
            if (length > 0 && length <= MAX_LINE_BYTES) {
                try {
                    String line = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, lineStart, length)).toString();
                    Entry entry = HistoryStore.fromLine(line);
                    if (entry != null) newestFirst.add(entry);
                } catch (CharacterCodingException malformed) {
                    // An incomplete UTF-8 tail is treated like a damaged JSONL row.
                }
            }
            end = i;
        }
        Collections.reverse(newestFirst);
        return new Loaded(true, newestFirst);
    }

    /** Serialize only the selected tail, keeping output bounded and replacing the old file atomically. */
    public static boolean write(Path file, List<Entry> snapshot, BooleanSupplier valid) throws IOException {
        if (!valid.getAsBoolean()) return false;
        ArrayDeque<byte[]> lines = new ArrayDeque<>();
        int retainedBytes = 0;
        for (int i = Math.max(0, snapshot.size() - HistorySnapshot.MAX_ENTRIES); i < snapshot.size(); i++) {
            byte[] line;
            try {
                line = HistoryStore.toLine(snapshot.get(i)).getBytes(StandardCharsets.UTF_8);
            } catch (RuntimeException malformed) {
                continue;
            }
            if (line.length > MAX_LINE_BYTES) continue;
            while (retainedBytes + line.length + 1 > MAX_BYTES) retainedBytes -= lines.removeFirst().length + 1;
            lines.addLast(line);
            retainedBytes += line.length + 1;
        }
        if (!valid.getAsBoolean()) return false;
        Files.createDirectories(file.getParent());
        Path staged = Files.createTempFile(file.getParent(), "history-", ".tmp");
        try {
            try (var output = Files.newOutputStream(staged)) {
                for (byte[] line : lines) {
                    output.write(line);
                    output.write('\n');
                }
            }
            if (!valid.getAsBoolean()) return false;
            try {
                Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
