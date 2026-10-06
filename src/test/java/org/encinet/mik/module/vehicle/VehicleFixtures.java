package org.encinet.mik.module.vehicle;

import org.joml.Matrix4f;
import org.joml.Quaterniond;

import java.util.List;

final class VehicleFixtures {
    static VehicleDefinition definition(VehicleDefinition.Kind kind) {
        return VehicleDefinition.preset("test_" + kind.name().toLowerCase(java.util.Locale.ROOT), kind,
                List.of(part(new Matrix4f().translation(-1, 0, -2).scale(2, 1, 4))),
                new VehicleDefinition.Collider(new VehicleVector(0, 0.5, 0), new VehicleVector(1, 0.5, 2)));
    }
    static VehicleDefinition.Part part(Matrix4f matrix) {
        return new VehicleDefinition.Part(VehicleDefinition.PartKind.BLOCK, "minecraft:stone", matrix,
                VehicleDefinition.Animation.BODY, "FIXED", "NONE", "CENTER", -1, -1,
                0x40000000, 200, (byte) -1, false, false);
    }
    static VehicleBody body(VehicleDefinition.Kind kind, VehicleVector origin) {
        return new VehicleBody(definition(kind), origin, new Quaterniond());
    }
    static VehiclePhysics.Environment environment(double ground, double water) {
        return new VehiclePhysics.Environment() {
            @Override public double groundDistance(VehicleVector point, double maximum) {
                double distance = point.coordinateY() - ground;
                return distance <= maximum ? distance : Double.POSITIVE_INFINITY;
            }
            @Override public double waterDepth(VehicleVector point) { return Math.max(0, water - point.coordinateY()); }
            @Override public VehicleVector wind() { return VehicleVector.ZERO; }
        };
    }
    static void simulate(VehicleBody body, VehicleInput input, VehiclePhysics.Environment environment, int steps) {
        VehiclePhysics physics = new VehiclePhysics();
        for (int index = 0; index < steps; index++) {
            VehiclePhysics.Motion motion = physics.step(body, input, environment, 0.01);
            body.position = body.position.add(motion.displacement());
            body.orientation.set(motion.orientation());
        }
    }
}
