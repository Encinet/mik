package org.encinet.mik.module.vehicle;

import org.joml.Quaterniond;

import java.util.UUID;

final class VehicleInstance {
    final UUID id;
    final UUID worldId;
    final UUID owner;
    final VehicleBody body;
    boolean publicAccess;
    VehicleEntityGroup entities;

    VehicleInstance(UUID id, UUID worldId, UUID owner, VehicleBody body) {
        this.id = id;
        this.worldId = worldId;
        this.owner = owner;
        this.body = body;
    }

    record Snapshot(UUID id, UUID world, UUID owner, String definition, boolean publicAccess,
                    VehicleVector origin, double rotationX, double rotationY, double rotationZ, double rotationW,
                    VehicleVector velocity, VehicleVector angularVelocity, double fuel, double health,
                    double throttle, boolean engineRunning, boolean assisted,
                    VehicleTransmission.Mode mode, VehicleTransmission.Selector selector,
                    int gear, int targetGear, double shiftRemaining, double gearAge, double rpm) {
        Snapshot {
            if (id == null || world == null || owner == null || definition == null || mode == null || selector == null)
                throw new IllegalArgumentException("Incomplete vehicle snapshot");
            double norm = rotationX * rotationX + rotationY * rotationY + rotationZ * rotationZ + rotationW * rotationW;
            if (!Double.isFinite(norm) || Math.abs(norm - 1) > 0.01 || velocity.length() > 100 || angularVelocity.length() > 10
                    || !Double.isFinite(fuel) || fuel < 0 || !Double.isFinite(health) || health < 0 || health > 100
                    || !Double.isFinite(throttle) || throttle < -1 || throttle > 1
                    || !Double.isFinite(shiftRemaining) || shiftRemaining < 0 || shiftRemaining > 5
                    || !Double.isFinite(gearAge) || gearAge < 0 || !Double.isFinite(rpm) || rpm < 0 || rpm > 60000)
                throw new IllegalArgumentException("Invalid vehicle snapshot");
            if (Math.abs(origin.coordinateX()) > 30000000 || Math.abs(origin.coordinateZ()) > 30000000 || Math.abs(origin.coordinateY()) > 100000)
                throw new IllegalArgumentException("Invalid vehicle location");
        }

        VehicleInstance restore(VehicleDefinition specification) {
            if (!definition.equals(specification.id()) || fuel > specification.fuelCapacity()
                    || gear < -1 || gear > specification.engine().ratios().size()
                    || targetGear < -1 || targetGear > specification.engine().ratios().size())
                throw new IllegalArgumentException("Snapshot does not match definition " + definition);
            VehicleBody restored = new VehicleBody(specification, origin,
                    new Quaterniond(rotationX, rotationY, rotationZ, rotationW));
            restored.velocity = velocity;
            restored.angularVelocity = angularVelocity;
            restored.fuel = fuel;
            restored.health = health;
            restored.throttle = throttle;
            restored.engineRunning = false;
            restored.assisted = assisted;
            restored.transmission.mode = mode;
            restored.transmission.selector = selector;
            restored.transmission.gear = gear;
            restored.transmission.targetGear = targetGear;
            restored.transmission.shiftRemaining = shiftRemaining;
            restored.transmission.gearAge = gearAge;
            restored.transmission.rpm = rpm;
            VehicleInstance instance = new VehicleInstance(id, world, owner, restored);
            instance.publicAccess = publicAccess;
            return instance;
        }
    }

    Snapshot snapshot() {
        VehicleTransmission transmission = body.transmission;
        return new Snapshot(id, worldId, owner, body.definition.id(), publicAccess, body.origin(),
                body.orientation.x, body.orientation.y, body.orientation.z, body.orientation.w,
                body.velocity, body.angularVelocity, body.fuel, body.health, body.throttle, body.engineRunning,
                body.assisted, transmission.mode, transmission.selector, transmission.gear, transmission.targetGear,
                transmission.shiftRemaining, transmission.gearAge, transmission.rpm);
    }
}
