package org.encinet.mik.shared.menu.runtime.layout;

import org.encinet.mik.shared.menu.ui.UiCompiler;
import org.encinet.mik.shared.menu.ui.UiConstraints;
import org.encinet.mik.shared.menu.ui.UiContent;
import org.encinet.mik.shared.menu.ui.UiElement;
import org.encinet.mik.shared.menu.ui.UiTextMetrics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** View-owned, bounded text measurements. Values contain no actions, nodes, entities or business state. */
public final class MenuTextMeasurementCache {
    public static final int CAPACITY = 2048;
    public static final int WEIGHT_BUDGET = 131072;
    private static final int MAX_ENTRY_WEIGHT = 4096;
    private sealed interface Key permits LeafKey, DocumentKey { }
    private record LeafKey(UiContent.Text text, UiConstraints constraints, int textWidth, double textScale,
                           double typography, double idleAmplitude, boolean interactive) implements Key { }
    private record DocumentKey(Component text, int width) implements Key { }
    private sealed interface Cached permits LeafMeasurement, DocumentLines { int weight(); }
    private record LeafMeasurement(UiCompiler.ElementMeasurement measurement, int weight) implements Cached { }
    private record DocumentLines(UiTextMetrics.ReadingLines lines, int weight) implements Cached { }
    public record Stats(int entries, long hits, long misses, int weight) { }
    private final LinkedHashMap<Key, Cached> values = new LinkedHashMap<>(128, .75f, true);
    private int retainedWeight;
    private long hits;
    private long misses;

    public UiCompiler.ElementMeasurement measure(UiElement element, UiConstraints constraints, double typography,
                                                  double idleAmplitude, Supplier<UiCompiler.ElementMeasurement> measurement) {
        if (!(element.content() instanceof UiContent.Text text)) return measurement.get();
        var key = new LeafKey(text, constraints, element.textWidthPixels(), element.style().textScale(),
                typography, idleAmplitude, element.input().interactive());
        var cached = values.get(key);
        if (cached instanceof LeafMeasurement leaf) { hits++; return leaf.measurement(); }
        misses++;
        int weight = weight(text.label(), MAX_ENTRY_WEIGHT);
        var result = java.util.Objects.requireNonNull(measurement.get());
        if (weight < 0) return result;
        retain(key, new LeafMeasurement(result, weight));
        return result;
    }

    /** Pure source breaks share the view's total budget; no node, callback or owner position is retained. */
    public UiTextMetrics.ReadingLines documentLines(Component text, int width, Supplier<UiTextMetrics.ReadingLines> measurement) {
        var key = new DocumentKey(text, width);
        var cached = values.get(key);
        if (cached instanceof DocumentLines document) { hits++; return document.lines(); }
        misses++;
        int weight = weight(text, WEIGHT_BUDGET);
        var result = java.util.Objects.requireNonNull(measurement.get());
        if (weight < 0 || result.ends().size() > (WEIGHT_BUDGET - weight) / Integer.BYTES) return result;
        retain(key, new DocumentLines(result, weight + result.ends().size() * Integer.BYTES));
        return result;
    }

    private void retain(Key key, Cached result) {
        var previous = values.put(key, result);
        retainedWeight += result.weight() - (previous == null ? 0 : previous.weight());
        while (values.size() > CAPACITY || retainedWeight > WEIGHT_BUDGET) {
            retainedWeight -= values.pollFirstEntry().getValue().weight();
        }
    }

    /** Retain only immutable text/style data; callbacks, hover payloads and dynamic object components stay uncached. */
    private static int weight(Component root, int maximum) {
        var pending = new ArrayDeque<Component>();
        pending.add(root);
        long size = 0;
        int nodes = 0;
        while (!pending.isEmpty()) {
            Component value = pending.removeLast();
            if (!(value instanceof TextComponent text) || ++nodes > 256 || value.clickEvent() != null
                    || value.hoverEvent() != null || value.insertion() != null) return -1;
            size += 32L + text.content().length() + (value.font() == null ? 0 : value.font().asString().length());
            if (size > maximum) return -1;
            if (value.children().size() > 256 - nodes - pending.size()) return -1;
            pending.addAll(value.children());
        }
        return (int) Math.max(1, size);
    }

    public Stats stats() { return new Stats(values.size(), hits, misses, retainedWeight); }
    public void clear() { values.clear(); retainedWeight = 0; }
}
