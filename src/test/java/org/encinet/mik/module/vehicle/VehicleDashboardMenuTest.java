package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAnchorMode;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPresentation;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.encinet.mik.module.menu.FloatingMenuSpatialFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VehicleDashboardMenuTest {
    private final List<String> commands = new ArrayList<>();
    private final AtomicReference<VehicleDashboardMenu.Page> page = new AtomicReference<>();

    @Test void cockpitFollowsPositionWithoutCapturingDrivingKeysOrOpeningNativeForms() {
        var definition = menu(car(), VehicleDashboardMenu.Page.DRIVE);
        assertEquals(FloatingMenuAnchorMode.FOLLOW_PLAYER_POSITION, definition.anchorMode());
        assertEquals(FloatingMenuPresentation.SPATIAL_REQUIRED, definition.presentation());
        assertEquals(FloatingMenuSpatialFrame.FRONT_ARC, definition.spatialFrame());
        assertTrue(definition.triggers().isEmpty());
        assertFalse(definition.titleVisible());
        assertEquals(0, definition.animation().idleAmplitude());
        assertEquals(0, definition.animation().idleSpeed());
        assertTrue(definition.entries().containsKey("telemetry"));
        assertTrue(definition.entries().containsKey("engine"));
        assertTrue(definition.entries().containsKey("mode"));
    }

    @Test void gearboxProvidesAllSelectorsModesAndConfiguredForwardGears() {
        var body = car();
        var definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        for (var selector : VehicleTransmission.Selector.values()) assertTrue(definition.entries().containsKey("selector:" + selector));
        assertTrue(definition.entries().get("automatic").selected());
        assertFalse(definition.entries().get("manual").selected());
        assertEquals(body.definition.engine().ratios().size(), definition.entries().keySet().stream().filter(id -> id.startsWith("gear:")).count());
        assertTrue(definition.entries().entrySet().stream().filter(entry -> entry.getKey().startsWith("gear:")).allMatch(entry -> !entry.getValue().enabled()));
        invoke(definition, "manual");
        assertEquals(List.of("mode manual"), commands);
    }

    @Test void manualButtonsDispatchTheSameControlsAsCommands() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.gear = 2;
        body.transmission.targetGear = 2;
        var definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        assertTrue(definition.entries().get("manual").selected());
        assertTrue(definition.entries().get("gear:2").selected());
        invoke(definition, "gear:3");
        invoke(definition, "up");
        invoke(definition, "down");
        invoke(definition, "selector:N");
        invoke(definition, "automatic");
        invoke(definition, "engine");
        assertEquals(List.of("gear 3", "gear up", "gear down", "gear N", "mode automatic", "engine"), commands);
    }

    @Test void movingOrShiftingDisablesUnsafeControlsWithVisibleReasons() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.selector = VehicleTransmission.Selector.D;
        body.velocity = new VehicleVector(1, 0, 0);
        var moving = menu(body, VehicleDashboardMenu.Page.DRIVE);
        assertFalse(moving.entries().get("selector:P").enabled());
        assertFalse(moving.entries().get("selector:R").enabled());
        assertNotNull(moving.entries().get("selector:P").disabledReason());
        assertTrue(moving.entries().get("selector:N").enabled());
        body.transmission.shiftRemaining = 0.2;
        var shifting = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        assertFalse(shifting.entries().get("gear:1").enabled());
        assertFalse(shifting.entries().get("up").enabled());
        assertFalse(shifting.entries().get("selector:N").enabled());
        assertTrue(shifting.entries().get("automatic").enabled());
    }

    @Test void numericIndicatorsAndLocalizedValuesAreNotComponentDebugStrings() {
        var body = car();
        body.velocity = new VehicleVector(0, 0, 10);
        body.fuel = 25;
        body.brake = 1;
        var definition = menu(body, VehicleDashboardMenu.Page.DRIVE);
        String readings = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("telemetry").label());
        assertTrue(readings.contains("36.0"));
        assertTrue(readings.contains("25.0"));
        assertFalse(readings.contains(Message.VEHICLE_DASH_AUTOMATIC.name()));
        String modeLabel = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("mode").label());
        assertTrue(modeLabel.contains(Message.VEHICLE_DASH_MANUAL.name()));
        assertFalse(modeLabel.contains("TextComponentImpl"));
        assertFalse(readings.contains("TextComponentImpl"));
        assertArrayEquals(new Object[]{"Automatic", 2}, VehicleDashboardMenu.plainArguments(Component.text("Automatic"), 2));
    }

    @Test void detailPageIncludesFuelConditionOccupancyPositionAndEstimates() {
        var definition = menu(car(), VehicleDashboardMenu.Page.DETAILS);
        String readings = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("telemetry").label());
        for (Message key : List.of(Message.VEHICLE_DASH_FUEL, Message.VEHICLE_DASH_ENERGY, Message.VEHICLE_DASH_HEALTH,
                Message.VEHICLE_DASH_POSITION, Message.VEHICLE_DASH_HEADING)) assertTrue(readings.contains(key.name()));
        assertEquals(6, readings.lines().count());
        assertTrue(readings.contains("—"));
        assertFalse(readings.contains("NaN"));
        assertFalse(readings.contains("Infinity"));
        invoke(definition, "page:GEARBOX");
        assertEquals(VehicleDashboardMenu.Page.GEARBOX, page.get());
    }

    @Test void idleGearboxShowsGearAndRpmWithoutRepeatingSelectorModeOrGearCount() {
        var definition = menu(car(), VehicleDashboardMenu.Page.GEARBOX);
        String readings = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("telemetry").label());
        assertEquals(2, readings.lines().count());
        assertTrue(readings.contains(Message.VEHICLE_DASH_GEAR.name()));
        assertTrue(readings.contains(Message.VEHICLE_DASH_RPM.name()));
        assertFalse(readings.contains(Message.VEHICLE_DASH_SHIFT.name()));
        assertFalse(readings.contains(Message.VEHICLE_DASH_AUTOMATIC.name()));
        assertFalse(readings.contains(Message.VEHICLE_DASH_MANUAL.name()));
    }

    @Test void shiftingReplacesTheGearLineButDoesNotHideOtherWarnings() {
        var body = car();
        body.transmission.gear = 2;
        body.transmission.targetGear = 3;
        body.transmission.shiftRemaining = 0.2;
        body.transmission.warning = "SHIFTING";
        var definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        String readings = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("telemetry").label());
        assertEquals(2, readings.lines().count());
        assertTrue(readings.contains(Message.VEHICLE_DASH_SHIFT.name() + " [2, 3,"));
        assertFalse(readings.contains(Message.VEHICLE_DASH_GEAR.name()));
        assertFalse(readings.contains(Message.VEHICLE_STATUS_SHIFTING.name()));
        body.blocked = true;
        definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        readings = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("telemetry").label());
        assertTrue(readings.contains(Message.VEHICLE_DASH_SHIFT.name()));
        assertTrue(readings.contains(Message.VEHICLE_STATUS_CONTACT.name()));
    }

    @Test void ordinaryEngineStateAppearsOnTheButtonNotAsAnExtraReading() {
        var body = car();
        for (boolean running : List.of(false, true)) {
            body.engineRunning = running;
            for (var choice : VehicleDashboardMenu.Page.values()) {
                var definition = menu(body, choice);
                String readings = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("telemetry").label());
                assertFalse(readings.contains(Message.VEHICLE_STATUS_ENGINE_ON.name()));
                assertFalse(readings.contains(Message.VEHICLE_STATUS_ENGINE_OFF.name()));
                assertFalse(readings.startsWith("\n"));
                assertFalse(readings.endsWith("\n"));
                assertFalse(readings.contains("\n\n"));
                String label = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("engine").label());
                assertTrue(label.contains((running ? Message.VEHICLE_DASH_ENGINE_STOP : Message.VEHICLE_DASH_ENGINE_START).name()));
            }
        }
    }

    @Test void damageAndFuelWarningsRemainVisibleOnEveryPage() {
        var body = car();
        for (Message warning : List.of(Message.VEHICLE_STATUS_DISABLED, Message.VEHICLE_STATUS_NO_FUEL, Message.VEHICLE_STATUS_LOW_FUEL)) {
            body.health = warning == Message.VEHICLE_STATUS_DISABLED ? 0 : 100;
            body.fuel = warning == Message.VEHICLE_STATUS_NO_FUEL ? 0 : body.definition.fuelCapacity() * 0.05;
            for (var choice : VehicleDashboardMenu.Page.values()) {
                var definition = menu(body, choice);
                String readings = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("telemetry").label());
                assertEquals(1, readings.lines().filter(line -> line.contains(warning.name())).count());
            }
        }
    }

    @Test void modeToggleNamesTheDestinationForCarsBoatsAndPlanes() {
        var body = car();
        for (var mode : VehicleTransmission.Mode.values()) {
            body.transmission.mode = mode;
            var definition = menu(body, VehicleDashboardMenu.Page.DRIVE);
            String label = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("mode").label());
            assertTrue(label.contains((mode == VehicleTransmission.Mode.AUTOMATIC ? Message.VEHICLE_DASH_MANUAL : Message.VEHICLE_DASH_AUTOMATIC).name()));
        }
        for (var kind : List.of(VehicleDefinition.Kind.BOAT, VehicleDefinition.Kind.PLANE)) {
            body = VehicleFixtures.body(kind, VehicleVector.ZERO);
            for (boolean assisted : List.of(false, true)) {
                body.assisted = assisted;
                var definition = menu(body, VehicleDashboardMenu.Page.DRIVE);
                String label = PlainTextComponentSerializer.plainText().serialize(definition.entries().get("mode").label());
                assertTrue(label.contains((assisted ? Message.VEHICLE_DASH_UNASSISTED : Message.VEHICLE_DASH_ASSISTED).name()));
            }
        }
    }

    @Test void boatsAndPlanesNeverShowCarGearSelectorsOrManualGearNumbers() {
        for (var kind : List.of(VehicleDefinition.Kind.BOAT, VehicleDefinition.Kind.PLANE)) {
            var definition = menu(VehicleFixtures.body(kind, VehicleVector.ZERO), VehicleDashboardMenu.Page.DRIVE);
            assertFalse(definition.entries().containsKey("page:GEARBOX"));
            assertFalse(definition.entries().keySet().stream().anyMatch(id -> id.startsWith("selector:") || id.startsWith("gear:")));
            invoke(definition, "up");
        }
        assertEquals(List.of("throttle up", "throttle up"), commands);
    }

    @Test void selectorsAndReadingsRemainInSeparateColumnsAcrossEveryPage() {
        for (var choice : VehicleDashboardMenu.Page.values()) {
            var definition = menu(car(), choice);
            List<FloatingMenuLayout.Node> nodes = definition.entries().values().stream().map(entry ->
                    new FloatingMenuLayout.Node(entry.id(), entry.style(), entry.role(), entry.region(), new FloatingMenuSize(1, 0.4))).toList();
            for (int index = 0; index < nodes.size(); index++) {
                var pose = definition.layout().pose(new FloatingMenuLayout.Context(index, nodes));
                boolean reading = List.of("readings", "model").contains(nodes.get(index).region());
                assertTrue(reading ? pose.right() + nodes.get(index).size().width() / 2 <= -0.55 + 1.0E-9
                        : pose.right() - nodes.get(index).size().width() / 2 >= 0.55 - 1.0E-9);
                assertTrue(Double.isFinite(pose.up()));
            }
        }
    }

    private FloatingMenuDefinition menu(VehicleBody body, VehicleDashboardMenu.Page choice) {
        var view = new VehicleDashboardMenu.View(UUID.randomUUID(), body.definition.id(), VehicleHud.Frame.of(body), 1, body.definition.seats().size());
        return VehicleDashboardMenu.create(view, choice, (message, values) -> Component.text(message.name() + " " + Arrays.toString(values)),
                (player, command) -> commands.add(command), page::set).build();
    }
    private void invoke(FloatingMenuDefinition definition, String id) {
        definition.entries().get(id).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
    }
    private VehicleBody car() { return VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO); }
}
