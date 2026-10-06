package org.encinet.mik.module.vehicle;

import org.joml.Quaterniond;

final class VehicleBody {
    final VehicleDefinition definition;
    final VehicleTransmission transmission = new VehicleTransmission();
    final Quaterniond orientation;
    VehicleVector position;
    VehicleVector velocity = VehicleVector.ZERO;
    VehicleVector angularVelocity = VehicleVector.ZERO;
    double fuel;
    double health = 100;
    double throttle;
    double steering;
    double brake;
    boolean handbrake;
    double wheelAngle;
    double propellerAngle;
    boolean engineRunning;
    boolean assisted = true;
    boolean grounded;
    boolean stalled;
    boolean floating;
    boolean blocked;

    VehicleBody(VehicleDefinition definition, VehicleVector origin, Quaterniond orientation) {
        this.definition = definition;
        this.orientation = new Quaterniond(orientation).normalize();
        position = origin.add(definition.centerOfMass().rotate(this.orientation));
        fuel = definition.fuelCapacity();
    }

    VehicleVector origin() { return position.subtract(definition.centerOfMass().rotate(orientation)); }
    VehicleVector point(VehicleVector local) { return origin().add(local.rotate(orientation)); }
    VehicleVector forward() { return new VehicleVector(0, 0, 1).rotate(orientation); }
    VehicleVector up() { return VehicleVector.UP.rotate(orientation); }
    VehicleVector localVelocity() { return velocity.rotate(new Quaterniond(orientation).conjugate()); }
    boolean resting() { return (grounded || floating) && velocity.length() < 0.03 && angularVelocity.length() < 0.03 && !engineRunning; }
}
