package com.atom.chat.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ServerConfigDraftTest {
    private ServerConfigValues values(String name) {
        return new ServerConfigValues(true, true, 1024, 64, 64, 7, 1000, 20, 8, name, List.of("hello"));
    }
    @Test void chipsShareOneDraftAndSuccessRequiresAuthoritativeSnapshot() {
        var draft = new ServerConfigDraft(4, values("old"));
        draft.text("packName", " new ");
        assertTrue(draft.dirty());
        assertEquals(" new ", draft.values().packName());
        assertTrue(draft.beginSave(100));
        draft.result(true, "");
        assertTrue(draft.saving());
        assertTrue(draft.dirty());
        draft.confirm(5, values("new"));
        assertFalse(draft.saving());
        assertFalse(draft.dirty());
        assertEquals(5, draft.version());
        assertEquals("new", draft.text("packName"));
        draft.text("packName", "next");
        assertTrue(draft.beginSave(200));
    }
    @Test void failureAndInvalidInputKeepTheDraft() {
        var draft = new ServerConfigDraft(1, values("old"));
        draft.text("maxFileKb", "oops");
        assertNull(draft.values());
        assertFalse(draft.beginSave(100));
        draft.text("maxFileKb", "12");
        assertTrue(draft.beginSave(100));
        draft.result(false, "conflict");
        assertFalse(draft.saving());
        assertEquals("12", draft.text("maxFileKb"));
        assertEquals("conflict", draft.error());
        assertTrue(draft.dirty());
    }
    @Test void editsCannotChangeTheInFlightSubmission() {
        var draft = new ServerConfigDraft(1, values("old"));
        draft.text("packName", "new");
        draft.beginSave(100);
        draft.text("packName", "later");
        assertEquals("new", draft.text("packName"));
        draft.timeout(10_101);
        assertTrue(draft.saving()); // A bounded chunked import can take longer than ten seconds.
        draft.timeout(120_101);
        assertFalse(draft.saving());
        assertTrue(draft.dirty());
        assertEquals("timeout", draft.error());
    }
}
