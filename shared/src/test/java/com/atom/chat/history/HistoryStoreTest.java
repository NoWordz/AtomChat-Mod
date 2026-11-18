package com.atom.chat.history;

import com.atom.chat.history.HistoryStore.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryStoreTest {

    @Test
    void singleplayerKeyUsesWorldName() {
        assertEquals("SP:NCR 生存", HistoryStore.keyFor(true, "NCR 生存", null));
    }

    @Test
    void multiplayerKeyUsesServerListNameAndIgnoresAddress() {
        assertEquals("MP:NCR", HistoryStore.keyFor(false, null, "NCR"));
    }

    @Test
    void fallsBackToGenericWorldKey() {
        assertEquals("world", HistoryStore.keyFor(false, null, null));
        assertEquals("world", HistoryStore.keyFor(true, "  ", null));
    }

    @Test
    void onlySpAndMpKeysAreWorldSpecific() {
        assertTrue(HistoryStore.isWorldSpecific("SP:NCR"));
        assertTrue(HistoryStore.isWorldSpecific("MP:NCR"));
        assertFalse(HistoryStore.isWorldSpecific("world"));
        assertFalse(HistoryStore.isWorldSpecific(null));
    }

    @Test
    void fileNameKeepsUnicodeAndAddsHash(@TempDir Path tmp) {
        HistoryStore.init(tmp.resolve("history"));
        Path f = HistoryStore.file("SP:NCR 生存");
        assertEquals(tmp.resolve("history"), f.getParent());
        String name = f.getFileName().toString();
        assertTrue(name.startsWith("SP_NCR 生存_"), name);
        assertTrue(name.endsWith(".jsonl"), name);
        // 16 hex chars of sha256 + ".jsonl"
        String hash = name.substring("SP_NCR 生存_".length(), name.length() - ".jsonl".length());
        assertEquals(16, hash.length());
        assertEquals(HistoryStore.sha256Short("SP:NCR 生存"), hash);
    }

    @Test
    void sanitizesCharactersThatBreakFileSystems(@TempDir Path tmp) {
        HistoryStore.init(tmp.resolve("history"));
        Path f = HistoryStore.file("MP:a/b:c*d?");
        String name = f.getFileName().toString();
        assertFalse(name.contains("/"));
        assertFalse(name.contains(":"));
        assertFalse(name.contains("*"));
        assertFalse(name.contains("?"));
    }

    @Test
    void roundTripKeepsEveryField() {
        Entry e = new Entry(1700000000000L, true, "uuid-1234", true, false,
                "Alice", "hello", "uuid-1234", "Alice", "Alice", "hi there",
                "{\"text\":\"hi\"}");
        Entry back = HistoryStore.fromLine(HistoryStore.toLine(e));
        assertNotNull(back);
        assertEquals(e.timestamp, back.timestamp);
        assertTrue(back.priv);
        assertEquals("uuid-1234", back.peer);
        assertTrue(back.own);
        assertFalse(back.system);
        assertEquals("Alice", back.quoteName);
        assertEquals("hello", back.quoteText);
        assertEquals("uuid-1234", back.senderUuid);
        assertEquals("Alice", back.senderName);
        assertEquals("Alice", back.profileName);
        assertEquals("hi there", back.contentText);
        assertEquals("{\"text\":\"hi\"}", back.componentJson);
    }

    @Test
    void publicChannelEntryHasNoPeer() {
        Entry e = new Entry(1L, false, null, false, true, null, null, null, null, null, "joined", null);
        Entry back = HistoryStore.fromLine(HistoryStore.toLine(e));
        assertNotNull(back);
        assertFalse(back.priv);
        assertNull(back.peer);
        assertTrue(back.system);
        assertNull(back.componentJson);
        assertEquals("joined", back.contentText);
    }

    @Test
    void optionalPresentationRetainsUnknownIdentityAndRepeatedBodyStyle() {
        var row = new Entry(1, false, null, false, false, null, null, null,
                "Alice · Unknown identity", "", "Alice", "{\"text\":\"Alice: Alice\"}",
                "{\"text\":\"Alice · Unknown identity\"}", "{\"text\":\"Alice\",\"bold\":true}");
        var loaded = HistoryStore.fromLine(HistoryStore.toLine(row));
        assertEquals("", loaded.profileName);
        assertEquals(row.senderComponentJson, loaded.senderComponentJson);
        assertEquals(row.bodyComponentJson, loaded.bodyComponentJson);
        var compact = HistoryStore.fromLine(HistoryStore.toLine(row, false));
        assertEquals(row.componentJson, compact.componentJson);
        assertNull(compact.bodyComponentJson);
    }

    @Test
    void unreadableLinesAreSkippedRatherThanFatal() {
        assertNull(HistoryStore.fromLine(null));
        assertNull(HistoryStore.fromLine("   "));
        assertNull(HistoryStore.fromLine("not json at all"));
        assertNull(HistoryStore.fromLine("{\"noTimestamp\":1}"));
        assertNull(HistoryStore.fromLine("{\"ts\":5,\"comp\":{\"text\":\"x\"}"));
    }
}
