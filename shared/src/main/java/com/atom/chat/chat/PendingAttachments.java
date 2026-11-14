package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import java.util.ArrayList;
import java.util.List;

/** Completed uploads survive screen changes without borrowing another conversation's composer. */
public final class PendingAttachments {
    private static final int MAX_PENDING = 16;
    private final List<Ticket> tickets = new ArrayList<>();

    public synchronized Ticket reserve(NavPage page) {
        String key = key(page);
        if (key == null || tickets.size() >= MAX_PENDING) return null;
        Ticket ticket = new Ticket(key);
        tickets.add(ticket);
        return ticket;
    }

    public synchronized int drain(NavPage page, DraftStore.ComposerTextPort composer) {
        if (composer == null) return 0;
        String key = key(page);
        int added = 0;
        for (var it = tickets.iterator(); it.hasNext();) {
            Ticket ticket = it.next();
            if (!ticket.key.equals(key) || ticket.code == null) continue;
            String current = composer.get();
            String next = current + (current.isEmpty() ? "" : " ") + ticket.code;
            if (next.length() > 256) break;
            composer.set(next);
            it.remove();
            added++;
        }
        return added;
    }

    public synchronized void clear() { tickets.clear(); }
    public synchronized int size() { return tickets.size(); }

    private static String key(NavPage page) {
        if (page == null) return null;
        if (page.page() == AppPage.WORLD_CHAT) return "world";
        return page.page() == AppPage.PRIVATE_CHAT ? "private:" + page.target().key() : null;
    }

    public final class Ticket {
        private final String key;
        private String code;
        private Ticket(String key) { this.key = key; }
        public boolean complete(String code) {
            synchronized (PendingAttachments.this) {
                if (!tickets.contains(this) || this.code != null) return false;
                if (code == null || code.length() > 256) { tickets.remove(this); return false; }
                this.code = code;
                return true;
            }
        }
        public void cancel() { synchronized (PendingAttachments.this) { tickets.remove(this); } }
        public boolean pending() { synchronized (PendingAttachments.this) { return tickets.contains(this); } }
    }
}
