package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAnchorMode;
import org.encinet.mik.module.menu.FloatingMenuAnimation;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuEasing;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuMovementPolicy;
import org.encinet.mik.module.menu.FloatingMenuPresentation;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class VehicleDashboardMenu {
    enum Page { DRIVE, GEARBOX, DETAILS }
    record View(UUID vehicle, String model, VehicleHud.Frame frame, int occupants, int seats) { }
    @FunctionalInterface interface Text { Component get(Message message, Object... arguments); }

    static FloatingMenuDefinition.Builder create(View view, Page page, Text text,
                                                 BiConsumer<Player, String> control, Consumer<Page> navigate) {
        Text supplied = text;
        text = (message, arguments) -> supplied.get(message, plainArguments(arguments));
        VehicleHud.Frame frame = view.frame();
        boolean car = frame.kind() == VehicleDefinition.Kind.CAR;
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("vehicle-dashboard")
                .anchorMode(FloatingMenuAnchorMode.FOLLOW_PLAYER_POSITION)
                .movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT)
                .presentation(FloatingMenuPresentation.SPATIAL_REQUIRED)
                .frontArc()
                .framing(FloatingMenuFraming.PANORAMIC)
                .animation(new FloatingMenuAnimation(2, 2, 2, 0.025, 0.025, 0, 0,
                        FloatingMenuEasing.CUBIC_OUT, FloatingMenuEasing.CUBIC_OUT))
                .layout(layout());
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
                    status);
        } else if (page == Page.GEARBOX && car) {
            readings = lines(frame.car().shiftRemaining() > 0 ? text.get(Message.VEHICLE_DASH_SHIFT, gear(frame.car().actualGear()), gear(frame.car().targetGear()),
                            whole(frame.car().shiftProgress() * 100), number(frame.car().shiftRemaining()))
                            : text.get(Message.VEHICLE_DASH_GEAR, frame.gear()),
                    text.get(Message.VEHICLE_DASH_RPM, whole(frame.rpm()), whole(frame.energy().redline())),
                    status);
        } else {
            readings = lines(text.get(Message.VEHICLE_DASH_SPEED, number(frame.speed() * 3.6), number(frame.maximumSpeed() * 3.6)),
                    text.get(Message.VEHICLE_DASH_RPM, whole(frame.rpm()), whole(frame.energy().redline())),
                    car ? text.get(Message.VEHICLE_DASH_GEAR, frame.gear()) : text.get(mode),
                    text.get(Message.VEHICLE_DASH_INPUT, whole(frame.throttle() * 100), whole(frame.car().brake() * 100), whole(frame.car().steering() * 100)),
                    car ? text.get(Message.VEHICLE_DASH_HANDBRAKE, text.get(frame.car().handbrake() ? Message.VEHICLE_VALUE_ON : Message.VEHICLE_VALUE_OFF))
                            : text.get(Message.VEHICLE_DASH_VERTICAL, number(frame.altitude()), number(frame.climb())),
                    text.get(Message.VEHICLE_DASH_FUEL, number(frame.energy().amount()), number(frame.energy().capacity()), whole(frame.fuel() * 100)),
                    text.get(Message.VEHICLE_DASH_HEALTH, whole(frame.health()), view.occupants(), view.seats()),
                    status);
        }
        menu.information("telemetry", readings).region("readings");
        if (car) {
            for (VehicleTransmission.Selector selector : VehicleTransmission.Selector.values()) {
                boolean enabled = selectorEnabled(frame, selector);
                button(menu, "selector:" + selector, "selectors", Component.text(selector.name()),
                        frame.car().selector() == selector, enabled, frame.car().shiftRemaining() > 0 ? Message.VEHICLE_STATUS_SHIFTING : Message.VEHICLE_STATUS_STOP_FIRST,
                        text, control, "gear " + selector.name());
            }
        }
        button(menu, "engine", "controls", text.get(frame.energy().engineRunning() ? Message.VEHICLE_DASH_ENGINE_STOP : Message.VEHICLE_DASH_ENGINE_START),
                frame.energy().engineRunning(), frame.energy().engineRunning() || frame.energy().amount() > 0 && frame.health() > 0,
                frame.health() <= 0 ? Message.VEHICLE_STATUS_DISABLED : Message.VEHICLE_STATUS_NO_FUEL, text, control, "engine");
        if (car && page == Page.GEARBOX) {
            button(menu, "automatic", "controls", text.get(Message.VEHICLE_DASH_AUTOMATIC), frame.car().mode() == VehicleTransmission.Mode.AUTOMATIC,
                    true, Message.VEHICLE_STATUS_MANUAL_ONLY, text, control, "mode automatic");
            button(menu, "manual", "controls", text.get(Message.VEHICLE_DASH_MANUAL), frame.car().mode() == VehicleTransmission.Mode.MANUAL,
                    true, Message.VEHICLE_STATUS_MANUAL_ONLY, text, control, "mode manual");
            for (int gear = 1; gear <= frame.car().gearCount(); gear++) button(menu, "gear:" + gear, "gears", Component.text("M" + gear), frame.car().actualGear() == gear,
                    frame.car().mode() == VehicleTransmission.Mode.MANUAL && frame.car().shiftRemaining() == 0,
                    frame.car().mode() != VehicleTransmission.Mode.MANUAL ? Message.VEHICLE_STATUS_MANUAL_ONLY : Message.VEHICLE_STATUS_SHIFTING,
                    text, control, "gear " + gear);
        } else {
            button(menu, "mode", "controls", text.get(Message.VEHICLE_DASH_MODE_SWITCH, text.get(nextMode)), false,
                    true, Message.VEHICLE_STATUS_MANUAL_ONLY, text, control, "mode");
        }
        if (car) {
            int base = frame.car().shiftRemaining() > 0 ? frame.car().targetGear() : frame.car().actualGear();
            boolean manual = frame.car().mode() == VehicleTransmission.Mode.MANUAL;
            Message reason = !manual ? Message.VEHICLE_STATUS_MANUAL_ONLY : frame.car().shiftRemaining() > 0 ? Message.VEHICLE_STATUS_SHIFTING : Message.VEHICLE_STATUS_GEAR_LIMIT;
            button(menu, "down", "controls", text.get(Message.VEHICLE_DASH_DOWN), false,
                    manual && frame.car().shiftRemaining() == 0 && base > -1, reason, text, control, "gear down");
            button(menu, "up", "controls", text.get(Message.VEHICLE_DASH_UP), false,
                    manual && frame.car().shiftRemaining() == 0 && base < frame.car().gearCount(), reason, text, control, "gear up");
        } else {
            button(menu, "down", "controls", text.get(Message.VEHICLE_DASH_THROTTLE_DOWN), false,
                    frame.throttle() > (frame.kind() == VehicleDefinition.Kind.BOAT ? -0.5 : 0), Message.VEHICLE_STATUS_GEAR_LIMIT, text, control, "throttle down");
            button(menu, "up", "controls", text.get(Message.VEHICLE_DASH_THROTTLE_UP), false,
                    frame.throttle() < 1, Message.VEHICLE_STATUS_GEAR_LIMIT, text, control, "throttle up");
        }
        for (Page choice : Page.values()) {
            if (choice == Page.GEARBOX && !car) continue;
            Message label = switch (choice) { case DRIVE -> Message.VEHICLE_DASH_DRIVE; case GEARBOX -> Message.VEHICLE_DASH_GEARBOX; case DETAILS -> Message.VEHICLE_DASH_DETAILS; };
            menu.navigation("page:" + choice, text.get(label)).region("navigation").selected(page == choice)
                    .primary((player, handle) -> navigate.accept(choice));
        }
        menu.navigation("recenter", text.get(Message.VEHICLE_DASH_RECENTER)).region("navigation").primary((player, handle) -> handle.reanchor());
        menu.navigation("close", text.get(Message.CLOSE)).region("navigation").primary((player, handle) -> handle.close());
        return menu;
    }

    static boolean selectorEnabled(VehicleHud.Frame frame, VehicleTransmission.Selector selector) {
        if (frame.car().shiftRemaining() > 0) return false;
        if (selector == VehicleTransmission.Selector.P || selector == VehicleTransmission.Selector.R
                || frame.car().selector() == VehicleTransmission.Selector.R && selector == VehicleTransmission.Selector.D)
            return frame.speed() <= 0.5;
        return selector != VehicleTransmission.Selector.D || frame.car().signedSpeed() >= -0.5;
    }

    private static FloatingMenuLayout layout() {
        Set<String> readingRegions = Set.of("model", "readings");
        Set<String> controlRegions = Set.of("selectors", "controls", "gears", "navigation");
        FloatingMenuLayout readingLayout = FloatingMenuLayouts.verticalRegions(0.14,
                FloatingMenuLayouts.information("model"), FloatingMenuLayouts.information("readings"));
        FloatingMenuLayout controlLayout = FloatingMenuLayouts.verticalRegions(0.16,
                FloatingMenuLayouts.actions("selectors", 4), FloatingMenuLayouts.actions("controls", 2),
                FloatingMenuLayouts.actions("gears", 4), FloatingMenuLayouts.navigation("navigation"));
        return context -> {
            boolean reading = readingRegions.contains(context.region());
            FloatingMenuLayout localLayout = reading ? readingLayout : controlLayout;
            FloatingMenuLayout.Context local = context.inRegions(reading ? readingRegions : controlRegions);
            double edge = reading ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            for (int index = 0; index < local.count(); index++) {
                var sibling = local.at(index);
                double right = localLayout.pose(sibling).right();
                edge = reading ? Math.max(edge, right + sibling.size().width() / 2)
                        : Math.min(edge, right - sibling.size().width() / 2);
            }
            return localLayout.pose(local).offset(reading ? -0.55 - edge : 0.55 - edge, -0.35, 0);
        };
    }

    private static void button(FloatingMenuDefinition.Builder menu, String id, String region, Component label,
                               boolean selected, boolean enabled, Message reason, Text text, BiConsumer<Player, String> control, String command) {
        var node = menu.navigation(id, label).region(region).selected(selected).primary((player, handle) -> control.accept(player, command));
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
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "—"; }
    private static String whole(double value) { return String.format(Locale.ROOT, "%.0f", value); }
    static Object[] plainArguments(Object... values) {
        return java.util.Arrays.stream(values).map(value -> value instanceof Component component
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component) : value).toArray();
    }
}
