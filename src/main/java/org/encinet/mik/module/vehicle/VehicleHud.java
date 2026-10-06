package org.encinet.mik.module.vehicle;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class VehicleHud {
    record Car(VehicleTransmission.Mode mode, VehicleTransmission.Selector selector, int actualGear, int targetGear,
               int gearCount, double shiftRemaining, double shiftProgress, double brake, boolean handbrake,
               double steering, double signedSpeed) { }
    record Energy(boolean engineRunning, double redline, double amount, double capacity, double consumption) {
        double enduranceSeconds() { return consumption > 0 ? amount / consumption : Double.NaN; }
        double rangeKilometers(double speed) { return consumption > 0 && speed > 0.1 ? speed * enduranceSeconds() / 1000 : Double.NaN; }
    }
    record Frame(VehicleDefinition.Kind kind, double speed, double altitude, double climb,
                 double heading, String gear, double rpm, double throttle, double fuel,
                 double health, boolean assisted, String warning, Car car, Energy energy,
                 VehicleVector origin, double maximumSpeed, boolean grounded, boolean floating) {
        static Frame of(VehicleBody body) {
            String warning = body.health <= 0 ? "DISABLED" : body.fuel <= 0 ? "NO_FUEL"
                    : body.stalled ? "STALL" : body.blocked ? "CONTACT"
                    : body.definition.kind() == VehicleDefinition.Kind.BOAT && body.grounded ? "GROUNDED"
                    : !body.transmission.warning.isEmpty() ? body.transmission.warning
                    : body.fuel / body.definition.fuelCapacity() < 0.1 ? "LOW_FUEL" : body.health <= 20 ? "DAMAGE"
                    : body.engineRunning ? "" : "ENGINE_OFF";
            VehicleTransmission transmission = body.transmission;
            double progress = transmission.shiftRemaining > 0
                    ? 1 - Math.clamp(transmission.shiftRemaining / body.definition.engine().shiftSeconds(), 0, 1) : 0;
            Car car = new Car(transmission.mode, transmission.selector, transmission.gear, transmission.targetGear,
                    body.definition.engine().ratios().size(), transmission.shiftRemaining, progress, body.brake,
                    body.handbrake || transmission.selector == VehicleTransmission.Selector.P, body.steering, body.localVelocity().coordinateZ());
            boolean running = body.engineRunning && body.fuel > 0 && body.health > 0;
            Energy energy = new Energy(running, body.definition.engine().redlineRpm(), body.fuel, body.definition.fuelCapacity(),
                    running ? body.definition.consumption() * (0.15 + Math.abs(body.throttle)) : 0);
            return new Frame(body.definition.kind(), body.velocity.length(), body.position.coordinateY(), body.velocity.coordinateY(),
                    (Math.toDegrees(Math.atan2(body.forward().coordinateX(), -body.forward().coordinateZ())) + 360) % 360,
                    body.transmission.label(), body.transmission.rpm, body.throttle,
                    body.fuel / body.definition.fuelCapacity(), body.health, body.assisted, warning, car, energy,
                    body.origin(), body.definition.maximumSpeed(), body.grounded, body.floating);
        }
    }
    private record Session(BossBar primary, BossBar secondary) { }
    private final LanguageService language;
    private final Map<UUID, Session> sessions = new HashMap<>();

    VehicleHud(LanguageService language) { this.language = language; }

    void show(Player player, Frame frame, double redline) {
        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar primary = BossBar.bossBar(Component.empty(), 0, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_20);
            BossBar secondary = BossBar.bossBar(Component.empty(), 1, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            player.showBossBar(primary);
            player.showBossBar(secondary);
            return new Session(primary, secondary);
        });
        String title = switch (frame.kind()) {
            case CAR -> language.t(player, Message.VEHICLE_HUD_CAR, decimal(frame.speed() * 3.6), frame.gear(), whole(frame.rpm()),
                    language.t(player, frame.car().mode() == VehicleTransmission.Mode.AUTOMATIC ? Message.VEHICLE_DASH_AUTOMATIC : Message.VEHICLE_DASH_MANUAL));
            case BOAT -> language.t(player, Message.VEHICLE_HUD_BOAT, decimal(frame.speed() * 1.94384), whole(frame.throttle() * 100), whole(frame.heading()));
            case PLANE -> language.t(player, Message.VEHICLE_HUD_PLANE, decimal(frame.speed() * 3.6), whole(frame.altitude()),
                    decimal(frame.climb()), whole(frame.throttle() * 100), language.t(player,
                    frame.assisted() ? Message.VEHICLE_DASH_ASSISTED : Message.VEHICLE_DASH_UNASSISTED));
        };
        session.primary().name(Component.text(title)).progress((float) Math.clamp(frame.kind() == VehicleDefinition.Kind.CAR
                ? frame.rpm() / redline : Math.abs(frame.throttle()), 0, 1));
        session.primary().color(frame.kind() == VehicleDefinition.Kind.CAR && frame.rpm() >= redline * 0.9
                ? BossBar.Color.RED : BossBar.Color.BLUE);
        session.secondary().name(Component.text(language.t(player, Message.VEHICLE_HUD_STATUS,
                whole(frame.fuel() * 100), whole(frame.health()), language.t(player, statusMessage(frame.warning(), frame.energy().engineRunning())))))
                .progress((float) Math.clamp(frame.fuel(), 0, 1))
                .color(switch (frame.warning()) {
                    case "DISABLED", "NO_FUEL", "STALL" -> BossBar.Color.RED;
                    case "ENGINE_OFF" -> BossBar.Color.WHITE;
                    case "" -> BossBar.Color.GREEN;
                    default -> BossBar.Color.YELLOW;
                });
    }
    static Message statusMessage(String code, boolean running) {
        return switch (code) {
            case "DISABLED" -> Message.VEHICLE_STATUS_DISABLED;
            case "NO_FUEL" -> Message.VEHICLE_STATUS_NO_FUEL;
            case "STALL" -> Message.VEHICLE_STATUS_STALL;
            case "CONTACT" -> Message.VEHICLE_STATUS_CONTACT;
            case "GROUNDED" -> Message.VEHICLE_STATUS_GROUNDED;
            case "STOP_FIRST" -> Message.VEHICLE_STATUS_STOP_FIRST;
            case "OVERREV" -> Message.VEHICLE_STATUS_OVERREV;
            case "SHIFTING" -> Message.VEHICLE_STATUS_SHIFTING;
            case "MANUAL_ONLY" -> Message.VEHICLE_STATUS_MANUAL_ONLY;
            case "GEAR_LIMIT" -> Message.VEHICLE_STATUS_GEAR_LIMIT;
            case "LOW_FUEL" -> Message.VEHICLE_STATUS_LOW_FUEL;
            case "DAMAGE" -> Message.VEHICLE_STATUS_DAMAGE;
            case "ENGINE_OFF" -> Message.VEHICLE_STATUS_ENGINE_OFF;
            case "ENGINE_ON" -> Message.VEHICLE_STATUS_ENGINE_ON;
            default -> running ? Message.VEHICLE_STATUS_ENGINE_ON : Message.VEHICLE_STATUS_ENGINE_OFF;
        };
    }

    void clear(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        player.hideBossBar(session.primary());
        player.hideBossBar(session.secondary());
    }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String whole(double value) { return String.format(Locale.ROOT, "%.0f", value); }
}
