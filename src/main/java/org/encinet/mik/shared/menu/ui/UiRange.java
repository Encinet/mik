package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.FloatingMenuHandle;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** A bounded live value. Sampling and change callbacks run on the menu's server thread. */
public record UiRange(double minimum, double maximum, DoubleSupplier value, double step,
                      DoubleFunction<Component> formatter, boolean labels) {
    public UiRange {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum <= minimum
                || !Double.isFinite(maximum - minimum) || !Double.isFinite(step) || step <= 0)
            throw new IllegalArgumentException("Invalid numeric range");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(formatter, "formatter");
    }

    public static UiRange live(double minimum, double maximum, DoubleSupplier value) {
        return new UiRange(minimum, maximum, value, 1,
                number -> Component.text(number == Math.rint(number) ? Long.toString((long) number) : Double.toString(number)), false);
    }
    public static UiRange fixed(double minimum, double maximum, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Range value must be finite");
        return live(minimum, maximum, () -> value);
    }
    public UiRange step(double amount) { return new UiRange(minimum, maximum, value, amount, formatter, labels); }
    public UiRange format(DoubleFunction<Component> format) { return new UiRange(minimum, maximum, value, step, format, labels); }
    public UiRange withLabels() { return new UiRange(minimum, maximum, value, step, formatter, true); }
    public double current() {
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalStateException("Live range returned a non-finite value");
        return Math.clamp(result, minimum, maximum);
    }
    public double fraction(double number) {
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Range value must be finite");
        return Math.clamp((number - minimum) / (maximum - minimum), 0, 1);
    }
    public double at(double fraction) {
        if (!Double.isFinite(fraction)) throw new IllegalArgumentException("Pointer fraction must be finite");
        return minimum + Math.clamp(fraction, 0, 1) * (maximum - minimum);
    }
    public Component text(double number) { return Objects.requireNonNull(formatter.apply(number), "formatted range value"); }

    @FunctionalInterface public interface Change {
        void apply(Player player, FloatingMenuHandle handle, double value);
    }

    /** Measured world-unit geometry shared by painting, labels and normalized pointer input. */
    public record Geometry(double width, double height, double leading, double trailing, double gap, double textScale) {
        public Geometry {
            if (!Double.isFinite(width + height + leading + trailing + gap + textScale)
                    || width <= 0 || height <= 0 || leading < 0 || trailing < 0 || gap < 0 || textScale <= 0
                    || width <= leading + trailing + gap * 2)
                throw new IllegalArgumentException("Invalid range geometry");
        }
        public double trackWidth() { return width - leading - trailing - gap * 2; }
        public double trackStart() { return -width / 2 + leading + gap; }
        /** Labels belong to the surface but cannot accidentally seek to either endpoint. */
        public OptionalDouble pointer(double contentFraction) {
            if (!Double.isFinite(contentFraction)) throw new IllegalArgumentException("Pointer fraction must be finite");
            double fraction = (contentFraction * width - leading - gap) / trackWidth();
            return fraction < -1e-8 || fraction > 1 + 1e-8 ? OptionalDouble.empty() : OptionalDouble.of(Math.clamp(fraction, 0, 1));
        }
    }
}
