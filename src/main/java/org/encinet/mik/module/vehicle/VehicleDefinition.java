package org.encinet.mik.module.vehicle;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

record VehicleDefinition(String id, Kind kind, double mass, VehicleVector centerOfMass,
                         VehicleVector inertia, List<Collider> colliders, List<Seat> seats,
                         List<VehicleVector> supports, double supportLength, double grip,
                         double maximumSpeed, double fuelCapacity, double consumption,
                         Engine engine, double buoyancy, double wingArea, double lift,
                         List<Part> parts) {
    enum Kind { CAR, BOAT, PLANE }
    enum PartKind { BLOCK, ITEM, TEXT }
    enum Animation { BODY, FRONT_WHEEL, WHEEL, PROPELLER, RUDDER }

    record Collider(VehicleVector center, VehicleVector halfSize) {
        Collider {
            Objects.requireNonNull(center);
            Objects.requireNonNull(halfSize);
            positive(halfSize.coordinateX(), "collider.x", 16);
            positive(halfSize.coordinateY(), "collider.y", 16);
            positive(halfSize.coordinateZ(), "collider.z", 16);
            if (center.length() + halfSize.length() > 24) throw new IllegalArgumentException("Collider exceeds 24 blocks");
        }
    }

    record Seat(VehicleVector position, VehicleVector exit, boolean driver) {
        Seat {
            Objects.requireNonNull(position);
            Objects.requireNonNull(exit);
            if (position.length() > 24 || exit.length() > 24) throw new IllegalArgumentException("Seat exceeds 24 blocks");
        }
    }

    record Engine(double peakTorque, double idleRpm, double redlineRpm, double inertia,
                  double wheelRadius, double finalDrive, double reverseRatio, List<Double> ratios,
                  double shiftSeconds) {
        Engine {
            positive(peakTorque, "torque", 100000);
            positive(idleRpm, "idle-rpm", 10000);
            positive(redlineRpm, "redline-rpm", 50000);
            if (redlineRpm <= idleRpm * 1.5) throw new IllegalArgumentException("Redline too close to idle");
            positive(inertia, "engine inertia", 100);
            positive(wheelRadius, "wheel radius", 4);
            positive(finalDrive, "final drive", 20);
            positive(reverseRatio, "reverse ratio", 20);
            positive(shiftSeconds, "shift time", 5);
            ratios = List.copyOf(ratios);
            if (ratios.isEmpty() || ratios.size() > 12) throw new IllegalArgumentException("Use 1..12 forward gears");
            double previous = Double.POSITIVE_INFINITY;
            for (double ratio : ratios) {
                positive(ratio, "gear ratio", 20);
                if (ratio >= previous) throw new IllegalArgumentException("Gear ratios must decrease");
                previous = ratio;
            }
        }
        double ratio(int gear) { return gear == 0 ? 0 : (gear < 0 ? -reverseRatio : ratios.get(gear - 1)) * finalDrive; }
    }

    record Part(PartKind kind, String payload, Matrix4fc transform, Animation animation,
                String billboard, String itemTransform, String alignment, int blockLight, int skyLight,
                int background, int lineWidth, byte textOpacity, boolean shadow, boolean seeThrough) {
        Part {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(payload);
            Objects.requireNonNull(animation);
            transform = new Matrix4f(transform);
            float[] values = transform.get(new float[16]);
            for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite model matrix");
            if (Math.abs(transform.m03()) > 1.0E-6 || Math.abs(transform.m13()) > 1.0E-6
                    || Math.abs(transform.m23()) > 1.0E-6 || Math.abs(transform.m33() - 1) > 1.0E-6)
                throw new IllegalArgumentException("Model matrix must be affine");
            for (int column = 0; column < 3; column++)
                if (transform.getColumn(column, new org.joml.Vector4f()).length() > 24)
                    throw new IllegalArgumentException("Model scale exceeds 24 blocks");
            if (new org.joml.Vector3f(transform.m30(), transform.m31(), transform.m32()).length() > 24)
                throw new IllegalArgumentException("Model offset exceeds 24 blocks");
            if (payload.length() > 131072 || lineWidth < 1 || lineWidth > 2048
                    || blockLight < -1 || blockLight > 15 || skyLight < -1 || skyLight > 15)
                throw new IllegalArgumentException("Invalid display properties");
            org.bukkit.entity.Display.Billboard.valueOf(billboard);
            org.bukkit.entity.ItemDisplay.ItemDisplayTransform.valueOf(itemTransform);
            org.bukkit.entity.TextDisplay.TextAlignment.valueOf(alignment);
        }
        @Override public Matrix4fc transform() { return new Matrix4f(transform); }
    }

    VehicleDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("Invalid model id");
        Objects.requireNonNull(kind);
        Objects.requireNonNull(centerOfMass);
        Objects.requireNonNull(inertia);
        Objects.requireNonNull(engine);
        positive(mass, "mass", 1000000);
        positive(inertia.coordinateX(), "inertia.x", 100000000);
        positive(inertia.coordinateY(), "inertia.y", 100000000);
        positive(inertia.coordinateZ(), "inertia.z", 100000000);
        positive(supportLength, "support length", 2);
        positive(grip, "grip", 3);
        positive(maximumSpeed, "maximum speed", 80);
        positive(fuelCapacity, "fuel capacity", 1000000);
        positive(consumption, "consumption", 1000);
        positive(buoyancy, "buoyancy", 100);
        positive(wingArea, "wing area", 1000);
        positive(lift, "lift", 10);
        colliders = List.copyOf(colliders);
        seats = List.copyOf(seats);
        supports = List.copyOf(supports);
        parts = List.copyOf(parts);
        if (colliders.isEmpty() || colliders.size() > 16 || parts.isEmpty() || parts.size() > 128
                || seats.isEmpty() || seats.size() > 16 || supports.size() < 3 || supports.size() > 16)
            throw new IllegalArgumentException("Invalid component count");
        if (seats.stream().filter(Seat::driver).count() != 1) throw new IllegalArgumentException("Exactly one driver seat required");
        if (centerOfMass.length() > 24 || supports.stream().anyMatch(point -> point.length() > 24))
            throw new IllegalArgumentException("Physics points exceed 24 blocks");
    }

    static VehicleDefinition preset(String id, Kind kind, List<Part> parts, Collider bounds) {
        double mass = switch (kind) { case CAR -> 1200; case BOAT -> 1800; case PLANE -> 900; };
        VehicleVector half = bounds.halfSize();
        VehicleVector center = bounds.center();
        List<VehicleVector> supports = List.of(
                new VehicleVector(center.coordinateX() - half.coordinateX() * 0.8, center.coordinateY() - half.coordinateY() + 0.3, center.coordinateZ() + half.coordinateZ() * 0.75),
                new VehicleVector(center.coordinateX() + half.coordinateX() * 0.8, center.coordinateY() - half.coordinateY() + 0.3, center.coordinateZ() + half.coordinateZ() * 0.75),
                new VehicleVector(center.coordinateX() - half.coordinateX() * 0.8, center.coordinateY() - half.coordinateY() + 0.3, center.coordinateZ() - half.coordinateZ() * 0.75),
                new VehicleVector(center.coordinateX() + half.coordinateX() * 0.8, center.coordinateY() - half.coordinateY() + 0.3, center.coordinateZ() - half.coordinateZ() * 0.75));
        return new VehicleDefinition(id, kind, mass, center,
                new VehicleVector(mass * (half.coordinateY() * half.coordinateY() + half.coordinateZ() * half.coordinateZ()) / 3,
                        mass * (half.coordinateX() * half.coordinateX() + half.coordinateZ() * half.coordinateZ()) / 3,
                        mass * (half.coordinateX() * half.coordinateX() + half.coordinateY() * half.coordinateY()) / 3),
                List.of(bounds), List.of(new Seat(center.add(new VehicleVector(0, 0.1, 0)),
                center.add(new VehicleVector(half.coordinateX() + 1, 0, 0)), true)), supports,
                0.3, 0.95, kind == Kind.PLANE ? 65 : 40, 100, 0.015,
                new Engine(kind == Kind.PLANE ? 1100 : 240, 800, 6500, 0.5, 0.35, 3.8, 3.2,
                        List.of(3.3, 2.1, 1.45, 1.05, 0.82), 0.3), 2.5, 16, 1.1, parts);
    }

    static Kind parseKind(String input) { return Kind.valueOf(input.toUpperCase(Locale.ROOT)); }
    static void positive(double value, String name, double maximum) {
        if (!Double.isFinite(value) || value <= 0 || value > maximum)
            throw new IllegalArgumentException("Invalid " + name);
    }
}
