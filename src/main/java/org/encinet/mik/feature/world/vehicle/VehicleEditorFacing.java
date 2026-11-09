package org.encinet.mik.feature.world.vehicle;

import org.joml.Matrix4f;

/** Exact quarter-turn changes of the model basis; colliders remain axis-aligned. */
final class VehicleEditorFacing {
    private VehicleEditorFacing() { }

    static VehicleVector rebase(VehicleVector point, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new VehicleVector(point.coordinateZ(), point.coordinateY(), -point.coordinateX());
            case 2 -> new VehicleVector(-point.coordinateX(), point.coordinateY(), -point.coordinateZ());
            case 3 -> new VehicleVector(-point.coordinateZ(), point.coordinateY(), point.coordinateX());
            default -> point;
        };
    }

    static Matrix4f matrix(int turns) {
        VehicleVector x = rebase(new VehicleVector(1, 0, 0), turns);
        VehicleVector z = rebase(new VehicleVector(0, 0, 1), turns);
        return new Matrix4f().m00((float) x.coordinateX()).m02((float) x.coordinateZ())
                .m20((float) z.coordinateX()).m22((float) z.coordinateZ());
    }

    static VehicleVector extents(VehicleVector value, int turns) {
        return Math.floorMod(turns, 2) == 0 ? value : new VehicleVector(value.coordinateZ(), value.coordinateY(), value.coordinateX());
    }

    static int nearest(float relativeYaw) {
        if (!Float.isFinite(relativeYaw)) throw new IllegalArgumentException("Finite heading required");
        return Math.floorMod(Math.round(relativeYaw / 90), 4);
    }
}
