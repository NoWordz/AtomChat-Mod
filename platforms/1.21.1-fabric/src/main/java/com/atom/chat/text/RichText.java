package com.atom.chat.text;

import com.atom.chat.chat.ImageCode;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable flat representation of a Minecraft {@link Text} tree.
 *
 * <p>The tree is flattened into runs of plain text plus their effective styles. A
 * separate root style is kept because it can be useful when rebuilding/rendering a
 * sliced piece whose selected content has no runs of its own.
 */
public final class RichText {
    public record RichRun(String text, Style style) {}

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"']+");

    private final List<RichRun> runs;
    private final Style rootStyle;

    private RichText(List<RichRun> runs, Style rootStyle) {
        this.runs = List.copyOf(runs);
        this.rootStyle = rootStyle;
    }

    public static RichText of(Text text) {
        List<RichRun> out = new ArrayList<>();
        // getWithStyle visits effective-style string segments, including the root
        // style on every child; literal section-sign codes inside a segment are
        // parsed separately below, which is what keeps an unrecognised or a
        // trailing one instead of swallowing it.
        for (Text part : text.getWithStyle(Style.EMPTY)) {
            appendLegacyParsed(out, part.getString(), part.getStyle(), part.getStyle());
        }
        return new RichText(mergeRuns(out), text.getStyle());
    }

    private static void appendLegacyParsed(List<RichRun> out, String raw, Style baseStyle, Style style) {
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '§' && i + 1 < raw.length()) {
                char code = raw.charAt(i + 1);
                if (current.length() > 0) {
                    out.add(new RichRun(current.toString(), style));
                    current.setLength(0);
                }
                Formatting cf = Formatting.byCode(code);
                if (cf == null) {
                    // Unknown code: preserve it literally instead of swallowing it.
                    current.append(ch).append(code);
                } else {
                    style = applySectionCode(style, baseStyle, cf);
                }
                i++;
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            out.add(new RichRun(current.toString(), style));
        }
    }

    private static Style applySectionCode(Style style, Style baseStyle, Formatting cf) {
        return switch (cf) {
            case RESET -> baseStyle;
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderline(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style.withColor(cf);
        };
    }

    /**
     * Joins neighbours that carry the same effective style. Flattening a tree and
     * re-reading its section codes both cut a run wherever a code appears, so
     * without this the run list would describe how the text was assembled instead
     * of how it looks.
     */
    private static List<RichRun> mergeRuns(List<RichRun> runs) {
        if (runs.size() <= 1) {
            return runs;
        }
        List<RichRun> merged = new ArrayList<>();
        for (RichRun run : runs) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).style().equals(run.style())) {
                RichRun last = merged.remove(merged.size() - 1);
                merged.add(new RichRun(last.text() + run.text(), last.style()));
            } else {
                merged.add(run);
            }
        }
        return merged;
    }

    public static RichText literal(String text) {
        return of(Text.literal(text));
    }

    public static RichText empty() {
        return new RichText(List.of(), Style.EMPTY);
    }

    public String getString() {
        StringBuilder sb = new StringBuilder();
        for (RichRun run : runs) {
            sb.append(run.text());
        }
        return sb.toString();
    }

    public List<RichRun> runs() {
        return runs;
    }

    public boolean isEmpty() {
        return runs.isEmpty();
    }

    /**
     * Rebuilds a Minecraft {@link Text} from this flat run list, preserving each
     * run's effective style. Useful when a rewrite has to splice original styled
     * slices back together with new placeholder runs.
     */
    public Text toText() {
        MutableText out = Text.literal("").setStyle(rootStyle);
        for (RichRun run : runs) {
            out.append(Text.literal(run.text()).setStyle(run.style()));
        }
        return out;
    }

    public RichText slice(int from, int to) {
        String full = getString();
        int start = Math.max(0, Math.min(from, full.length()));
        int end = Math.max(0, Math.min(to, full.length()));
        if (start > end) {
            return new RichText(List.of(), rootStyle);
        }
        start = adjustStartBoundary(full, start);
        end = adjustEndBoundary(full, end);
        if (start >= end) {
            return new RichText(List.of(), rootStyle);
        }

        List<RichRun> out = new ArrayList<>();
        int pos = 0;
        for (RichRun run : runs) {
            int runEnd = pos + run.text().length();
            if (runEnd > start && pos < end) {
                int s = Math.max(0, start - pos);
                int e = Math.min(run.text().length(), end - pos);
                if (s < e) {
                    out.add(new RichRun(run.text().substring(s, e), run.style()));
                }
            }
            pos = runEnd;
            if (pos >= end) {
                break;
            }
        }
        return new RichText(out, rootStyle);
    }

    /**
     * Returns a copy with {@code [from, to)} replaced by {@code replacement}.
     * Indices are clamped and adjusted off surrogate pairs exactly like
     * {@link #slice}. The root style of this text is kept.
     */
    public RichText splice(int from, int to, RichText replacement) {
        String full = getString();
        int start = Math.max(0, Math.min(from, full.length()));
        int end = Math.max(0, Math.min(to, full.length()));
        start = adjustStartBoundary(full, start);
        end = adjustEndBoundary(full, end);
        if (start > end) {
            int swap = start;
            start = end;
            end = swap;
        }
        List<RichRun> out = new ArrayList<>(slice(0, start).runs());
        if (replacement != null) {
            out.addAll(replacement.runs());
        }
        out.addAll(slice(end, full.length()).runs());
        return new RichText(out, rootStyle);
    }

    /**
     * Returns a copy with click/hover interactions and underlines removed, while
     * keeping colours and other visible styling. Used for private-chat sender
     * names, where vanilla /msg click actions would otherwise turn the name into
     * a misleading link.
     */
    public RichText stripInteractions() {
        List<RichRun> out = new ArrayList<>();
        for (RichRun run : runs) {
            Style style = run.style();
            Style clean = style;
            if (style.getClickEvent() != null) {
                clean = clean.withClickEvent(null);
            }
            if (style.getHoverEvent() != null) {
                clean = clean.withHoverEvent(null);
            }
            if (style.isUnderlined()) {
                clean = clean.withUnderline(false);
            }
            out.add(new RichRun(run.text(), clean));
        }
        return new RichText(out, rootStyle);
    }

    public RichText linkifyUrls() {
        List<RichRun> out = new ArrayList<>();
        for (RichRun run : runs) {
            if (run.style().getClickEvent() != null) {
                out.add(run);
                continue;
            }

            // An image code's tail looks exactly like the rest of a URL
            // (",name=…,w=…,h=…]]"), so linking it produced a bogus OPEN_URL that
            // threw URISyntaxException when clicked. Stay off the code's own range.
            List<int[]> codes = ImageCode.ranges(run.text());
            Matcher matcher = URL_PATTERN.matcher(run.text());
            int last = 0;
            while (matcher.find()) {
                if (ImageCode.covers(codes, matcher.start())) {
                    continue;
                }
                if (matcher.start() > last) {
                    out.add(new RichRun(run.text().substring(last, matcher.start()), run.style()));
                }
                String url = matcher.group();
                Style urlStyle = run.style()
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                out.add(new RichRun(url, urlStyle));
                last = matcher.end();
            }
            if (last < run.text().length()) {
                out.add(new RichRun(run.text().substring(last), run.style()));
            }
        }
        return new RichText(out, rootStyle);
    }

    /** True when any run (or the root style) carries an explicit colour. */
    public boolean hasColor() {
        if (rootStyle.getColor() != null) {
            return true;
        }
        for (RichRun run : runs) {
            if (run.style().getColor() != null) {
                return true;
            }
        }
        return false;
    }

    /** Returns a copy with every run's style passed through {@code mapper}. */
    public RichText mapStyles(java.util.function.UnaryOperator<Style> mapper) {
        List<RichRun> out = new ArrayList<>();
        for (RichRun run : runs) {
            out.add(new RichRun(run.text(), mapper.apply(run.style())));
        }
        return new RichText(out, rootStyle);
    }

    /** Run-wise concatenation; the root style comes from {@code first}. */
    public static RichText concat(RichText first, RichText second) {
        List<RichRun> out = new ArrayList<>(first.runs);
        out.addAll(second.runs);
        return new RichText(out, first.rootStyle);
    }

    public Style rootStyle() {
        return rootStyle;
    }

    /**
     * Moves a start index left so it never points between the two UTF-16 code
     * units of a surrogate pair.
     */
    private static int adjustStartBoundary(String text, int index) {
        if (index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) {
            return index - 1;
        }
        return index;
    }

    /**
     * Moves an end index right so it never points between the two UTF-16 code
     * units of a surrogate pair.
     */
    private static int adjustEndBoundary(String text, int index) {
        if (index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) {
            return index + 1;
        }
        return index;
    }
}
