package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** Fixed RGB and palette references are distinct values; both preserve explicit alpha. */
public sealed interface UiColor permits UiColor.Literal, UiColor.Themed {
    double alpha();
    default double alpha(UiThemeColors palette) { return alpha(); }
    UiColor withAlpha(double alpha);
    int argb(UiThemeColors palette);
    default int argb() { return argb(UiPalette.DEFAULT); }

    static UiColor literal(int rgb, double alpha) { return new Literal(rgb, alpha); }
    static UiColor fromArgb(int argb) { return literal(argb & 0xffffff, (argb >>> 24) / 255.0); }
    static UiColor themed(UiColorRole role) { return new Themed(Objects.requireNonNull(role), java.util.OptionalDouble.empty()); }

    record Literal(int rgb, double alpha) implements UiColor {
        public Literal {
            if ((rgb & 0xff000000) != 0) throw new IllegalArgumentException("RGB must not include alpha");
            checkAlpha(alpha);
        }
        @Override public UiColor withAlpha(double value) { return new Literal(rgb, value); }
        @Override public int argb(UiThemeColors palette) {
            Objects.requireNonNull(palette, "palette");
            return compose(rgb, alpha);
        }
    }

    record Themed(UiColorRole role, java.util.OptionalDouble opacity) implements UiColor {
        public Themed {
            Objects.requireNonNull(role, "role"); Objects.requireNonNull(opacity, "opacity");
            if (opacity.isPresent()) checkAlpha(opacity.getAsDouble());
        }
        @Override public double alpha() { return opacity.orElse(role.alpha()); }
        @Override public double alpha(UiThemeColors palette) { return opacity.orElse(palette.alpha(role)); }
        @Override public UiColor withAlpha(double value) { return new Themed(role, java.util.OptionalDouble.of(value)); }
        @Override public int argb(UiThemeColors palette) { return compose(Objects.requireNonNull(palette, "palette").rgb(role), alpha(palette)); }
    }

    private static void checkAlpha(double alpha) {
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1) throw new IllegalArgumentException("Invalid color opacity");
    }
    private static int compose(int rgb, double alpha) { return rgb | ((int) Math.round(alpha * 255) << 24); }
}
