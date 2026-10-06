package org.encinet.mik.module.vehicle;

import org.joml.Quaterniondc;

import java.util.ArrayList;
import java.util.List;

final class VehicleCollision {
    record Box(VehicleVector center, VehicleVector halfSize, List<VehicleVector> axes) {
        Box {
            axes = List.copyOf(axes);
            if (axes.size() != 3) throw new IllegalArgumentException("Three box axes required");
        }
        static Box axisAligned(VehicleVector minimum, VehicleVector maximum) {
            return new Box(minimum.add(maximum).multiply(0.5), maximum.subtract(minimum).multiply(0.5),
                    List.of(new VehicleVector(1, 0, 0), VehicleVector.UP, new VehicleVector(0, 0, 1)));
        }
        static Box body(VehicleDefinition.Collider collider, VehicleVector origin, Quaterniondc orientation) {
            return new Box(origin.add(collider.center().rotate(orientation)), collider.halfSize(),
                    List.of(new VehicleVector(1, 0, 0).rotate(orientation), VehicleVector.UP.rotate(orientation),
                            new VehicleVector(0, 0, 1).rotate(orientation)));
        }
        double radius(VehicleVector axis) {
            return Math.abs(axes.get(0).dot(axis)) * halfSize.coordinateX() + Math.abs(axes.get(1).dot(axis)) * halfSize.coordinateY()
                    + Math.abs(axes.get(2).dot(axis)) * halfSize.coordinateZ();
        }
        VehicleVector extent() {
            return new VehicleVector(radius(new VehicleVector(1, 0, 0)), radius(VehicleVector.UP),
                    radius(new VehicleVector(0, 0, 1)));
        }
    }
    record Hit(double fraction, VehicleVector normal, double penetration) { }

    static Hit sweep(Box moving, VehicleVector displacement, Box obstacle) {
        List<VehicleVector> axes = new ArrayList<>(moving.axes());
        axes.addAll(obstacle.axes());
        for (VehicleVector first : moving.axes()) for (VehicleVector second : obstacle.axes()) {
            VehicleVector crossed = first.cross(second);
            if (crossed.length() > 1.0E-7) axes.add(crossed.normalized());
        }
        VehicleVector difference = moving.center().subtract(obstacle.center());
        double enter = Double.NEGATIVE_INFINITY;
        double leave = Double.POSITIVE_INFINITY;
        double penetration = Double.POSITIVE_INFINITY;
        VehicleVector enterNormal = VehicleVector.ZERO;
        VehicleVector overlapNormal = VehicleVector.ZERO;
        boolean overlapping = true;
        for (VehicleVector axis : axes) {
            double radius = moving.radius(axis) + obstacle.radius(axis);
            double distance = difference.dot(axis);
            double depth = radius - Math.abs(distance);
            if (depth < 0) overlapping = false;
            if (depth < penetration) {
                penetration = depth;
                overlapNormal = axis.multiply(distance >= 0 ? 1 : -1);
            }
            double speed = displacement.dot(axis);
            if (Math.abs(speed) < 1.0E-10) {
                if (depth < -1.0E-8) return null;
                continue;
            }
            double first = (-radius - distance) / speed;
            double second = (radius - distance) / speed;
            double axisEnter = Math.min(first, second);
            if (axisEnter > enter) {
                enter = axisEnter;
                enterNormal = axis.multiply(speed > 0 ? -1 : 1);
            }
            leave = Math.min(leave, Math.max(first, second));
            if (enter > leave) return null;
        }
        if (overlapping) {
            if (penetration < 1.0E-6 && displacement.dot(overlapNormal) >= -1.0E-9) return null;
            return new Hit(0, overlapNormal, Math.max(0, penetration));
        }
        if (enter < 0 || enter > 1 || leave < 0) return null;
        return new Hit(enter, enterNormal, 0);
    }
}
