package org.encinet.mik.feature.world.vehicle;

import java.util.Locale;

final class VehicleControls {
    sealed interface Action permits Toggle, Mode, Select, Gear, StepGear, Throttle { }
    enum Toggle implements Action { ENGINE, MODE }
    record Mode(boolean automatic) implements Action { }
    record Select(VehicleTransmission.Selector selector) implements Action { }
    record Gear(int value) implements Action { }
    record StepGear(int direction) implements Action { }
    record Throttle(int direction) implements Action { }

    static String apply(VehicleBody body, Action action) {
        return switch (action) {
            case Toggle toggle -> {
                if (toggle == Toggle.MODE) {
                    if (body.definition.kind() == VehicleDefinition.Kind.CAR) body.transmission.toggle();
                    else body.assisted = !body.assisted;
                    yield body.definition.kind() == VehicleDefinition.Kind.CAR ? body.transmission.mode.name() : body.assisted ? "ASSIST" : "MANUAL";
                }
                if (!body.engineRunning && (body.fuel <= 0 || body.health <= 0))
                    throw new IllegalArgumentException(body.health <= 0 ? "DISABLED" : "NO_FUEL");
                body.engineRunning = !body.engineRunning;
                yield body.engineRunning ? "ENGINE_ON" : "ENGINE_OFF";
            }
            case Mode mode -> {
                if (body.definition.kind() == VehicleDefinition.Kind.CAR)
                    body.transmission.setMode(mode.automatic() ? VehicleTransmission.Mode.AUTOMATIC : VehicleTransmission.Mode.MANUAL);
                else body.assisted = mode.automatic();
                yield body.definition.kind() == VehicleDefinition.Kind.CAR ? body.transmission.mode.name() : body.assisted ? "ASSIST" : "MANUAL";
            }
            case Select select -> {
                requireCar(body);
                var transmission = body.transmission;
                var selector = select.selector();
                if ((selector == VehicleTransmission.Selector.P || selector == VehicleTransmission.Selector.R
                        || transmission.selector == VehicleTransmission.Selector.R && selector == VehicleTransmission.Selector.D)
                        && body.velocity.length() > 0.5) transmission.warning = "STOP_FIRST";
                else transmission.select(selector, body.localVelocity().coordinateZ(), body.definition.engine());
                yield gearResult(body);
            }
            case Gear gear -> {
                requireCar(body);
                var transmission = body.transmission;
                if (transmission.mode != VehicleTransmission.Mode.MANUAL) throw new IllegalArgumentException("MANUAL_ONLY");
                if (!blockedDirection(body, gear.value()) && transmission.request(gear.value(), body.localVelocity().coordinateZ(), body.definition.engine()))
                    transmission.selector = transmission.targetGear < 0 ? VehicleTransmission.Selector.R
                            : transmission.targetGear == 0 ? VehicleTransmission.Selector.N : VehicleTransmission.Selector.D;
                yield gearResult(body);
            }
            case StepGear step -> { requireCar(body); stepGear(body, step.direction()); yield gearResult(body); }
            case Throttle throttle -> {
                if (body.definition.kind() == VehicleDefinition.Kind.CAR) throw new IllegalArgumentException("Use W/S for car pedals");
                body.throttle = Math.clamp(body.throttle + Integer.signum(throttle.direction()) * 0.05,
                        body.definition.kind() == VehicleDefinition.Kind.BOAT ? -0.5 : 0, 1);
                yield String.format(Locale.ROOT, "%.0f%%", body.throttle * 100);
            }
        };
    }

    private static void requireCar(VehicleBody body) {
        if (body.definition.kind() != VehicleDefinition.Kind.CAR) throw new IllegalArgumentException("Cars only");
    }

    private static String gearResult(VehicleBody body) {
        if (!body.transmission.warning.isEmpty()) throw new IllegalArgumentException(body.transmission.warning);
        return body.transmission.label();
    }

    static boolean stepGear(VehicleBody body, int direction) {
        VehicleTransmission transmission = body.transmission;
        int base = transmission.shiftRemaining > 0 ? transmission.targetGear : transmission.gear;
        if (transmission.mode != VehicleTransmission.Mode.MANUAL || !blockedDirection(body, base + Integer.signum(direction)))
            transmission.stepGear(direction, body.localVelocity().coordinateZ(), body.definition.engine());
        return transmission.warning.isEmpty();
    }

    private static boolean blockedDirection(VehicleBody body, int gear) {
        if ((gear == -1 || gear > 0 && body.transmission.selector == VehicleTransmission.Selector.R) && body.velocity.length() > 0.5) {
            body.transmission.warning = "STOP_FIRST";
            return true;
        }
        return false;
    }

}
