package org.encinet.mik.module.vehicle;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

record VehicleVector(double coordinateX, double coordinateY, double coordinateZ) {
    static final VehicleVector ZERO = new VehicleVector(0, 0, 0);
    static final VehicleVector UP = new VehicleVector(0, 1, 0);

    VehicleVector {
        if (!Double.isFinite(coordinateX) || !Double.isFinite(coordinateY) || !Double.isFinite(coordinateZ))
            throw new IllegalArgumentException("Non-finite vector");
        coordinateX = coordinateX == 0 ? 0 : coordinateX;
        coordinateY = coordinateY == 0 ? 0 : coordinateY;
        coordinateZ = coordinateZ == 0 ? 0 : coordinateZ;
    }

    VehicleVector add(VehicleVector other) { return new VehicleVector(coordinateX + other.coordinateX, coordinateY + other.coordinateY, coordinateZ + other.coordinateZ); }
    VehicleVector subtract(VehicleVector other) { return add(other.multiply(-1)); }
    VehicleVector multiply(double scale) { return new VehicleVector(coordinateX * scale, coordinateY * scale, coordinateZ * scale); }
    double dot(VehicleVector other) { return coordinateX * other.coordinateX + coordinateY * other.coordinateY + coordinateZ * other.coordinateZ; }
    VehicleVector cross(VehicleVector other) {
        return new VehicleVector(coordinateY * other.coordinateZ - coordinateZ * other.coordinateY, coordinateZ * other.coordinateX - coordinateX * other.coordinateZ, coordinateX * other.coordinateY - coordinateY * other.coordinateX);
    }
    double length() { return Math.sqrt(dot(this)); }
    VehicleVector normalized() { return length() < 1.0E-9 ? ZERO : multiply(1 / length()); }
    VehicleVector limited(double maximum) { return length() > maximum ? normalized().multiply(maximum) : this; }
    VehicleVector rotate(Quaterniondc orientation) {
        Vector3d rotated = orientation.transform(new Vector3d(coordinateX, coordinateY, coordinateZ));
        return new VehicleVector(rotated.x, rotated.y, rotated.z);
    }
}
