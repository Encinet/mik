package org.encinet.mik.module.vehicle;

import org.joml.Quaterniond;

final class VehiclePhysics {
    interface Environment {
        double groundDistance(VehicleVector point, double maximum);
        double waterDepth(VehicleVector point);
        VehicleVector wind();
    }
    record Motion(VehicleVector displacement, Quaterniond orientation) { }

    Motion step(VehicleBody body, VehicleInput input, Environment world, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 0.05) throw new IllegalArgumentException("Invalid step");
        VehicleDefinition definition = body.definition;
        body.blocked = false;
        body.grounded = false;
        body.floating = false;
        body.stalled = false;
        if (body.fuel <= 0 || body.health <= 0) body.engineRunning = false;
        VehicleVector force = new VehicleVector(0, -definition.mass() * 9.81, 0);
        VehicleVector torque = VehicleVector.ZERO;
        int contacts = 0;
        for (VehicleVector local : definition.supports()) {
            VehicleVector point = body.point(local);
            VehicleVector arm = point.subtract(body.position);
            VehicleVector pointVelocity = body.velocity.add(body.angularVelocity.rotate(body.orientation).cross(arm));
            double distance = world.groundDistance(point, definition.supportLength() + 0.4);
            if (distance <= definition.supportLength() + 0.15) {
                double spring = Math.max(0, definition.mass() / definition.supports().size()
                        * (9.81 + (definition.supportLength() - distance) * 90 - pointVelocity.coordinateY() * 8));
                VehicleVector support = new VehicleVector(0, spring, 0);
                force = force.add(support);
                torque = torque.add(arm.cross(support));
                contacts++;
            }
            double depth = world.waterDepth(point);
            if (depth > 0 && definition.kind() == VehicleDefinition.Kind.BOAT) {
                double lift = Math.max(0, definition.mass() * 9.81 / definition.supports().size()
                        * Math.min(1.5, depth * definition.buoyancy()) - pointVelocity.coordinateY() * definition.mass() * 4.5
                        / definition.supports().size());
                VehicleVector buoyancy = new VehicleVector(0, lift, 0);
                force = force.add(buoyancy);
                torque = torque.add(arm.cross(buoyancy));
                body.floating = true;
            }
        }
        body.grounded = contacts > 0;
        VehicleVector localVelocity = body.localVelocity();
        double steeringTarget = input.sideways();
        body.steering += Math.clamp(steeringTarget - body.steering, -seconds * 3, seconds * 3);
        boolean running = body.engineRunning && body.fuel > 0;
        switch (definition.kind()) {
            case CAR -> {
                double throttle = input.occupied() && input.forward() > 0 && !input.jump() ? input.forward() : 0;
                body.throttle = throttle;
                body.handbrake = input.jump() || body.transmission.selector == VehicleTransmission.Selector.P;
                body.brake = input.forward() < 0 || !input.occupied() || body.handbrake ? 1 : 0;
                double drive = body.transmission.driveForce(definition.engine(), localVelocity.coordinateZ(), throttle, running, seconds);
                if (body.grounded) {
                    double budget = definition.mass() * 9.81 * definition.grip() * contacts / definition.supports().size();
                    double brake = body.brake;
                    double lateral = -localVelocity.coordinateX() * definition.mass() * (input.jump() ? 1.2 : 6);
                    double longitudinal = drive - Math.clamp(localVelocity.coordinateZ() * definition.mass() * (brake * 10 + 0.08), -budget, budget);
                    VehicleVector tire = new VehicleVector(lateral, 0, longitudinal).limited(budget);
                    force = force.add(tire.rotate(body.orientation));
                    double wheelbase = Math.max(1, definition.colliders().getFirst().halfSize().coordinateZ() * 1.6);
                    double angle = -body.steering * 0.55 / (1 + Math.abs(localVelocity.coordinateZ()) * 0.06);
                    double targetYaw = localVelocity.coordinateZ() * Math.tan(angle) / wheelbase;
                    torque = torque.add(new VehicleVector(0, (targetYaw - body.angularVelocity.coordinateY()) * definition.inertia().coordinateY() * 5, 0)
                            .rotate(body.orientation));
                }
                body.wheelAngle += localVelocity.coordinateZ() / definition.engine().wheelRadius() * seconds;
            }
            case BOAT -> {
                if (input.occupied()) {
                    double next = Math.clamp(body.throttle + input.forward() * seconds * 0.6, -0.5, 1);
                    if (next < 0 && localVelocity.coordinateZ() > 1 || next > 0 && localVelocity.coordinateZ() < -1) next = 0;
                    body.throttle = next;
                } else body.throttle *= Math.exp(-seconds * 3);
                if (body.floating) {
                    force = force.add(new VehicleVector(-localVelocity.coordinateX() * definition.mass() * 2, 0,
                            (running ? body.throttle * definition.engine().peakTorque() * 18 : 0)
                                    - localVelocity.coordinateZ() * Math.abs(localVelocity.coordinateZ()) * definition.mass() * 0.018)
                            .rotate(body.orientation));
                    torque = torque.add(new VehicleVector(0, -body.steering * localVelocity.coordinateZ() * definition.inertia().coordinateY() * 0.3, 0)
                            .rotate(body.orientation));
                }
                updatePropellerRpm(body, running, seconds);
            }
            case PLANE -> {
                VehicleVector air = body.velocity.subtract(world.wind());
                VehicleVector localAir = air.rotate(new Quaterniond(body.orientation).conjugate());
                double airspeed = air.length();
                double angle = Math.atan2(-localAir.coordinateY(), Math.max(0.01, localAir.coordinateZ()));
                body.stalled = localAir.coordinateZ() > 2 && Math.abs(angle) > 0.3;
                double coefficient = Math.clamp(0.15 + angle * 4.5, -definition.lift(), definition.lift());
                if (body.stalled) coefficient *= Math.max(0.08, 1 - (Math.abs(angle) - 0.3) * 2.5);
                double dynamic = 0.5 * 1.225 * airspeed * airspeed * definition.wingArea();
                VehicleVector liftDirection = body.up().subtract(air.normalized().multiply(body.up().dot(air.normalized()))).normalized();
                if (localAir.coordinateZ() > 0) force = force.add(liftDirection.multiply(dynamic * coefficient));
                force = force.add(air.normalized().multiply(-dynamic * (0.03 + coefficient * coefficient * 0.08)));
                force = force.add(body.forward().multiply(running ? body.throttle * definition.engine().peakTorque() * 8 : 0));
                double authority = Math.clamp(airspeed / 15, 0, 1);
                VehicleVector targetRates = new VehicleVector(input.forward() * 0.7 * authority,
                        -input.sideways() * 0.15 * authority, input.sideways() * 1.1 * authority);
                VehicleVector rates = targetRates.subtract(body.angularVelocity).multiply(2);
                if (body.assisted && input.occupied() && input.forward() == 0 && input.sideways() == 0) {
                    VehicleVector leveling = body.up().cross(VehicleVector.UP).rotate(new Quaterniond(body.orientation).conjugate());
                    rates = rates.add(new VehicleVector(leveling.coordinateX(), 0, leveling.coordinateZ()).multiply(1.5 * authority));
                }
                torque = torque.add(new VehicleVector(rates.coordinateX() * definition.inertia().coordinateX(), rates.coordinateY() * definition.inertia().coordinateY(),
                        rates.coordinateZ() * definition.inertia().coordinateZ()).rotate(body.orientation));
                if (body.grounded) {
                    force = force.add(new VehicleVector(-localVelocity.coordinateX() * definition.mass() * 4, 0,
                            -localVelocity.coordinateZ() * definition.mass() * (input.jump() || !input.occupied() ? 3 : 0.02))
                            .rotate(body.orientation));
                    torque = torque.add(new VehicleVector(0, -input.sideways() * localVelocity.coordinateZ() * definition.inertia().coordinateY() * 0.2, 0)
                            .rotate(body.orientation));
                }
                updatePropellerRpm(body, running, seconds);
            }
        }
        force = force.add(body.velocity.multiply(-body.velocity.length() * definition.mass() * 0.0015));
        if (definition.kind() != VehicleDefinition.Kind.BOAT && world.waterDepth(body.position) > 0)
            force = force.add(body.velocity.multiply(-definition.mass() * 2));
        body.velocity = body.velocity.add(force.multiply(seconds / definition.mass())).limited(definition.maximumSpeed());
        VehicleVector localTorque = torque.rotate(new Quaterniond(body.orientation).conjugate());
        body.angularVelocity = body.angularVelocity.add(new VehicleVector(localTorque.coordinateX() / definition.inertia().coordinateX(),
                localTorque.coordinateY() / definition.inertia().coordinateY(), localTorque.coordinateZ() / definition.inertia().coordinateZ()).multiply(seconds))
                .multiply(Math.exp(-seconds * (body.floating ? body.assisted ? 2 : 0.4 : body.grounded ? 2.5 : 0.15))).limited(2.5);
        body.wheelAngle %= 2 * Math.PI;
        body.propellerAngle = (body.propellerAngle + body.transmission.rpm * Math.PI / 30 * seconds) % (2 * Math.PI);
        if (running) body.fuel = Math.max(0, body.fuel - definition.consumption() * (0.15 + Math.abs(body.throttle)) * seconds);
        Quaterniond next = new Quaterniond(body.orientation).rotateXYZ(body.angularVelocity.coordinateX() * seconds,
                body.angularVelocity.coordinateY() * seconds, body.angularVelocity.coordinateZ() * seconds).normalize();
        return new Motion(body.velocity.multiply(seconds), next);
    }

    private void updatePropellerRpm(VehicleBody body, boolean running, double seconds) {
        double target = running ? body.definition.engine().idleRpm() + Math.abs(body.throttle)
                * (body.definition.engine().redlineRpm() - body.definition.engine().idleRpm()) * 0.85 : 0;
        body.transmission.rpm += (target - body.transmission.rpm) * Math.min(1, seconds * 4);
    }
}
