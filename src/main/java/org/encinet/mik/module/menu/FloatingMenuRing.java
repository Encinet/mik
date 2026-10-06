package org.encinet.mik.module.menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared ordering and geometry for player-centered circular menus. */
public final class FloatingMenuRing {
    private FloatingMenuRing() { }

    /** Returns the selected item, then alternating clockwise and counterclockwise neighbors. */
    public static List<Slot> around(int count, int selected, int maximumVisible) {
        if (count < 0 || maximumVisible < 1
                || (count > 0 && (selected < 0 || selected >= count))) {
            throw new IllegalArgumentException("Invalid ring selection");
        }
        if (count == 0) return List.of();
        int visible = Math.min(count, maximumVisible);
        List<Slot> slots = new ArrayList<>(visible);
        Set<Integer> used = new HashSet<>();
        for (int distance = 0; slots.size() < visible; distance++) {
            for (int offset : distance == 0
                    ? new int[]{0} : new int[]{distance, -distance}) {
                int index = Math.floorMod(selected + offset, count);
                if (used.add(index)) slots.add(new Slot(index, offset));
                if (slots.size() == visible) break;
            }
        }
        return List.copyOf(slots);
    }

    /** Local forward is negative in front of a player-centered anchor. */
    public static FloatingMenuPose pose(int offset, int visibleCards,
                                        double radius, double up) {
        return pose((double) offset, visibleCards, radius, up);
    }

    public static FloatingMenuPose pose(double offset, int visibleCards,
                                        double radius, double up) {
        if (!Double.isFinite(offset) || visibleCards < 1 || !Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(up)) {
            throw new IllegalArgumentException("Invalid ring geometry");
        }
        double angleDegrees = offset * 360.0 / visibleCards;
        double angle = Math.toRadians(angleDegrees);
        return FloatingMenuPose.oriented(new FloatingMenuPoint(
                radius * Math.sin(angle), up, -radius * Math.cos(angle)),
                angleDegrees, 0.0);
    }

    /** Places a short row on the player's front arc at a constant reachable radius. */
    public static FloatingMenuPose frontArc(int index, int count,
                                             double angleStepDegrees,
                                             double radius, double up) {
        if (count < 1 || index < 0 || index >= count
                || !Double.isFinite(angleStepDegrees) || angleStepDegrees <= 0.0
                || !Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(up)) {
            throw new IllegalArgumentException("Invalid front arc geometry");
        }
        double angleDegrees = (index - (count - 1) * 0.5) * angleStepDegrees;
        double angle = Math.toRadians(angleDegrees);
        return FloatingMenuPose.oriented(new FloatingMenuPoint(
                radius * Math.sin(angle), up, -radius * Math.cos(angle)),
                angleDegrees, 0.0);
    }

    /** Moves a card along the shorter arc instead of cutting through the viewer. */
    public static FloatingMenuPoint approach(FloatingMenuPoint current,
                                             FloatingMenuPoint target,
                                             double fraction) {
        if (current == null || target == null || !Double.isFinite(fraction)
                || fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("Invalid ring transition");
        }
        if (Math.abs(current.right() - target.right()) < 0.001
                && Math.abs(current.up() - target.up()) < 0.001
                && Math.abs(current.forward() - target.forward()) < 0.001) {
            return target;
        }
        double currentAngle = Math.toDegrees(Math.atan2(
                current.right(), -current.forward()));
        double targetAngle = Math.toDegrees(Math.atan2(
                target.right(), -target.forward()));
        double delta = (targetAngle - currentAngle + 180.0) % 360.0;
        if (delta < 0.0) delta += 360.0;
        delta -= 180.0;
        double angle = Math.toRadians(currentAngle + delta * fraction);
        double radius = Math.hypot(current.right(), current.forward())
                + (Math.hypot(target.right(), target.forward())
                - Math.hypot(current.right(), current.forward())) * fraction;
        double up = current.up() + (target.up() - current.up()) * fraction;
        return new FloatingMenuPoint(radius * Math.sin(angle), up,
                -radius * Math.cos(angle));
    }

    public record Slot(int index, int offset) { }
}
