package org.encinet.mik.module.vehicle;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class VehicleEditorGeometry {
    static VehicleVector local(Location origin, Location position) {
        if (origin.getWorld() == null || !origin.getWorld().equals(position.getWorld()))
            throw new IllegalArgumentException("Position must be in the editor's world");
        if (origin.distanceSquared(position) > 1024)
            throw new IllegalArgumentException("Position must be within 32 blocks of the editor origin");
        return new VehicleVector(position.getX() - origin.getX(), position.getY() - origin.getY(), position.getZ() - origin.getZ())
                .rotate(VehicleModel.orientation(origin.getYaw()).conjugate());
    }

    static Location world(Location origin, VehicleVector point) {
        VehicleVector offset = point.rotate(VehicleModel.orientation(origin.getYaw()));
        return origin.clone().add(offset.coordinateX(), offset.coordinateY(), offset.coordinateZ());
    }

    static VehicleDefinition.Collider box(VehicleVector first, VehicleVector second) {
        VehicleVector half = new VehicleVector(Math.abs(first.coordinateX() - second.coordinateX()) / 2,
                Math.abs(first.coordinateY() - second.coordinateY()) / 2, Math.abs(first.coordinateZ() - second.coordinateZ()) / 2);
        if (half.coordinateX() < 0.001 || half.coordinateY() < 0.001 || half.coordinateZ() < 0.001)
            throw new IllegalArgumentException("Choose opposite corners with width, height and depth of at least 0.002 blocks");
        return new VehicleDefinition.Collider(first.add(second).multiply(0.5), half);
    }

    static List<VehicleVector> outline(VehicleDefinition.Collider collider) {
        List<VehicleVector> points = new ArrayList<>();
        VehicleVector half = collider.halfSize();
        for (int corner = 0; corner < 8; corner++) {
            VehicleVector start = collider.center().add(new VehicleVector((corner & 1) == 0 ? -half.coordinateX() : half.coordinateX(),
                    (corner & 2) == 0 ? -half.coordinateY() : half.coordinateY(), (corner & 4) == 0 ? -half.coordinateZ() : half.coordinateZ()));
            for (int axis = 0; axis < 3; axis++) {
                if ((corner & (1 << axis)) != 0) continue;
                VehicleVector edge = switch (axis) {
                    case 0 -> new VehicleVector(half.coordinateX() * 2, 0, 0);
                    case 1 -> new VehicleVector(0, half.coordinateY() * 2, 0);
                    default -> new VehicleVector(0, 0, half.coordinateZ() * 2);
                };
                for (int sample = 0; sample <= 4; sample++) points.add(start.add(edge.multiply(sample / 4.0)));
            }
        }
        return List.copyOf(points);
    }

    static String coordinates(VehicleVector point) {
        return point.coordinateX() + " " + point.coordinateY() + " " + point.coordinateZ();
    }

    static String display(VehicleVector point) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", point.coordinateX(), point.coordinateY(), point.coordinateZ());
    }

    static VehicleVector snap(VehicleVector point, double step) {
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Positive finite grid step required");
        return new VehicleVector(Math.rint(point.coordinateX() / step) * step,
                Math.rint(point.coordinateY() / step) * step, Math.rint(point.coordinateZ() / step) * step);
    }
}
