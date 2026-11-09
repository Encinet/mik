package org.encinet.mik.shared.menu.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;

/** Literal styled runs, indexed by code point. Nonliteral components remain atomic document blocks. */
final class UiDocumentText {
    private record Run(int from, int to, int utf16, String text, Style style) { }
    private record Visit(Component node, Style inherited, int depth) { }
    private final List<Run> runs;
    private final String plain;
    private final int[] offsets;

    private UiDocumentText(List<Run> runs, String plain) {
        this.runs = List.copyOf(runs); this.plain = plain;
        int count = plain.codePointCount(0, plain.length());
        offsets = new int[count + 1];
        for (int index = 0, offset = 0; index < count; index++) {
            offsets[index] = offset;
            offset += Character.charCount(plain.codePointAt(offset));
        }
        offsets[count] = plain.length();
    }

    static Optional<UiDocumentText> literal(Component content) {
        var pending = new ArrayDeque<Visit>();
        pending.add(new Visit(content, Style.empty(), 0));
        var runs = new ArrayList<Run>();
        var plain = new StringBuilder();
        int nodes = 0, points = 0;
        while (!pending.isEmpty()) {
            var visit = pending.removeLast();
            if (++nodes > 4096 || visit.depth() > 64) throw new IllegalArgumentException("Document text tree budget exceeded");
            if (!(visit.node() instanceof TextComponent text)) return Optional.empty();
            Style style = text.style().merge(visit.inherited(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
            int count = text.content().codePointCount(0, text.content().length());
            if (points + count > UiDocument.MAX_TEXT_CODE_POINTS) throw new IllegalArgumentException("Document text budget exceeded");
            if (count > 0) runs.add(new Run(points, points + count, plain.length(), text.content(), style));
            points += count; plain.append(text.content());
            for (int index = text.children().size() - 1; index >= 0; index--)
                pending.add(new Visit(text.children().get(index), style, visit.depth() + 1));
        }
        return Optional.of(new UiDocumentText(runs, plain.toString()));
    }

    int length() { return offsets.length - 1; }
    int skipWhitespace(int from) {
        while (from < length() && Character.isWhitespace(plain.codePointAt(offsets[from]))) from++;
        return from;
    }
    List<Integer> graphemeEnds() {
        var ends = new ArrayList<Integer>();
        var matcher = java.util.regex.Pattern.compile("\\X").matcher(plain);
        while (matcher.find()) ends.add(java.util.Arrays.binarySearch(offsets, matcher.end()));
        return List.copyOf(ends);
    }

    Component slice(int from, int to) {
        if (from < 0 || to < from || to > length()) throw new IllegalArgumentException("Invalid document text range");
        var result = Component.text();
        int low = 0, high = runs.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (runs.get(middle).to() <= from) low = middle + 1; else high = middle;
        }
        for (int index = low; index < runs.size(); index++) {
            Run run = runs.get(index);
            if (run.from() >= to) break;
            int start = offsets[Math.max(from, run.from())] - run.utf16();
            int end = offsets[Math.min(to, run.to())] - run.utf16();
            result.append(Component.text(run.text().substring(start, end)).style(run.style()));
        }
        return result.build();
    }

    /** A page boundary carries its consumed separator; it must not render an extra blank line past the page. */
    Component page(int from, int to) {
        int end = to;
        while (end > from && Character.isWhitespace(plain.codePointAt(offsets[end - 1]))) end--;
        return slice(from, end);
    }
}
