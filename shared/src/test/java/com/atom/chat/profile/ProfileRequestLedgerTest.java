package com.atom.chat.profile;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class ProfileRequestLedgerTest {
    @Test void previousConnectionReplyIsRejectedAndIdIsNotReused() {
        var ledger = new ProfileRequestLedger(() -> 0);
        UUID player = UUID.randomUUID();
        long old = ledger.issue(player); ledger.clear(); long current = ledger.issue(player);
        assertNotEquals(old, current);
        assertFalse(ledger.accept(old, player));
        assertTrue(ledger.accept(current, player));
        assertFalse(ledger.accept(current, player));
    }
    @Test void wrongSubjectDoesNotConsumeValidRequest() {
        var ledger = new ProfileRequestLedger(() -> 0);
        UUID player = UUID.randomUUID(); long token = ledger.issue(player);
        assertFalse(ledger.accept(token, UUID.randomUUID()));
        assertTrue(ledger.accept(token, player));
    }
    @Test void expiredAndEvictedRequestsCannotPopulateCache() {
        var time = new AtomicLong(); var ledger = new ProfileRequestLedger(time::get);
        UUID player = UUID.randomUUID(); long old = ledger.issue(player);
        time.set(15000); ledger.expire();
        assertFalse(ledger.accept(old, player));
        long first = ledger.issue(player), latest = first;
        for (int i = 0; i < 513; i++) latest = ledger.issue(player);
        assertFalse(ledger.accept(first, player));
        assertTrue(ledger.accept(latest, player));
    }
}
