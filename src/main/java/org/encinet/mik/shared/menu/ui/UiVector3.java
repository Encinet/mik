package org.encinet.mik.shared.menu.ui;

/** Immutable vector in a declared local coordinate system; component declarations use UI units. */
public record UiVector3(double right, double up, double forward) {
    public static final UiVector3 ZERO = new UiVector3(0, 0, 0);
    public UiVector3 {
        if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(forward))
            throw new IllegalArgumentException("Spatial coordinates must be finite");
        if (right == 0) right = 0;
        if (up == 0) up = 0;
        if (forward == 0) forward = 0;
    }
    public UiVector3 add(UiVector3 other) { return new UiVector3(right + other.right, up + other.up, forward + other.forward); }
    public UiVector3 subtract(UiVector3 other) { return new UiVector3(right - other.right, up - other.up, forward - other.forward); }
    public UiVector3 multiply(double value) { return new UiVector3(right * value, up * value, forward * value); }
    public double dot(UiVector3 other) { return right * other.right + up * other.up + forward * other.forward; }
    public UiVector3 cross(UiVector3 other) {
        return new UiVector3(up * other.forward - forward * other.up, forward * other.right - right * other.forward,
                right * other.up - up * other.right);
    }
}
