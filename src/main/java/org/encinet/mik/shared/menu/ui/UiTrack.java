package org.encinet.mik.shared.menu.ui;

/** Content tracks can shrink; intrinsic tracks retain their measured width, and fixed tracks declare it. */
public record UiTrack(Kind kind, double minimum, double maximum, double weight) {
    public enum Kind { CONTENT, INTRINSIC, FIXED, FRACTION }
    public UiTrack {
        java.util.Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(weight)
                || minimum < 0 || maximum < minimum || weight < 0 || maximum > 4096
                || (kind == Kind.FRACTION) != (weight > 0) || kind == Kind.FIXED && minimum != maximum)
            throw new IllegalArgumentException("Invalid track size");
    }
    public static UiTrack content() { return content(UiLength.ZERO, UiLength.ui(4096)); }
    public static UiTrack content(UiLength minimum, UiLength maximum) { return new UiTrack(Kind.CONTENT, minimum.value(), maximum.value(), 0); }
    public static UiTrack intrinsic() { return new UiTrack(Kind.INTRINSIC, 0, 4096, 0); }
    public static UiTrack fixed(UiLength size) { return new UiTrack(Kind.FIXED, size.value(), size.value(), 0); }
    public static UiTrack fraction(double weight) { return new UiTrack(Kind.FRACTION, 0, 4096, weight); }
}
