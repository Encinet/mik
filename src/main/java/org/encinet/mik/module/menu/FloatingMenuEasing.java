package org.encinet.mik.module.menu;

public enum FloatingMenuEasing {
    LINEAR {
        @Override public double apply(double value) { return clamp(value); }
    },
    CUBIC_OUT {
        @Override public double apply(double value) { return 1.0 - Math.pow(1.0 - clamp(value), 3.0); }
    },
    BACK_OUT {
        @Override public double apply(double value) {
            double x = clamp(value) - 1.0;
            return 1.0 + 2.70158 * x * x * x + 1.70158 * x * x;
        }
    };

    public abstract double apply(double value);

    static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
