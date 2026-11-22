package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAnchorMode;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class VehicleDashboardMenuTest {
    private final List<VehicleControls.Action> commands = new ArrayList<>();
    private final AtomicReference<VehicleDashboardMenu.Page> page = new AtomicReference<>();
    private final AtomicReference<Boolean> hud = new AtomicReference<>();

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
        assertEquals(4, text(entry(definition, "telemetry")).lines().count());
    }

    @Test void gearboxProvidesAllSelectorsModesAndConfiguredForwardGears() {
        var body = car();
        var definition = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        for (var selector : VehicleTransmission.Selector.values()) assertTrue((entry(definition, "selector:" + selector) != null));
        assertTrue(entry(definition, "automatic").selected());
        assertFalse(entry(definition, "manual").selected());
        assertTrue(definition.entries().keySet().stream().noneMatch(id -> id.contains("/gear:")));
        assertNull(entry(definition, "engine"));
        invoke(definition, "manual");
        assertEquals(List.of(new VehicleControls.Mode(false)), commands);
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        var manual = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        for (int gear = 1; gear <= body.definition.engine().ratios().size(); gear++) assertNotNull(entry(manual, "gear:" + gear));
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
        invoke(definition, "selector:N");
        invoke(definition, "automatic");
        var drive = menu(body, VehicleDashboardMenu.Page.DRIVE);
        invoke(drive, "up");
        invoke(drive, "down");
        invoke(drive, "engine");
        assertEquals(List.of(new VehicleControls.Gear(3), new VehicleControls.Select(VehicleTransmission.Selector.N), new VehicleControls.Mode(true),
                new VehicleControls.StepGear(1), new VehicleControls.StepGear(-1), VehicleControls.Toggle.ENGINE), commands);
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
        assertFalse(entry(menu(body, VehicleDashboardMenu.Page.DRIVE), "up").enabled());
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
        assertTrue(readings.contains(Message.VEHICLE_DASH_AUTOMATIC.name()));
        var gearbox = menu(body, VehicleDashboardMenu.Page.GEARBOX);
        String modeLabel = PlainTextComponentSerializer.plainText().serialize(entry(gearbox, "manual").label());
        assertTrue(modeLabel.contains(Message.VEHICLE_DASH_MANUAL.name()));
        assertFalse(modeLabel.contains("TextComponentImpl"));
        assertFalse(readings.contains("TextComponentImpl"));
        assertArrayEquals(new Object[]{"Automatic", 2}, VehicleDashboardMenu.plainArguments(Component.text("Automatic"), 2));
    }

    @Test void detailPageIncludesFuelConditionOccupancyPositionAndEstimates() {
        var definition = menu(car(), VehicleDashboardMenu.Page.DETAILS);
        String readings = readings(definition);
        for (Message key : List.of(Message.VEHICLE_DASH_FUEL, Message.VEHICLE_DASH_ENERGY, Message.VEHICLE_DASH_HEALTH,
                Message.VEHICLE_DASH_POSITION, Message.VEHICLE_DASH_HEADING)) assertTrue(readings.contains(key.name()));
        assertNull(entry(definition, "engine"));
        assertNull(entry(definition, "selector:P"));
        assertTrue(readings.contains("—"));
        assertFalse(readings.contains("NaN"));
        assertFalse(readings.contains("Infinity"));
        invoke(definition, "page:GEARBOX");
        assertEquals(VehicleDashboardMenu.Page.GEARBOX, page.get());
    }

    @Test void detailedReadingsPageWithoutLosingFieldsAtNarrowFovAndLargeText() {
        var view = view(car(), true);
        var index = new AtomicInteger();
        var seen = new HashSet<String>();
        var allReadings = new HashSet<String>();
        int pageCount = 0;
        do {
            var definition = VehicleDashboardMenu.create(view, VehicleDashboardMenu.Page.DETAILS, index.get(),
                    (message, values) -> Component.text(message.name() + " " + Arrays.toString(values)),
                    (_, _) -> { }, _ -> { }, index::set, (_, _) -> { }).build();
            allReadings.addAll(elements(definition).values().stream().map(node -> node.key().value())
                    .filter(key -> key.startsWith("reading:")).toList());
            var measured = SceneGeometryTestSupport.readableFrame(definition, 1.8, new FloatingMenuFieldOfView(30), 1.3);
            assertDisjoint(measured);
            assertNotNull(element(measured, "page:DRIVE"));
            for (var element : measured.elements()) if (element.node().key().value().startsWith("reading:"))
                assertTrue(seen.add(element.node().key().value()));
            pageCount++;
            var next = measured.pagination().stream().filter(value -> value.page().hasNext()).findFirst();
            if (next.isEmpty()) break;
            next.get().action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        } while (pageCount <= allReadings.size());
        assertTrue(pageCount > 1);
        assertEquals(allReadings, seen);
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

    @Test void engineStateAndActionAppearOnlyOnDrivingPage() {
        var body = car();
        for (boolean running : List.of(false, true)) {
            body.engineRunning = running;
            for (var choice : VehicleDashboardMenu.Page.values()) {
                var definition = menu(body, choice);
                String readings = readings(definition);
                assertEquals(choice == VehicleDashboardMenu.Page.DRIVE,
                        readings.contains((running ? Message.VEHICLE_STATUS_ENGINE_ON : Message.VEHICLE_STATUS_ENGINE_OFF).name()));
                assertFalse(readings.startsWith("\n"));
                assertFalse(readings.endsWith("\n"));
                assertFalse(readings.contains("\n\n"));
                if (choice == VehicleDashboardMenu.Page.DRIVE) {
                    String label = PlainTextComponentSerializer.plainText().serialize(entry(definition, "engine").label());
                    assertTrue(label.contains((running ? Message.VEHICLE_DASH_ENGINE_STOP : Message.VEHICLE_DASH_ENGINE_START).name()));
                } else assertNull(entry(definition, "engine"));
            }
        }
    }

    @Test void damageAndFuelWarningsRemainAvailableWithoutRepeatingInTheControlsGuide() {
        var body = car();
        for (Message warning : List.of(Message.VEHICLE_STATUS_DISABLED, Message.VEHICLE_STATUS_NO_FUEL, Message.VEHICLE_STATUS_LOW_FUEL)) {
            body.health = warning == Message.VEHICLE_STATUS_DISABLED ? 0 : 100;
            body.fuel = warning == Message.VEHICLE_STATUS_NO_FUEL ? 0 : body.definition.fuelCapacity() * 0.05;
            for (var choice : VehicleDashboardMenu.Page.values()) {
                var definition = menu(body, choice);
                assertEquals(choice == VehicleDashboardMenu.Page.HELP ? 0 : 1L,
                        readings(definition).lines().filter(line -> line.contains(warning.name())).count());
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
            var reading = element(measured, switch (choice) {
                case DRIVE, GEARBOX -> "telemetry";
                case DETAILS -> "reading:0";
                case HELP -> "help";
            });
            var control = element(measured, choice == VehicleDashboardMenu.Page.GEARBOX ? "selector:P" : "page:DRIVE");
            assertTrue(reading.y() + reading.size().height() < control.y());
            assertEquals(choice == VehicleDashboardMenu.Page.DRIVE ? FloatingMenuSpatialFrame.FRONT_ARC : FloatingMenuSpatialFrame.IN_FRONT,
                    definition.spatialFrame());
        }
    }

    @Test void telemetryPassesNumbersToLocalizationAndManualDrivingOnlyShowsRelevantControls() {
        var body = car();
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        var view = view(body, true);
        var arguments = new java.util.EnumMap<Message, Object[]>(Message.class);
        var definition = VehicleDashboardMenu.create(view, VehicleDashboardMenu.Page.DRIVE, 0, (message, values) -> {
            arguments.put(message, values);
            return Component.text(message.name());
        }, (_, command) -> commands.add(command), page::set, _ -> { }, (_, visible) -> hud.set(visible)).build();
        assertTrue(Arrays.stream(arguments.get(Message.VEHICLE_DASH_SPEED)).allMatch(Number.class::isInstance));
        assertTrue(Arrays.stream(arguments.get(Message.VEHICLE_DASH_FUEL)).allMatch(Number.class::isInstance));
        assertEquals(org.encinet.mik.shared.menu.ui.UiRole.ACTION, entry(definition, "up").role());
        assertNull(entry(definition, "selector:P"));
        invoke(definition, "up");
        assertEquals(List.of(new VehicleControls.StepGear(1)), commands);
    }

    @Test void hudActionNamesTheDestinationAndNeverDispatchesADrivingCommandOrNavigation() {
        var body = car();
        var shown = menu(body, VehicleDashboardMenu.Page.DRIVE, true);
        assertTrue(text(entry(shown, "hud")).contains("hide-hud"));
        invoke(shown, "hud");
        assertEquals(Boolean.FALSE, hud.get());
        var hidden = menu(body, VehicleDashboardMenu.Page.DRIVE, false);
        assertTrue(text(entry(hidden, "hud")).contains("show-hud"));
        invoke(hidden, "hud");
        assertEquals(Boolean.TRUE, hud.get());
        assertTrue(commands.isEmpty());
        assertNull(page.get());
    }

    @Test void localizedPanelsStayReadableForEveryVehicleAndExtremeFov() throws Exception {
        for (var locale : Language.values()) try (var h = new VehicleEditorHarness(locale)) {
            for (var kind : VehicleDefinition.Kind.values()) {
                var body = VehicleFixtures.body(kind, VehicleVector.ZERO);
                body.transmission.mode = VehicleTransmission.Mode.MANUAL;
                for (var choice : VehicleDashboardMenu.Page.values()) {
                    if (kind != VehicleDefinition.Kind.CAR && choice == VehicleDashboardMenu.Page.GEARBOX) continue;
                    var scene = VehicleDashboardMenu.create(view(body, true), choice, 0,
                            (message, values) -> h.language.text(h.player, message, net.kyori.adventure.text.format.NamedTextColor.AQUA, values),
                            (_, _) -> { }, _ -> { }, _ -> { }, (_, _) -> { }).build();
                    for (int fov : new int[]{30, 110}) {
                        var measured = SceneGeometryTestSupport.readableFrame(scene, 1.8, new FloatingMenuFieldOfView(fov), 1);
                        assertDisjoint(measured);
                        assertNotNull(entry(scene, measured, "page:DRIVE"));
                        assertNotNull(entry(scene, measured, "close"));
                        if (choice == VehicleDashboardMenu.Page.DRIVE) assertNotNull(entry(scene, measured, "engine"));
                    }
                }
            }
        }
    }

    private MenuScene menu(VehicleBody body, VehicleDashboardMenu.Page choice) {
        return menu(body, choice, true);
    }
    private MenuScene menu(VehicleBody body, VehicleDashboardMenu.Page choice, boolean hudVisible) {
        return VehicleDashboardMenu.create(view(body, hudVisible), choice, 0, (message, values) -> Component.text(message.name() + " " + Arrays.toString(values)),
                (_, command) -> commands.add(command), page::set, _ -> { }, (_, visible) -> hud.set(visible)).build();
    }
    private static VehicleDashboardMenu.View view(VehicleBody body, boolean hudVisible) {
        return new VehicleDashboardMenu.View(UUID.randomUUID(), body.definition.id(), VehicleHud.Frame.of(body), 1, body.definition.seats().size(), hudVisible);
    }
    private static String readings(MenuScene scene) {
        return scene.entries().values().stream().filter(value -> value.id().contains("/readings/")).map(VehicleDashboardMenuTest::text)
                .collect(Collectors.joining("\n"));
    }
    private static String text(MenuScene.Entry entry) {
        return PlainTextComponentSerializer.plainText().serialize(entry.label());
    }
    private void invoke(MenuScene definition, String id) {
        entry(definition, id).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
    }
    private VehicleBody car() { return VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO); }
}
