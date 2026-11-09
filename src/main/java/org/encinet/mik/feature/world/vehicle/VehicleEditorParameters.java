package org.encinet.mik.feature.world.vehicle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Presentation groups and unit conversion; persisted physics values stay in blocks and seconds. */
final class VehicleEditorParameters {
    enum Group { BODY, ENGINE, FUEL, BOAT, PLANE;
        String key() { return name().toLowerCase(Locale.ROOT); }
    }
    private VehicleEditorParameters() { }
    static List<String> fields(Group group) {
        return switch (group) {
            case BODY -> List.of("kind", "mass", "maximum-speed", "support-length", "grip", "center-of-mass", "inertia");
            case ENGINE -> List.of("engine.torque", "engine.idle-rpm", "engine.redline-rpm", "engine.inertia", "engine.wheel-radius",
                    "engine.final-drive", "engine.reverse-ratio", "engine.ratios", "engine.shift-seconds");
            case FUEL -> List.of("fuel.capacity", "fuel.consumption");
            case BOAT -> List.of("buoyancy");
            case PLANE -> List.of("wing-area", "lift");
        };
    }
    static List<Group> groups(String kind) {
        return switch (VehicleDefinition.parseKind(kind)) {
            case CAR -> List.of(Group.BODY, Group.ENGINE, Group.FUEL);
            case BOAT -> List.of(Group.BODY, Group.ENGINE, Group.FUEL, Group.BOAT);
            case PLANE -> List.of(Group.BODY, Group.ENGINE, Group.FUEL, Group.PLANE);
        };
    }
    static String labelKey(Group group, String kind) { return group == Group.ENGINE && !kind.equals("CAR") ? "power" : group.key(); }
    static List<String> visibleFields(Group group, String kind) {
        if (kind.equals("CAR")) return fields(group);
        if (group == Group.ENGINE) return List.of("engine.torque", "engine.idle-rpm", "engine.redline-rpm");
        return fields(group).stream().filter(field -> !field.equals("grip")).toList();
    }
    static String display(VehicleModelDraft draft, String field) {
        if (field.equals("maximum-speed")) return BigDecimal.valueOf(Double.parseDouble(draft.field(field))).multiply(BigDecimal.valueOf(3.6)).stripTrailingZeros().toPlainString();
        if (field.equals("engine.ratios")) return draft.field(field).replace("[", "").replace("]", "");
        return draft.field(field);
    }
    static String stored(String field, String value) {
        if (!field.equals("maximum-speed")) return value;
        double speed = Double.parseDouble(value) / 3.6;
        if (!Double.isFinite(speed)) throw new IllegalArgumentException("Finite speed required");
        return Double.toString(speed);
    }
}
