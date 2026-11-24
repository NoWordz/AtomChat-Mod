package com.atom.chat.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rich-text line wrapping and character hit testing.
 *
 * <p>Wrapping operates on UTF-16 code points, never splitting surrogate pairs.
 * Spaces at a wrap boundary are not kept on the previous line and are skipped
 * at the start of the next line; spaces that fit inside a line are preserved.
 */
public final class RichTextLayout {
    /**
     * A single wrapped line of kept visible text.
     *
     * <p>{@code textStart} and {@code textEnd} are absolute UTF-16 indices into
     * the original {@link RichText} string, not local offsets within this line.
     * They refer only to characters actually kept in this line's visible runs;
     * spaces dropped at a wrap boundary are not represented in either adjacent
     * line.
     *
     * @param runs the visible text runs that make up this line
     * @param textStart the absolute UTF-16 index in the original {@link RichText}
     *                  of the first kept visible character on this line
     * @param textEnd the exclusive absolute UTF-16 index in the original
     *                {@link RichText} after the last kept visible character on
     *                this line
     */
    /** Visual pieces retain source indices; item padding never changes copied text. */
    public record Part(String text, RichText.RichRun source, int textStart, int textEnd, float itemWidth) {
        public boolean isItem() { return itemWidth > 0; }
        public float width(TextMeasurer measurer) { return isItem() ? itemWidth : measurer.measure(text, source); }
    }

    public record RichLine(List<RichText.RichRun> runs, int textStart, int textEnd, List<Part> parts) {
        public RichLine(List<RichText.RichRun> runs, int textStart, int textEnd) {
            this(runs, textStart, textEnd, textParts(runs, textStart));
        }

        public RichLine {
            runs = List.copyOf(runs);
            parts = List.copyOf(parts);
        }

        public String getPlainText() {
            StringBuilder sb = new StringBuilder();
            for (RichText.RichRun run : runs) {
                sb.append(run.text());
            }
            return sb.toString();
        }
    }

    private RichTextLayout() {
    }

    private static List<Part> textParts(List<RichText.RichRun> runs, int start) {
        List<Part> parts = new ArrayList<>();
        for (var run : runs) {
            parts.add(new Part(run.text(), run, start, start + run.text().length(), 0));
            start += run.text().length();
        }
        return parts;
    }

    /** Item slots and the first label code point wrap together whenever they fit. */
    public static List<RichLine> wrapWithItems(RichText text, TextMeasurer measurer,
                                               float maxWidth, float itemWidth) {
        if (itemWidth <= 0 || text.runs().stream().noneMatch(RichText.RichRun::hasItemHover)) {
            return wrap(text, measurer, maxWidth);
        }
        List<Part> atoms = new ArrayList<>();
        RichText.RichRun previous = null;
        int offset = 0;
        for (var run : text.runs()) {
            boolean startsItem = run.hasItemHover() && (previous == null || !previous.hasItemHover()
                    || !run.style().getHoverEvent().equals(previous.style().getHoverEvent()));
            int padding = startsItem && run.text().startsWith("   ") ? 3 : 0;
            if (startsItem) atoms.add(new Part("", run, offset, offset + padding, itemWidth));
            for (int i = padding; i < run.text().length();) {
                int end = i + Character.charCount(run.text().codePointAt(i));
                atoms.add(new Part(run.text().substring(i, end), run, offset + i, offset + end, 0));
                i = end;
            }
            offset += run.text().length();
            previous = run;
        }
        List<RichLine> lines = new ArrayList<>();
        List<Part> line = new ArrayList<>();
        float width = 0;
        for (int i = 0; i < atoms.size(); i++) {
            Part part = atoms.get(i);
            if (part.text().equals("\n")) {
                emitItemLine(lines, line, text.runs(), measurer, true);
                line.clear();
                width = 0;
                continue;
            }
            float advance = part.width(measurer);
            float needed = advance;
            if (part.isItem() && i + 1 < atoms.size() && !atoms.get(i + 1).text().equals("\n")) {
                needed += atoms.get(i + 1).width(measurer);
            }
            boolean firstLabelAfterSlot = line.size() == 1 && line.get(0).isItem() && !part.isItem()
                    && line.get(0).textEnd() == part.textStart();
            if (!line.isEmpty() && !firstLabelAfterSlot && width + needed > maxWidth) {
                emitItemLine(lines, line, text.runs(), measurer, true);
                line.clear();
                width = 0;
            }
            if (line.isEmpty() && !lines.isEmpty() && part.text().equals(" ") && measurer.collapsibleSpace(part.source())) continue;
            line.add(part);
            width += advance;
        }
        // Ordinary trailing spaces on the final line retain the existing contract.
        emitItemLine(lines, line, text.runs(), measurer, false);
        return lines;
    }

