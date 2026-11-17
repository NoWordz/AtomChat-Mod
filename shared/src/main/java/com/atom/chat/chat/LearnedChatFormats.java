package com.atom.chat.chat;

import java.util.*;

/** Exact envelope relationships learned exclusively from structurally attributed messages. */
public final class LearnedChatFormats {
    public record Format(String before, String between, String after, boolean whisper) {
        public Format(String before, String between, String after) { this(before, between, after, false); }
    }
    public record Match(String account, String display, String content, int bodyStart, int bodyEnd, int labelEnd, boolean whisper) {}
    private static final Set<Format> formats = new LinkedHashSet<>();
    private LearnedChatFormats() {}
    public static void observe(String line, String label, String body) { observe(line, label, body, false); }
    public static synchronized void observe(String line, String label, String body, boolean whisper) {
        if (line == null || label == null || body == null || label.isBlank() || body.isBlank() || line.length() > 32768) return;
        int name = line.indexOf(label), content = name < 0 ? -1 : line.indexOf(body, name + label.length());
        if (name < 0 || content < 0 || line.indexOf(label, name + 1) >= 0 || line.indexOf(body, content + 1) >= 0) return;
        String gap = line.substring(name + label.length(), content);
        // A whitespace-only relationship cannot distinguish chat from prose.
        if (gap.isBlank() || gap.length() > 32) return;
        String before = line.substring(0, name), after = line.substring(content + body.length());
        if (before.length() > 96 || after.length() > 96) return;
        formats.add(new Format(before, gap, after, whisper));
        while (formats.size() > 64) formats.remove(formats.iterator().next());
    }
    public static Optional<Match> match(String line) {
        if (line == null || line.length() > 32768) return Optional.empty();
        Match result = null;
        for (Format format : snapshot()) {
            if (!line.startsWith(format.before()) || !line.endsWith(format.after())) continue;
            for (SeenPlayers.Seen player : SeenPlayers.snapshot()) {
                for (String name : names(player)) {
                    String start = format.before() + name + format.between();
                    if (!line.startsWith(start)) continue;
                    int end = line.length() - format.after().length();
                    if (end <= start.length()) continue;
                    Match candidate = new Match(player.profileName(), format.before() + name,
                            line.substring(start.length(), end), start.length(), end, format.before().length() + name.length(), format.whisper());
                    if (result != null && !result.equals(candidate)) return Optional.empty();
                    result = candidate;
                }
            }
        }
        return Optional.ofNullable(result);
    }
    private static List<String> names(SeenPlayers.Seen player) {
        return player.displayName() == null || player.displayName().equals(player.profileName())
                ? List.of(player.profileName()) : List.of(player.profileName(), player.displayName());
    }
    public static synchronized List<Format> snapshot() { return List.copyOf(formats); }
    public static synchronized void restore(List<Format> saved) {
        if (saved == null) return;
        for (Format f : saved) if (formats.size() < 64 && f != null && f.before() != null && f.between() != null
                && f.after() != null && !f.between().isBlank() && f.before().length() <= 96 && f.between().length() <= 32
                && f.after().length() <= 96) formats.add(f);
    }
    public static synchronized void clear() { formats.clear(); }
}
