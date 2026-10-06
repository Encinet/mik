package org.encinet.mik.module.vehicle;

import java.util.Locale;

final class VehicleControls {
    static String apply(VehicleBody body, String input) {
        String[] args = input.strip().split("\\s+");
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "engine" -> {
                exact(args, 1);
                if (!body.engineRunning && (body.fuel <= 0 || body.health <= 0))
                    throw new IllegalArgumentException(body.health <= 0 ? "DISABLED" : "NO_FUEL");
                body.engineRunning = !body.engineRunning;
                yield body.engineRunning ? "ENGINE_ON" : "ENGINE_OFF";
            }
            case "mode" -> {
                if (args.length > 2) throw new IllegalArgumentException("mode [automatic|manual|assisted]");
                if (body.definition.kind() == VehicleDefinition.Kind.CAR) {
                    if (args.length == 1) body.transmission.toggle();
                    else body.transmission.setMode(switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "automatic", "auto" -> VehicleTransmission.Mode.AUTOMATIC;
                        case "manual" -> VehicleTransmission.Mode.MANUAL;
                        default -> throw new IllegalArgumentException("mode automatic|manual");
                    });
                    yield body.transmission.mode.name();
                }
                if (args.length == 1) body.assisted = !body.assisted;
                else body.assisted = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "assisted", "auto" -> true;
                    case "manual" -> false;
                    default -> throw new IllegalArgumentException("mode assisted|manual");
                };
                yield body.assisted ? "ASSIST" : "MANUAL";
            }
            case "gear" -> {
                exact(args, 2);
                if (body.definition.kind() != VehicleDefinition.Kind.CAR) throw new IllegalArgumentException("Cars only");
                VehicleTransmission transmission = body.transmission;
                String next = args[1].toUpperCase(Locale.ROOT);
                double speed = body.localVelocity().coordinateZ();
                if (next.equals("UP") || next.equals("DOWN")) stepGear(body, next.equals("UP") ? 1 : -1);
                else if (next.matches("-?\\d+")) {
                    if (transmission.mode != VehicleTransmission.Mode.MANUAL) throw new IllegalArgumentException("MANUAL_ONLY");
                    int gear = Integer.parseInt(next);
                    if (!blockedDirection(body, gear) && transmission.request(gear, speed, body.definition.engine()))
                        transmission.selector = transmission.targetGear < 0 ? VehicleTransmission.Selector.R
                                : transmission.targetGear == 0 ? VehicleTransmission.Selector.N : VehicleTransmission.Selector.D;
                } else {
                    VehicleTransmission.Selector selector = VehicleTransmission.Selector.valueOf(next);
                    if ((selector == VehicleTransmission.Selector.P || selector == VehicleTransmission.Selector.R
                            || transmission.selector == VehicleTransmission.Selector.R && selector == VehicleTransmission.Selector.D)
                            && body.velocity.length() > 0.5) transmission.warning = "STOP_FIRST";
                    else transmission.select(selector, speed, body.definition.engine());
                }
                if (!transmission.warning.isEmpty()) throw new IllegalArgumentException(transmission.warning);
                yield transmission.label();
            }
            case "throttle" -> {
                exact(args, 2);
                if (body.definition.kind() == VehicleDefinition.Kind.CAR) throw new IllegalArgumentException("Use W/S for car pedals");
                int direction = switch (args[1].toLowerCase(Locale.ROOT)) { case "up" -> 1; case "down" -> -1; default -> throw new IllegalArgumentException("throttle up|down"); };
                body.throttle = Math.clamp(body.throttle + direction * 0.05,
                        body.definition.kind() == VehicleDefinition.Kind.BOAT ? -0.5 : 0, 1);
                yield String.format(Locale.ROOT, "%.0f%%", body.throttle * 100);
            }
            default -> throw new IllegalArgumentException("Unknown driving control");
        };
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

    private static void exact(String[] args, int length) {
        if (args.length != length) throw new IllegalArgumentException("Invalid driving control arguments");
    }
}