    private static void emitItemLine(List<RichLine> lines, List<Part> parts,
                                     List<RichText.RichRun> source, TextMeasurer measurer, boolean trim) {
        int count = parts.size();
        while (trim && count > 0 && parts.get(count - 1).text().equals(" ")
                && measurer.collapsibleSpace(parts.get(count-1).source())) count--;
        if (count == 0) return;
        List<Part> merged = new ArrayList<>();
        for (Part part : parts.subList(0, count)) {
            if (!merged.isEmpty() && !part.isItem()) {
                Part last = merged.get(merged.size() - 1);
                if (!last.isItem() && last.source().style().equals(part.source().style())
                        && last.textEnd() == part.textStart()) {
                    merged.set(merged.size() - 1, new Part(last.text() + part.text(), last.source(),
                            last.textStart(), part.textEnd(), 0));
                    continue;
                }
            }
            merged.add(part);
        }
        int start = merged.get(0).textStart();
        int end = merged.get(merged.size() - 1).textEnd();
        lines.add(new RichLine(sliceRuns(source, start, end), start, end, merged));
    }

    public static float width(RichLine line, TextMeasurer measurer) {
        float width = 0;
        for (Part part : line.parts()) width += part.width(measurer);
        return width;
    }

    /** X advance to an original UTF-16 boundary, including any preceding item slot. */
    public static float advanceTo(RichLine line, TextMeasurer measurer, int absoluteIndex) {
        float width = 0;
        for (Part part : line.parts()) {
            if (absoluteIndex <= part.textStart()) break;
            if (part.isItem()) {
                width += part.itemWidth();
            } else {
                int chars = Math.min(part.text().length(), absoluteIndex - part.textStart());
                width += measurer.measure(part.text().substring(0, chars), part.source());
                if (absoluteIndex < part.textEnd()) break;
            }
        }
        return width;
    }

    public static List<RichLine> wrap(RichText text, TextMeasurer measurer, float maxWidth) {
        List<RichLine> lines = new ArrayList<>();
        String full = text.getString();
        if (full.isEmpty()) {
            return lines;
        }

        List<RichText.RichRun> sourceRuns = text.runs();
        int sourceIndex = 0;
        int sourceEnd = sourceRuns.isEmpty() ? 0 : sourceRuns.get(0).text().length();
        int lineStart = 0;
        int lineEnd = 0;
        float lineWidth = 0f;
        int index = 0;

        while (index < full.length()) {
            int codePoint = full.codePointAt(index);
            int codePointEnd = index + Character.charCount(codePoint);

            if (codePoint == '\n') {
                if (lineEnd > lineStart) {
                    emitTrimmedLine(lines, sourceRuns, full, lineStart, lineEnd, measurer);
                }
                lineStart = codePointEnd;
                lineEnd = codePointEnd;
                lineWidth = 0f;
                index = codePointEnd;
                continue;
            }

            String codePointText = full.substring(index, codePointEnd);
            while (sourceIndex + 1 < sourceRuns.size() && index >= sourceEnd) {
                sourceEnd += sourceRuns.get(++sourceIndex).text().length();
            }
            int segmentStart = Math.max(lineStart, sourceEnd - sourceRuns.get(sourceIndex).text().length());
            float codePointWidth = measurer.measure(full.substring(segmentStart, codePointEnd), sourceRuns.get(sourceIndex))
                    - measurer.measure(full.substring(segmentStart, index), sourceRuns.get(sourceIndex));
            boolean space = codePoint == ' ' && measurer.collapsibleSpace(sourceRuns.get(sourceIndex));

            if (space) {
                if (lineEnd == lineStart) {
                    if (!lines.isEmpty()) {
                        // Don't let a wrapped line begin with a discarded space.
                        index = codePointEnd;
                        continue;
                    }
                    // Preserve leading spaces on the first line if they fit.
                    lineEnd = codePointEnd;
                    lineWidth += codePointWidth;
                    index = codePointEnd;
                    continue;
                }
                if (lineWidth + codePointWidth > maxWidth) {
                    emitTrimmedLine(lines, sourceRuns, full, lineStart, lineEnd, measurer);
                    lineStart = codePointEnd;
                    lineEnd = codePointEnd;
                    lineWidth = 0f;
                    index = codePointEnd;
                    continue;
                }
                lineEnd = codePointEnd;
                lineWidth += codePointWidth;
                index = codePointEnd;
            } else {
                if (lineEnd > lineStart && lineWidth + codePointWidth > maxWidth) {
                    emitTrimmedLine(lines, sourceRuns, full, lineStart, lineEnd, measurer);
                    lineStart = index;
                    lineEnd = codePointEnd;
                    lineWidth = measurer.measure(codePointText, sourceRuns.get(sourceIndex));
                } else {
                    if (lineEnd == lineStart) {
                        lineStart = index;
                    }
                    lineEnd = codePointEnd;
                    lineWidth += codePointWidth;
                }
                index = codePointEnd;
            }
        }

        if (lineEnd > lineStart) {
            lines.add(makeLine(sourceRuns, full, lineStart, lineEnd));
        }
        return lines;
    }

