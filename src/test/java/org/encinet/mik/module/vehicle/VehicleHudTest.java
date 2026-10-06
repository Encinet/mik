package org.encinet.mik.module.vehicle;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleHudTest {
    @Test void screenBarsShowModeDuringReverseWarnClearlyAndOnlyClearTheirOwnSession() {
        var shown = new java.util.ArrayList<net.kyori.adventure.bossbar.BossBar>();
        var hidden = new java.util.ArrayList<net.kyori.adventure.bossbar.BossBar>();
        var playerId = java.util.UUID.randomUUID();
        var player = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class}, (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getUniqueId": return playerId;
                        case "showBossBar": shown.add((net.kyori.adventure.bossbar.BossBar) arguments[0]); return null;
                        case "hideBossBar": hidden.add((net.kyori.adventure.bossbar.BossBar) arguments[0]); return null;
                        case "equals": return proxy == arguments[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "HUD test player";
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        var language = new org.encinet.mik.module.i18n.LanguageService(null) {
            @Override public String t(org.bukkit.entity.Player viewer, org.encinet.mik.module.i18n.Message message, Object... arguments) {
                return message.name() + " " + java.util.Arrays.toString(arguments);
            }
        };
        var hud = new VehicleHud(language);
        var body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        body.engineRunning = true;
        body.transmission.selector = VehicleTransmission.Selector.R;
        body.transmission.gear = -1;
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.rpm = body.definition.engine().redlineRpm() * 0.95;
        hud.show(player, VehicleHud.Frame.of(body), body.definition.engine().redlineRpm());
        assertEquals(2, shown.size());
        var primary = shown.getFirst();
        assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(primary.name()).contains("VEHICLE_DASH_MANUAL"));
        assertEquals(net.kyori.adventure.bossbar.BossBar.Color.RED, primary.color());
        assertEquals(0.95, primary.progress(), 1.0E-6);
        body.transmission.mode = VehicleTransmission.Mode.AUTOMATIC;
        body.fuel = 0;
        hud.show(player, VehicleHud.Frame.of(body), body.definition.engine().redlineRpm());
        assertEquals(2, shown.size());
        assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(primary.name()).contains("VEHICLE_DASH_AUTOMATIC"));
        assertEquals(net.kyori.adventure.bossbar.BossBar.Color.RED, shown.getLast().color());
        hud.clear(player);
        assertEquals(shown, hidden);
        hud.clear(player);
        assertEquals(2, hidden.size());
    }
    @Test void extendedCarReadingsAreFrozenTogetherWithTheFrame() {
        var body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(1, 2, 3));
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.selector = VehicleTransmission.Selector.D;
        body.transmission.gear = 2;
        body.transmission.targetGear = 3;
        body.transmission.shiftRemaining = body.definition.engine().shiftSeconds() / 2;
        body.brake = 1;
        body.steering = -0.25;
        body.velocity = new VehicleVector(0, 0, -5);
        var frame = VehicleHud.Frame.of(body);
        body.transmission.gear = 3;
        body.steering = 0;
        body.brake = 0;
        assertEquals(VehicleTransmission.Mode.MANUAL, frame.car().mode());
        assertEquals(2, frame.car().actualGear());
        assertEquals(3, frame.car().targetGear());
        assertEquals(0.5, frame.car().shiftProgress(), 1.0E-9);
        assertEquals(1, frame.car().brake());
        assertEquals(-0.25, frame.car().steering());
        assertEquals(-5, frame.car().signedSpeed());
        assertEquals(new VehicleVector(1, 2, 3), frame.origin());
    }

    @Test void fuelEstimatesUseTheSameConsumptionModelAsPhysicsAndHaveNoFakeInfinity() {
        var body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        var stopped = VehicleHud.Frame.of(body);
        assertEquals(0, stopped.energy().consumption());
        assertTrue(Double.isNaN(stopped.energy().enduranceSeconds()));
        assertTrue(Double.isNaN(stopped.energy().rangeKilometers(10)));
        body.engineRunning = true;
        body.throttle = 0.5;
        body.fuel = 25;
        var running = VehicleHud.Frame.of(body);
        assertEquals(body.definition.consumption() * 0.65, running.energy().consumption(), 1.0E-9);
        assertEquals(25 / running.energy().consumption(), running.energy().enduranceSeconds(), 1.0E-9);
        assertTrue(Double.isNaN(running.energy().rangeKilometers(0)));
        assertEquals(10 * running.energy().enduranceSeconds() / 1000, running.energy().rangeKilometers(10), 1.0E-9);
    }

    @Test void pedalReadingsComeFromAppliedPhysicsInsteadOfUnrelatedPlayerState() {
        var body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(0, 1, 0));
        body.transmission.selector = VehicleTransmission.Selector.N;
        var physics = new VehiclePhysics();
        var environment = VehicleFixtures.environment(0, 0);
        physics.step(body, new VehicleInput(-1, 0, false, true), environment, 0.01);
        assertEquals(1, VehicleHud.Frame.of(body).car().brake());
        assertFalse(VehicleHud.Frame.of(body).car().handbrake());
        physics.step(body, new VehicleInput(1, 0, true, true), environment, 0.01);
        assertTrue(VehicleHud.Frame.of(body).car().handbrake());
        assertEquals(0, body.throttle);
        physics.step(body, new VehicleInput(1, 0, false, true), environment, 0.01);
        assertEquals(1, body.throttle);
        assertEquals(0, body.brake);
    }

    @Test void lowFuelAndCriticalDamageUseLocalizedWarningKeys() {
        var body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        body.engineRunning = true;
        body.fuel = 5;
        assertEquals("LOW_FUEL", VehicleHud.Frame.of(body).warning());
        body.fuel = 50;
        body.health = 10;
        assertEquals("DAMAGE", VehicleHud.Frame.of(body).warning());
        assertEquals(org.encinet.mik.module.i18n.Message.VEHICLE_STATUS_OVERREV, VehicleHud.statusMessage("OVERREV", false));
        assertEquals(org.encinet.mik.module.i18n.Message.VEHICLE_STATUS_ENGINE_OFF, VehicleHud.statusMessage("ENGINE_OFF", true));
    }
    @Test void headingUsesNorthZeroAndClockwiseCompassDegrees() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.BOAT, VehicleVector.ZERO);
        assertEquals(180, VehicleHud.Frame.of(body).heading(), 1.0E-6);
        body.orientation.set(new Quaterniond().rotateY(Math.PI));
        assertEquals(0, VehicleHud.Frame.of(body).heading(), 1.0E-6);
        body.orientation.set(new Quaterniond().rotateY(Math.PI / 2));
        assertEquals(90, VehicleHud.Frame.of(body).heading(), 1.0E-6);
        body.orientation.set(new Quaterniond().rotateY(-Math.PI / 2));
        assertEquals(270, VehicleHud.Frame.of(body).heading(), 1.0E-6);
    }

    @Test void frameIsFrozenAndUsesOnePhysicalSnapshot() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        body.velocity = new VehicleVector(0, 0, 10);
        body.transmission.gear = 2;
        body.transmission.selector = VehicleTransmission.Selector.D;
        body.fuel = 25;
        body.engineRunning = true;
        VehicleHud.Frame frame = VehicleHud.Frame.of(body);
        body.velocity = VehicleVector.ZERO;
        body.transmission.gear = 1;
        body.fuel = 0;
        assertEquals(10, frame.speed());
        assertEquals("D2", frame.gear());
        assertEquals(0.25, frame.fuel());
        assertEquals("", frame.warning());
    }

    @Test void dangerousWarningsTakePriorityOverContactOrShiftHints() {
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.PLANE, VehicleVector.ZERO);
        body.engineRunning = true;
        body.stalled = true;
        body.blocked = true;
        assertEquals("STALL", VehicleHud.Frame.of(body).warning());
        body.fuel = 0;
        assertEquals("NO_FUEL", VehicleHud.Frame.of(body).warning());
        body.health = 0;
        assertEquals("DISABLED", VehicleHud.Frame.of(body).warning());
    }
}
