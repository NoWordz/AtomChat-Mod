package com.atom.chat.tools;

import com.atom.chat.chat.ChatMessage;
import java.util.List;
import java.util.Objects;

/** Borrows the actual composer, like quick-phrase editing; the host supplies only the current page. */
public final class ComposerSearch {
    public interface Host {
        String composerText();
        int composerCaret();
        void setComposerText(String text);
        void moveComposerCaret(int caret);
        void focusComposer();
        Object searchScope();
        List<ChatMessage> searchMessages();
        boolean revealSearchMessage(ChatMessage message);
        void highlightSearchMessage(ChatMessage message);
    }
    private final Host host;
    private boolean active;
    private String parked = "", query;
    private int parkedCaret, index = -1;
    private Object scope;
    private List<ChatMessage> source = List.of(), matches = List.of();
    public ComposerSearch(Host host) { this.host = host; }
    public boolean active() { return active; }
    public com.atom.chat.chat.DraftStore.ComposerTextPort draftPort(com.atom.chat.chat.DraftStore.ComposerTextPort live) {
        if (!active) return live;
        return new com.atom.chat.chat.DraftStore.ComposerTextPort() {
            public String get() { return parked; }
            public void set(String text) { parked = text; }
            public void moveCursorToStart() { parkedCaret = 0; }
        };
    }
    public int count() { return matches.size(); }
    public int position() { return index + 1; }
    /** An upload started before the borrow completes against the parked draft, never the query. */
    public boolean appendToParkedDraft(String text) {
        if (!active) return false;
        if (text != null && parked.length() + text.length() <= 256) parked += text;
        return true;
    }
    public void open() {
        if (active) { close(); return; }
        parked = host.composerText(); parkedCaret = host.composerCaret(); scope = host.searchScope();
        active = true; query = null; source = List.of(); matches = List.of(); index = -1;
        host.setComposerText(""); host.focusComposer(); host.moveComposerCaret(0);
        update();
    }
    public void close() {
        if (!active) return;
        active = false;
        host.setComposerText(parked); host.focusComposer(); host.moveComposerCaret(parkedCaret);
        host.highlightSearchMessage(null); source = List.of(); matches = List.of(); index = -1;
    }
    public void update() {
        if (!active) return;
        if (!Objects.equals(scope, host.searchScope())) { close(); return; }
        String text = host.composerText();
        List<ChatMessage> current = host.searchMessages();
        if (!Objects.equals(query, text) || !source.equals(current)) {
            ChatMessage previous = index >= 0 && index < matches.size() ? matches.get(index) : null;
            boolean queryChanged = !Objects.equals(query, text);
            query = text; source = List.copyOf(current);
            var rows = source.stream().map(m -> new HistorySearch.Row<>(m, null, m.getTimestamp(), m.getSenderName(), m.getContentText())).toList();
            matches = HistorySearch.find(rows, text, HistorySearch.Channel.ALL).stream().map(HistorySearch.Row::message).toList();
            index = matches.isEmpty() ? -1 : queryChanged ? 0 : Math.max(0, matches.indexOf(previous));
            if (index >= 0) host.revealSearchMessage(matches.get(index));
        }
        host.highlightSearchMessage(index >= 0 ? matches.get(index) : null);
    }
    public void move(int direction) {
        update();
        if (!active || matches.isEmpty()) return;
        index = Math.floorMod(index + direction, matches.size());
        if (!host.revealSearchMessage(matches.get(index))) { source = List.of(); update(); }
        else host.highlightSearchMessage(matches.get(index));
    }
}
