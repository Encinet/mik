package org.encinet.mik.module.vehicle;

import java.util.List;

final class VehicleEditorTransform {
    enum Mode { ROTATE, SCALE, SIZE }

    static double defaultStep(Mode mode) {
        return switch (mode) { case ROTATE -> 5; case SCALE -> 1.05; case SIZE -> 0.1; };
    }

    static double nextStep(Mode mode, double current) {
        List<Double> choices = switch (mode) {
            case ROTATE -> List.of(1.0, 5.0, 15.0);
            case SCALE -> List.of(1.01, 1.05, 1.25);
            case SIZE -> List.of(0.1, 0.25, 1.0);
        };
        return choices.get((choices.indexOf(current) + 1) % choices.size());
    }

    static VehicleVector value(VehicleModelDraft draft, Mode mode, int index) {
        if (mode == Mode.SIZE) return draft.collider(index).halfSize().multiply(2);
        if (mode == Mode.ROTATE) return draft.position("parts", index, false);
        var scale = draft.parts().get(index).transform().getScale(new org.joml.Vector3f());
        return new VehicleVector(scale.x, scale.y, scale.z);
    }

    static void adjust(VehicleModelDraft draft, Mode mode, int index, int axis, int direction, double step) {
        if (axis < 0 || axis > 2 || Math.abs(direction) != 1 || !Double.isFinite(step) || step <= 0 || mode == Mode.SCALE && step <= 1)
            throw new IllegalArgumentException("Invalid transform adjustment");
        if (mode == Mode.SCALE) {
            double factor = direction > 0 ? step : 1 / step;
            draft.changePart(index, "scale", VehicleEditorGeometry.coordinates(new VehicleVector(axis == 0 ? factor : 1,
                    axis == 1 ? factor : 1, axis == 2 ? factor : 1)));
            return;
        }
        VehicleVector offset = new VehicleVector(axis == 0 ? direction * step : 0, axis == 1 ? direction * step : 0, axis == 2 ? direction * step : 0);
        if (mode == Mode.ROTATE) draft.changePart(index, "rotate", VehicleEditorGeometry.coordinates(offset));
        else draft.size(index, value(draft, mode, index).add(offset));
    }

    static void scaleAll(VehicleModelDraft draft, int index, int direction, double step) {
        if (Math.abs(direction) != 1 || !Double.isFinite(step) || step <= 1) throw new IllegalArgumentException("Invalid scale adjustment");
        double factor = direction > 0 ? step : 1 / step;
        draft.changePart(index, "scale", VehicleEditorGeometry.coordinates(new VehicleVector(factor, factor, factor)));
    }
}
