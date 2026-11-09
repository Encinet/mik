package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAnchorMode;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class VehicleDashboardMenuTest {
    private final List<VehicleControls.Action> commands = new ArrayList<>();
    private final AtomicReference<VehicleDashboardMenu.Page> page = new AtomicReference<>();

    @Test void cockpitFollowsPositionWithoutCapturingDrivingKeysOrOpeningNativeForms() {
        var definition = menu(car(), VehicleDashboardMenu.Page.DRIVE);
        assertEquals(FloatingMenuAnchorMode.INERTIAL_TOOLBAR, definition.anchorMode());
        assertEquals(FloatingMenuSpatialFrame.FRONT_ARC, definition.spatialFrame());
        assertTrue(definition.triggers().isEmpty());
        assertFalse(definition.titleVisible());
        assertEquals(0, definition.animation().idleAmplitude());
        assertEquals(0, definition.animation().idleSpeed());
        assertTrue((entry(definition, "telemetry") != null));
        assertTrue((entry(definition, "engine") != null));
        assertNull(entry(definition, "mode"));
        assertNull(entry(definition, "selector:P"));
        assertNull(entry(definition, "up"));
        assertEquals(3, PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label()).lines().count());
    }

    @Test void gearboxProvidesAllSelectorsModesAndConfiguredForwardGears() {
        var body = car();
        var definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        for (var selector : VehicleTransmission.Selector.values()) assertTrue((entry(definition, "selector:" + selector) != null));
        assertTrue(entry(definition, "automatic").selected());
        assertFalse(entry(definition, "manual").selected());
        assertEquals(body.definition.engine().ratios().size(), definition.entries().keySet().stream().filter(id -> id.contains("/gear:")).count());
        assertTrue(definition.entries().entrySet().stream().filter(entry -> entry.getKey().contains("/gear:")).allMatch(entry -> !entry.getValue().enabled()));
        invoke(definition, "manual");
        assertEquals(List.of(new VehicleControls.Mode(false)), commands);
    }

    @Test void manualButtonsDispatchTypedDrivingOperations() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.gear = 2;
        body.transmission.targetGear = 2;
        var definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        assertTrue(entry(definition, "manual").selected());
        assertTrue(entry(definition, "gear:2").selected());
        invoke(definition, "gear:3");
        invoke(definition, "up");
        invoke(definition, "down");
        invoke(definition, "selector:N");
        invoke(definition, "automatic");
        invoke(definition, "engine");
        assertEquals(List.of(new VehicleControls.Gear(3), new VehicleControls.StepGear(1), new VehicleControls.StepGear(-1), new VehicleControls.Select(VehicleTransmission.Selector.N), new VehicleControls.Mode(true), VehicleControls.Toggle.ENGINE), commands);
    }

    @Test void movingOrShiftingDisablesUnsafeControlsWithVisibleReasons() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        body.transmission.selector = VehicleTransmission.Selector.D;
        body.velocity = new VehicleVector(1, 0, 0);
        var moving = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        assertFalse(entry(moving, "selector:P").enabled());
        assertFalse(entry(moving, "selector:R").enabled());
        assertNotNull(entry(moving, "selector:P").disabledReason());
        assertTrue(entry(moving, "selector:N").enabled());
        body.transmission.shiftRemaining = 0.2;
        var shifting = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        assertFalse(entry(shifting, "gear:1").enabled());
        assertFalse(entry(shifting, "up").enabled());
        assertFalse(entry(shifting, "selector:N").enabled());
        assertTrue(entry(shifting, "automatic").enabled());
    }

    @Test void numericIndicatorsAndLocalizedValuesAreNotComponentDebugStrings() {
        var body = car();
        body.velocity = new VehicleVector(0, 0, 10);
        body.fuel = 25;
        body.brake = 1;
        var definition = menu(body, VehicleDashboardMenu.Page.DRIVE);
        String readings = PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label());
        assertTrue(readings.contains("36.0"));
        assertTrue(readings.contains("25.0"));
        assertFalse(readings.contains(Message.VEHICLE_DASH_AUTOMATIC.name()));
        var gearbox = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        String modeLabel = PlainTextComponentSerializer.plainText().serialize(entry(gearbox, "manual").label());
        assertTrue(modeLabel.contains(Message.VEHICLE_DASH_MANUAL.name()));
        assertFalse(modeLabel.contains("TextComponentImpl"));
        assertFalse(readings.contains("TextComponentImpl"));
        assertArrayEquals(new Object[]{"Automatic", 2}, VehicleDashboardMenu.plainArguments(Component.text("Automatic"), 2));
    }

    @Test void detailPageIncludesFuelConditionOccupancyPositionAndEstimates() {
        var definition = menu(car(), VehicleDashboardMenu.Page.DETAILS);
        String readings = PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label());
        for (Message key : List.of(Message.VEHICLE_DASH_FUEL, Message.VEHICLE_DASH_ENERGY, Message.VEHICLE_DASH_HEALTH,
                Message.VEHICLE_DASH_POSITION, Message.VEHICLE_DASH_HEADING)) assertTrue(readings.contains(key.name()));
        assertEquals(9, readings.lines().count());
        assertNull(entry(definition, "engine"));
        assertNull(entry(definition, "selector:P"));
        assertTrue(readings.contains("—"));
        assertFalse(readings.contains("NaN"));
        assertFalse(readings.contains("Infinity"));
        invoke(definition, "page:GEARBOX");
        assertEquals(VehicleDashboardMenu.Page.GEARBOX, page.get());
    }

    @Test void idleGearboxShowsGearAndRpmWithoutRepeatingSelectorModeOrGearCount() {
        var definition = menu(car(), VehicleDashboardMenu.Page.GEARBOX);
        String readings = PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label());
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
        String readings = PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label());
        assertEquals(2, readings.lines().count());
        assertTrue(readings.contains(Message.VEHICLE_DASH_SHIFT.name() + " [2, 3,"));
        assertFalse(readings.contains(Message.VEHICLE_DASH_GEAR.name()));
        assertFalse(readings.contains(Message.VEHICLE_STATUS_SHIFTING.name()));
        body.blocked = true;
        definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        readings = PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label());
        assertTrue(readings.contains(Message.VEHICLE_DASH_SHIFT.name()));
        assertTrue(readings.contains(Message.VEHICLE_STATUS_CONTACT.name()));
    }

    @Test void ordinaryEngineStateAppearsOnTheButtonNotAsAnExtraReading() {
        var body = car();
        for (boolean running : List.of(false, true)) {
            body.engineRunning = running;
            for (var choice : VehicleDashboardMenu.Page.values()) {
                var definition = menu(body, choice);
                String readings = PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label());
                assertFalse(readings.contains(Message.VEHICLE_STATUS_ENGINE_ON.name()));
                assertFalse(readings.contains(Message.VEHICLE_STATUS_ENGINE_OFF.name()));
                assertFalse(readings.startsWith("\n"));
                assertFalse(readings.endsWith("\n"));
                assertFalse(readings.contains("\n\n"));
                if (choice != VehicleDashboardMenu.Page.DETAILS) {
                    String label = PlainTextComponentSerializer.plainText().serialize(entry(definition, "engine").label());
                    assertTrue(label.contains((running ? Message.VEHICLE_DASH_ENGINE_STOP : Message.VEHICLE_DASH_ENGINE_START).name()));
                }
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
                String readings = PlainTextComponentSerializer.plainText().serialize(entry(definition, "telemetry").label());
                assertEquals(1, readings.lines().filter(line -> line.contains(warning.name())).count());
            }
        }
    }

    @Test void modeToggleNamesTheDestinationForCarsBoatsAndPlanes() {
        var body = car();
        for (var mode : VehicleTransmission.Mode.values()) {
            body.transmission.mode = mode;
            var definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
            assertEquals(mode == VehicleTransmission.Mode.AUTOMATIC, entry(definition, "automatic").selected());
            assertEquals(mode == VehicleTransmission.Mode.MANUAL, entry(definition, "manual").selected());
        }
        for (var kind : List.of(VehicleDefinition.Kind.BOAT, VehicleDefinition.Kind.PLANE)) {
            body = VehicleFixtures.body(kind, VehicleVector.ZERO);
            for (boolean assisted : List.of(false, true)) {
                body.assisted = assisted;
                var definition = menu(body, VehicleDashboardMenu.Page.DRIVE);
                String label = PlainTextComponentSerializer.plainText().serialize(entry(definition, "mode").label());
                assertTrue(label.contains((assisted ? Message.VEHICLE_DASH_UNASSISTED : Message.VEHICLE_DASH_ASSISTED).name()));
            }
        }
    }

    @Test void boatsAndPlanesNeverShowCarGearSelectorsOrManualGearNumbers() {
        for (var kind : List.of(VehicleDefinition.Kind.BOAT, VehicleDefinition.Kind.PLANE)) {
            var definition = menu(VehicleFixtures.body(kind, VehicleVector.ZERO), VehicleDashboardMenu.Page.DRIVE);
            assertFalse((entry(definition, "page:GEARBOX") != null));
            assertFalse(definition.entries().keySet().stream().anyMatch(id -> id.contains("/selector:") || id.contains("/gear:")));
            invoke(definition, "up");
        }
        assertEquals(List.of(new VehicleControls.Throttle(1), new VehicleControls.Throttle(1)), commands);
    }

    @Test void readingsAndToolsUseSeparatePlanesAcrossEveryPage() {
        for (var choice : VehicleDashboardMenu.Page.values()) {
            var definition = menu(car(), choice);
            var measured = frame(definition);
            assertDisjoint(measured);
            var reading = element(measured, "telemetry");
            var control = element(measured, choice == VehicleDashboardMenu.Page.GEARBOX ? "selector:P" : "page:DRIVE");
            assertTrue(reading.y() + reading.size().height() < control.y());
            assertEquals(choice == VehicleDashboardMenu.Page.DRIVE ? FloatingMenuSpatialFrame.FRONT_ARC : FloatingMenuSpatialFrame.IN_FRONT,
                    definition.spatialFrame());
        }
    }

    @Test void telemetryPassesNumbersToLocalizationAndManualDrivingOnlyShowsRelevantControls() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        var view = new VehicleDashboardMenu.View(UUID.randomUUID(), body.definition.id(), VehicleHud.Frame.of(body), 1, body.definition.seats().size());
        var arguments = new java.util.EnumMap<Message, Object[]>(Message.class);
        var definition = VehicleDashboardMenu.create(view, VehicleDashboardMenu.Page.DRIVE, (message, values) -> {
            arguments.put(message, values);
            return Component.text(message.name());
        }, (_, command) -> commands.add(command), page::set).build();
        assertTrue(Arrays.stream(arguments.get(Message.VEHICLE_DASH_SPEED)).allMatch(Number.class::isInstance));
        assertTrue(Arrays.stream(arguments.get(Message.VEHICLE_DASH_FUEL)).allMatch(Number.class::isInstance));
        assertEquals(org.encinet.mik.shared.menu.ui.UiRole.ACTION, entry(definition, "up").role());
        assertNull(entry(definition, "selector:P"));
        invoke(definition, "up");
        assertEquals(List.of(new VehicleControls.StepGear(1)), commands);
    }

    private MenuScene menu(VehicleBody body, VehicleDashboardMenu.Page choice) {
        var view = new VehicleDashboardMenu.View(UUID.randomUUID(), body.definition.id(), VehicleHud.Frame.of(body), 1, body.definition.seats().size());
        return VehicleDashboardMenu.create(view, choice, (message, values) -> Component.text(message.name() + " " + Arrays.toString(values)),
                (_, command) -> commands.add(command), page::set).build();
    }
    private void invoke(MenuScene definition, String id) {
        entry(definition, id).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
    }
    private VehicleBody car() { return VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO); }
}
