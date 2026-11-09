package org.encinet.mik.shared.menu.ui;

/** Optional first/last text alignment lines measured down from the component's top edge. */
public record UiBaseline(double first, double last) {
    public enum Line { FIRST, LAST }

    public UiBaseline {
        if (!Double.isFinite(first) || !Double.isFinite(last) || first < 0 || last < first)
            throw new IllegalArgumentException("Invalid component baselines");
    }

    public double at(Line line) { return java.util.Objects.requireNonNull(line) == Line.FIRST ? first : last; }
    public UiBaseline shifted(double down) { return new UiBaseline(first + down, last + down); }
}
