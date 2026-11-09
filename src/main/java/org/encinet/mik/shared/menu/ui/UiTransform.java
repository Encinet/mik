package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** Composable similarity transform: positive uniform scale keeps reading planes rigid and invertible. */
public record UiTransform(UiVector3 translation, UiRotation rotation, double scale) {
    public static final UiTransform IDENTITY = new UiTransform(UiVector3.ZERO, UiRotation.IDENTITY, 1);
    public UiTransform {
        Objects.requireNonNull(translation); Objects.requireNonNull(rotation);
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Spatial scale must be positive and finite");
    }
    public UiVector3 apply(UiVector3 point) { return rotation.apply(point.multiply(scale)).add(translation); }
    public UiTransform compose(UiTransform child) {
        return new UiTransform(apply(child.translation), rotation.compose(child.rotation), scale * child.scale);
    }
    public UiTransform inverse() {
        UiRotation inverse = rotation.inverse();
        return new UiTransform(inverse.apply(translation).multiply(-1 / scale), inverse, 1 / scale);
    }
    public UiTransform translated(double right, double up, double forward) {
        return new UiTransform(translation.add(new UiVector3(right, up, forward)), rotation, scale);
    }
    public UiTransform rotated(double yaw, double pitch, double roll) {
        return new UiTransform(translation, rotation.compose(UiRotation.degrees(yaw, pitch, roll)), scale);
    }
    public UiTransform scaled(double factor) { return new UiTransform(translation, rotation, scale * factor); }
    public UiTransform around(UiVector3 pivot) {
        return new UiTransform(translation.add(pivot).subtract(rotation.apply(pivot.multiply(scale))), rotation, scale);
    }
}
