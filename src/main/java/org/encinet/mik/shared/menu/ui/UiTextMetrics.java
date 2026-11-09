package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Default-font advances and client word wrapping, independent of world units and UI roles. */
public final class UiTextMetrics {
    // Vanilla PlayerGlyphProvider and AtlasGlyphProvider render an object as one eight-pixel glyph.
    private static final ComponentFlattener FLATTENER = ComponentFlattener.basic().toBuilder()
            .mapper(ObjectComponent.class, _ -> "\uFFFC").build();
    private static final java.util.regex.Pattern GRAPHEMES = java.util.regex.Pattern.compile("\\X");
    private UiTextMetrics() { }

    public static Component fitSingleLine(Component content, int lineWidth) {
        Component single = UiText.abbreviate(content, Integer.MAX_VALUE, 1);
        return fitLines(single, lineWidth, 1);
    }

    /** Keeps complete content when it fits, otherwise fits a styled prefix and ellipsis. */
    public static Component fitLines(Component content, int lineWidth, int maximumLines) {
        if (maximumLines < 1) throw new IllegalArgumentException("Text preview needs at least one line");
        Extent natural = measure(content, lineWidth);
        if (natural.lines() <= maximumLines && natural.widthPixels() <= lineWidth) return content;
        String plain = PlainTextComponentSerializer.plainText().serialize(content);
        int low = 1, high = plain.codePointCount(0, plain.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            Component candidate = UiText.abbreviate(content, middle);
            Extent extent = measure(candidate, lineWidth);
            if (extent.lines() <= maximumLines && extent.widthPixels() <= lineWidth) low = middle;
            else high = middle - 1;
        }
        return low == 1 ? Component.text("…") : UiText.abbreviate(content, low);
    }

    public static Extent measure(Component component, int lineWidth) {
        if (lineWidth < 1) throw new IllegalArgumentException("Text line width must be positive");
        return wrap(glyphs(component), lineWidth, null);
    }

    /** Consumed code-point ends for complete reading pages, keeping Unicode graphemes together. */
    public static List<Integer> pageEnds(Component component, int lineWidth, int maximumLines) {
        return readingLines(component, lineWidth).pageEnds(maximumLines);
    }

    /** Width-dependent source lines can be reused when only the page height changes. */
    public record ReadingLines(List<Integer> ends) {
        public ReadingLines {
            ends = List.copyOf(ends);
            if (ends.isEmpty()) throw new IllegalArgumentException("Reading lines need a source end");
            int previous = 0;
            for (int end : ends) {
                if (end < previous) throw new IllegalArgumentException("Reading line ends must be ordered");
                previous = end;
            }
        }
        public List<Integer> pageEnds(int maximumLines) {
            if (maximumLines < 1) throw new IllegalArgumentException("Document needs a positive page height");
            var pages = new ArrayList<Integer>();
            for (int index = 0; index < ends.size(); index++) if ((index + 1) % maximumLines == 0
                    && (pages.isEmpty() || !pages.getLast().equals(ends.get(index)))) pages.add(ends.get(index));
            if (pages.isEmpty() || !pages.getLast().equals(ends.getLast())) pages.add(ends.getLast());
            return List.copyOf(pages);
        }
    }

    public static ReadingLines readingLines(Component component, int lineWidth) {
        if (lineWidth < 1) throw new IllegalArgumentException("Document needs a positive line width");
        var raw = glyphs(component);
        if (raw.isEmpty()) return new ReadingLines(List.of(0));
        var plain = new StringBuilder();
        raw.forEach(glyph -> plain.appendCodePoint(glyph.point()));
        var matcher = GRAPHEMES.matcher(plain);
        var clusters = new ArrayList<Glyph>();
        int index = 0;
        while (matcher.find()) {
            int count = plain.codePointCount(matcher.start(), matcher.end());
            int point = raw.get(index).point(), advance = 0;
            for (int offset = 0; offset < count; offset++) {
                var glyph = raw.get(index++);
                advance += glyph.advance();
                if (glyph.point() == '\n') point = '\n';
            }
            clusters.add(new Glyph(point, advance, count));
        }
        var ends = new ArrayList<Integer>();
        wrap(clusters, lineWidth, ends::add);
        return new ReadingLines(ends);
    }

    private static List<Glyph> glyphs(Component component) {
        var glyphs = new ArrayList<Glyph>();
        FLATTENER.flatten(Objects.requireNonNull(component, "component"), new FlattenerListener() {
            private final Deque<Boolean> bold = new ArrayDeque<>();
            @Override public void pushStyle(Style style) {
                var state = style.decoration(TextDecoration.BOLD);
                bold.push(state == TextDecoration.State.TRUE
                        || state == TextDecoration.State.NOT_SET && !bold.isEmpty() && bold.peek());
            }
            @Override public void popStyle(Style style) { bold.pop(); }
            @Override public void component(String text) {
                text.codePoints().forEach(point -> glyphs.add(new Glyph(point,
                        (point == '\uFFFC' ? 8 : UiFontMetrics.advance(point))
                                + (!bold.isEmpty() && bold.peek() && point != '\n' ? 1 : 0))));
            }
        });
        return glyphs;
    }

    private static Extent wrap(List<Glyph> glyphs, int lineWidth, java.util.function.IntConsumer lineEnd) {
        int[] points = lineEnd == null ? null : new int[glyphs.size() + 1];
        if (points != null) for (int index = 0; index < glyphs.size(); index++) points[index + 1] = points[index] + glyphs.get(index).count();
        int start = 0, maximum = 1, lines = 0;
        boolean wrapped = false;
        while (start < glyphs.size()) {
            int index = start, width = 0, lastSpace = -1, widthAtSpace = 0;
            boolean hadAdvance = false;
            for (; index < glyphs.size(); index++) {
                Glyph glyph = glyphs.get(index);
                if (glyph.point() == '\n') break;
                if (glyph.point() == ' ') {
                    lastSpace = index;
                    widthAtSpace = width;
                }
                if (hadAdvance && width + glyph.advance() > lineWidth) break;
                width += glyph.advance();
                hadAdvance |= glyph.advance() != 0;
            }
            lines++;
            if (index == glyphs.size()) {
                maximum = Math.max(maximum, width);
                if (lineEnd != null) lineEnd.accept(points[index]);
                break;
            }
            if (glyphs.get(index).point() == '\n') {
                maximum = Math.max(maximum, width);
                start = index + 1;
                if (lineEnd != null) lineEnd.accept(points[start]);
                if (start == glyphs.size()) {
                    lines++;
                    if (lineEnd != null) lineEnd.accept(points[start]);
                }
            } else {
                wrapped = true;
                maximum = Math.max(maximum, lastSpace >= start ? widthAtSpace : width);
                start = lastSpace >= start ? lastSpace + 1 : index;
                if (lineEnd != null) lineEnd.accept(points[start]);
            }
        }
        // Non-Latin advances are estimates. A client's different word breaks may fill any
        // part of the permitted line, so reserve that width instead of one predicted line.
        return new Extent(wrapped ? Math.max(maximum, lineWidth) : maximum, Math.max(1, lines));
    }

    public record Extent(int widthPixels, int lines) { }
    private record Glyph(int point, int advance, int count) {
        Glyph(int point, int advance) { this(point, advance, 1); }
    }
}
