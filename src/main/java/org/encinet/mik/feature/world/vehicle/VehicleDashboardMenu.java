package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAnchorMode;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuEasing;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.menu.FloatingMenuMovementPolicy;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class VehicleDashboardMenu {
    enum Page { DRIVE, GEARBOX, DETAILS }
    record View(UUID vehicle, String model, VehicleHud.Frame frame, int occupants, int seats) { }
    @FunctionalInterface interface Text { Component get(Message message, Object... arguments); }

    static MenuScene.Builder create(View view, Page page, Text text,
                                                 BiConsumer<Player, VehicleControls.Action> control, Consumer<Page> navigate) {
        Text supplied = text;
        text = (message, arguments) -> supplied.get(message, plainArguments(arguments));
        VehicleHud.Frame frame = view.frame();
        boolean car = frame.kind() == VehicleDefinition.Kind.CAR;
        MenuScene.Builder menu = MenuScene.screen("vehicle-dashboard")
                .anchorMode(page == Page.DRIVE ? FloatingMenuAnchorMode.INERTIAL_TOOLBAR : FloatingMenuAnchorMode.FOLLOW_PLAYER_POSITION)
                .movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT)
                .framing(FloatingMenuFraming.COMFORTABLE)
                .animation(new FloatingMenuAnimation(2, 2, 2, 0.12, 0.025, 0.025, 0, 0,
                        FloatingMenuEasing.CUBIC_OUT, FloatingMenuEasing.CUBIC_OUT))
                .content(content());
        if (page == Page.DRIVE) menu.frontArc();
        menu.information("model", text.get(Message.VEHICLE_DASH_TITLE, view.model())).region("model");
        Message warning = switch (frame.warning()) {
            case "", "ENGINE_ON", "ENGINE_OFF" -> null;
            case "SHIFTING" -> car && page == Page.GEARBOX && frame.car().shiftRemaining() > 0
                    ? null : Message.VEHICLE_STATUS_SHIFTING;
            default -> VehicleHud.statusMessage(frame.warning(), frame.energy().engineRunning());
        };
        Component status = warning == null ? null : text.get(warning);
        Message mode = car ? frame.car().mode() == VehicleTransmission.Mode.AUTOMATIC ? Message.VEHICLE_DASH_AUTOMATIC : Message.VEHICLE_DASH_MANUAL
                : frame.assisted() ? Message.VEHICLE_DASH_ASSISTED : Message.VEHICLE_DASH_UNASSISTED;
        Message nextMode = car ? frame.car().mode() == VehicleTransmission.Mode.AUTOMATIC ? Message.VEHICLE_DASH_MANUAL : Message.VEHICLE_DASH_AUTOMATIC
                : frame.assisted() ? Message.VEHICLE_DASH_UNASSISTED : Message.VEHICLE_DASH_ASSISTED;
        Component readings;
        if (page == Page.DETAILS) {
            readings = lines(
                    text.get(Message.VEHICLE_DASH_FUEL, number(frame.energy().amount()), number(frame.energy().capacity()), whole(frame.fuel() * 100)),
                    text.get(Message.VEHICLE_DASH_ENERGY, number(frame.energy().consumption() * 60),
                            number(frame.energy().enduranceSeconds() / 60), number(frame.energy().rangeKilometers(frame.speed()))),
                    text.get(Message.VEHICLE_DASH_HEALTH, whole(frame.health()), view.occupants(), view.seats()),
                    text.get(Message.VEHICLE_DASH_POSITION, number(frame.origin().coordinateX()), number(frame.origin().coordinateY()), number(frame.origin().coordinateZ())),
                    text.get(Message.VEHICLE_DASH_HEADING, whole(frame.heading())),
                    text.get(Message.VEHICLE_DASH_CONTACT, text.get(frame.grounded() || frame.floating() ? Message.VEHICLE_VALUE_ON : Message.VEHICLE_VALUE_OFF)),
                    text.get(Message.VEHICLE_DASH_RPM, whole(frame.rpm()), whole(frame.energy().redline())),
                    text.get(Message.VEHICLE_DASH_INPUT, whole(frame.throttle() * 100), whole(frame.car().brake() * 100), whole(frame.car().steering() * 100)),
                    car ? text.get(Message.VEHICLE_DASH_HANDBRAKE, text.get(frame.car().handbrake() ? Message.VEHICLE_VALUE_ON : Message.VEHICLE_VALUE_OFF))
                            : text.get(Message.VEHICLE_DASH_VERTICAL, number(frame.altitude()), number(frame.climb())),
                    status);
        } else if (page == Page.GEARBOX && car) {
            readings = lines(frame.car().shiftRemaining() > 0 ? text.get(Message.VEHICLE_DASH_SHIFT, gear(frame.car().actualGear()), gear(frame.car().targetGear()),
                            whole(frame.car().shiftProgress() * 100), number(frame.car().shiftRemaining()))
                            : text.get(Message.VEHICLE_DASH_GEAR, frame.gear()),
                    text.get(Message.VEHICLE_DASH_RPM, whole(frame.rpm()), whole(frame.energy().redline())),
                    status);
        } else {
            readings = lines(text.get(Message.VEHICLE_DASH_SPEED, number(frame.speed() * 3.6), number(frame.maximumSpeed() * 3.6)),
                    car ? text.get(Message.VEHICLE_DASH_GEAR, frame.gear()) : text.get(mode),
                    !car ? text.get(Message.VEHICLE_DASH_VERTICAL, number(frame.altitude()), number(frame.climb())) : null,
                    text.get(Message.VEHICLE_DASH_FUEL, number(frame.energy().amount()), number(frame.energy().capacity()), whole(frame.fuel() * 100)),
                    status);
        }
        menu.information("telemetry", readings).region("readings");
        if (page != Page.DETAILS) addControls(menu, frame, page, text, control, nextMode);
        for (Page choice : Page.values()) {
            if (choice == Page.GEARBOX && !car) continue;
            Message label = switch (choice) { case DRIVE -> Message.VEHICLE_DASH_DRIVE; case GEARBOX -> Message.VEHICLE_DASH_GEARBOX; case DETAILS -> Message.VEHICLE_DASH_DETAILS; };
            menu.navigation("page:" + choice, text.get(label)).region("navigation").selected(page == choice)
                    .primary((_, _) -> navigate.accept(choice));
        }
        menu.navigation("recenter", text.get(Message.VEHICLE_DASH_RECENTER)).region("navigation").primary((_, handle) -> handle.reanchor());
        menu.navigation("close", text.get(Message.CLOSE)).region("navigation").primary((_, handle) -> handle.close());
        return menu;
    }

    private static void addControls(MenuScene.Builder menu, VehicleHud.Frame frame, Page page, Text text,
                                    BiConsumer<Player, VehicleControls.Action> control, Message nextMode) {
        boolean car = frame.kind() == VehicleDefinition.Kind.CAR;
        if (car && page == Page.GEARBOX) {
            for (VehicleTransmission.Selector selector : VehicleTransmission.Selector.values()) {
                boolean enabled = selectorEnabled(frame, selector);
                button(menu, "selector:" + selector, "selectors", Component.text(selector.name()),
                        frame.car().selector() == selector, enabled, frame.car().shiftRemaining() > 0 ? Message.VEHICLE_STATUS_SHIFTING : Message.VEHICLE_STATUS_STOP_FIRST,
                        text, control, new VehicleControls.Select(selector));
            }
        }
        button(menu, "engine", "controls", text.get(frame.energy().engineRunning() ? Message.VEHICLE_DASH_ENGINE_STOP : Message.VEHICLE_DASH_ENGINE_START),
                frame.energy().engineRunning(), frame.energy().engineRunning() || frame.energy().amount() > 0 && frame.health() > 0,
                frame.health() <= 0 ? Message.VEHICLE_STATUS_DISABLED : Message.VEHICLE_STATUS_NO_FUEL, text, control, VehicleControls.Toggle.ENGINE);
        if (car && page == Page.GEARBOX) {
            button(menu, "automatic", "controls", text.get(Message.VEHICLE_DASH_AUTOMATIC), frame.car().mode() == VehicleTransmission.Mode.AUTOMATIC,
                    true, Message.VEHICLE_STATUS_MANUAL_ONLY, text, control, new VehicleControls.Mode(true));
            button(menu, "manual", "controls", text.get(Message.VEHICLE_DASH_MANUAL), frame.car().mode() == VehicleTransmission.Mode.MANUAL,
                    true, Message.VEHICLE_STATUS_MANUAL_ONLY, text, control, new VehicleControls.Mode(false));
            for (int gear = 1; gear <= frame.car().gearCount(); gear++) button(menu, "gear:" + gear, "gears", Component.text("M" + gear), frame.car().actualGear() == gear,
                    frame.car().mode() == VehicleTransmission.Mode.MANUAL && frame.car().shiftRemaining() == 0,
                    frame.car().mode() != VehicleTransmission.Mode.MANUAL ? Message.VEHICLE_STATUS_MANUAL_ONLY : Message.VEHICLE_STATUS_SHIFTING,
                    text, control, new VehicleControls.Gear(gear));
        } else if (!car) {
            button(menu, "mode", "controls", text.get(Message.VEHICLE_DASH_MODE_SWITCH, text.get(nextMode)), null,
                    true, Message.VEHICLE_STATUS_MANUAL_ONLY, text, control, VehicleControls.Toggle.MODE);
        }
        if (car && (page == Page.GEARBOX || frame.car().mode() == VehicleTransmission.Mode.MANUAL)) {
            int base = frame.car().shiftRemaining() > 0 ? frame.car().targetGear() : frame.car().actualGear();
            boolean manual = frame.car().mode() == VehicleTransmission.Mode.MANUAL;
            Message reason = !manual ? Message.VEHICLE_STATUS_MANUAL_ONLY : frame.car().shiftRemaining() > 0 ? Message.VEHICLE_STATUS_SHIFTING : Message.VEHICLE_STATUS_GEAR_LIMIT;
            button(menu, "down", "controls", text.get(Message.VEHICLE_DASH_DOWN), null,
                    manual && frame.car().shiftRemaining() == 0 && base > -1, reason, text, control, new VehicleControls.StepGear(-1));
            button(menu, "up", "controls", text.get(Message.VEHICLE_DASH_UP), null,
                    manual && frame.car().shiftRemaining() == 0 && base < frame.car().gearCount(), reason, text, control, new VehicleControls.StepGear(1));
        } else if (!car) {
            button(menu, "down", "controls", text.get(Message.VEHICLE_DASH_THROTTLE_DOWN), null,
                    frame.throttle() > (frame.kind() == VehicleDefinition.Kind.BOAT ? -0.5 : 0), Message.VEHICLE_STATUS_GEAR_LIMIT, text, control, new VehicleControls.Throttle(-1));
            button(menu, "up", "controls", text.get(Message.VEHICLE_DASH_THROTTLE_UP), null,
                    frame.throttle() < 1, Message.VEHICLE_STATUS_GEAR_LIMIT, text, control, new VehicleControls.Throttle(1));
        }
    }

    static boolean selectorEnabled(VehicleHud.Frame frame, VehicleTransmission.Selector selector) {
        if (frame.car().shiftRemaining() > 0) return false;
        if (selector == VehicleTransmission.Selector.P || selector == VehicleTransmission.Selector.R
                || frame.car().selector() == VehicleTransmission.Selector.R && selector == VehicleTransmission.Selector.D)
            return frame.speed() <= 0.5;
        return selector != VehicleTransmission.Selector.D || frame.car().signedSpeed() >= -0.5;
    }

    private static UiNode content() {
        return UiTemplates.sheet(UiTemplates.window("telemetry", UiTemplates.menu(
                        UiTemplates.title("model"), UiTemplates.information("readings"))),
                UiTemplates.toolbar("tools", UiTemplates.menu(UiTemplates.actions("selectors", 4),
                        UiTemplates.actions("controls", 3), UiTemplates.actions("gears", 4),
                        UiTemplates.navigation("navigation"))));
    }

    private static void button(MenuScene.Builder menu, String id, String region, Component label,
                               Boolean selected, boolean enabled, Message reason, Text text, BiConsumer<Player, VehicleControls.Action> control, VehicleControls.Action action) {
        var node = menu.control(id, label).region(region).primary((player, _) -> control.accept(player, action));
        if (selected != null) node.selected(selected);
        if (!enabled) node.disabled(text.get(reason));
    }
    private static Component lines(Component... values) {
        var builder = Component.text();
        boolean first = true;
        for (Component value : values) {
            if (value == null) continue;
            if (!first) builder.append(Component.newline());
            builder.append(value);
            first = false;
        }
        return builder.build().colorIfAbsent(NamedTextColor.AQUA);
    }
    private static String gear(int gear) { return gear < 0 ? "R" : gear == 0 ? "N" : String.valueOf(gear); }
    private static Object number(double value) { return Double.isFinite(value) ? Math.round(value * 10) / 10.0 : "—"; }
    private static Object whole(double value) { return Double.isFinite(value) ? Math.round(value) : "—"; }
    static Object[] plainArguments(Object... values) {
        return java.util.Arrays.stream(values).map(value -> value instanceof Component component
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component) : value).toArray();
    }
}