    /**
     * Returns the character hit at {@code localX} as an absolute UTF-16 index
     * into the original {@link RichText} string, not a local offset within the
     * line. The returned index is always within {@code [line.textStart(),
     * line.textEnd()]}.
     *
     * @param line the line to hit-test
     * @param measurer measures character widths using the same metrics that
     *                 produced the line layout
     * @param localX the x position relative to this line's left edge
     * @return the absolute UTF-16 index into the original {@link RichText}
     */
    public static int charAt(RichLine line, TextMeasurer measurer, float localX) {
        return hit(line, measurer, localX, false);
    }

    /** Nearest caret boundary, sharing exactly the visual advances used by paint. */
    public static int caretAt(RichLine line, TextMeasurer measurer, float localX) {
        return hit(line, measurer, localX, true);
    }

    private static int hit(RichLine line, TextMeasurer measurer, float localX, boolean caret) {
        float x = 0, nearest = Float.POSITIVE_INFINITY;
        int boundary = line.textStart(), hit = -1;
        for (Part part : line.parts()) {
            int count = part.isItem() ? 1 : part.text().codePointCount(0,part.text().length());
            int index = 0;
            for(int c=0;c<count;c++) {
                int end = part.isItem() ? 0 : index + Character.charCount(part.text().codePointAt(index));
                float next = part.isItem() ? x + part.itemWidth()
                        : x + measurer.measure(part.text().substring(0,end),part.source())
                              - measurer.measure(part.text().substring(0,index),part.source());
                int before=part.isItem()?part.textStart():part.textStart()+index;
                int after=part.isItem()?part.textEnd():part.textStart()+end;
                float d=Math.abs(localX-x);
                if(d<=nearest){nearest=d;boundary=before;}
                d=Math.abs(localX-next);
                if(d<=nearest){nearest=d;boundary=after;}
                // Later positive ink wins overlaps caused by a negative space provider.
                if(next>x && localX>=x && localX<next)hit=before;
                x=next;index=end;
            }
        }
        return caret || hit<0 ? boundary : hit;
    }

    private static RichText.RichRun runAt(List<RichText.RichRun> runs, int index) {
        int offset=0;
        for(var run:runs){offset+=run.text().length();if(index<offset)return run;}
        return runs.get(runs.size()-1);
    }

    private static void emitTrimmedLine(List<RichLine> lines, List<RichText.RichRun> sourceRuns,
                                        String full, int start, int end, TextMeasurer measurer) {
        int visibleEnd = end;
        while (visibleEnd > start && full.charAt(visibleEnd - 1) == ' '
                && measurer.collapsibleSpace(runAt(sourceRuns, visibleEnd - 1))) {
            visibleEnd--;
        }
        if (visibleEnd > start) {
            lines.add(makeLine(sourceRuns, full, start, visibleEnd));
        }
    }

    private static RichLine makeLine(List<RichText.RichRun> sourceRuns, String full,
                                     int start, int end) {
        if (end < start || start < 0 || end > full.length()) {
            throw new IllegalArgumentException("Invalid line range " + start + ".." + end);
        }
        return new RichLine(sliceRuns(sourceRuns, start, end), start, end);
    }

    private static List<RichText.RichRun> sliceRuns(List<RichText.RichRun> sourceRuns,
                                                    int start, int end) {
        List<RichText.RichRun> out = new ArrayList<>();
        int pos = 0;
        for (RichText.RichRun run : sourceRuns) {
            int runEnd = pos + run.text().length();
            if (runEnd > start && pos < end) {
                int sliceStart = Math.max(0, start - pos);
                int sliceEnd = Math.min(run.text().length(), end - pos);
                if (sliceStart < sliceEnd) {
                    out.add(new RichText.RichRun(run.text().substring(sliceStart, sliceEnd), run.style()));
                }
            }
            pos = runEnd;
            if (pos >= end) {
                break;
            }
        }
        return out;
    }
}
