package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.function.Predicate;

final class VehicleDisplayPicker {
    record Hit(Display display, VehicleVector position, double distance) { }
    static Display pick(Location eye, Vector direction, Collection<? extends Entity> candidates, Predicate<Entity> excluded) {
        return pick(eye, direction, candidates, excluded, 32);
    }

    static Display pick(Location eye, Vector direction, Collection<? extends Entity> candidates, Predicate<Entity> excluded, double maximum) {
        Hit hit = hit(eye, direction, candidates, excluded, maximum);
        return hit == null ? null : hit.display();
    }

    static Hit hit(Location eye, Vector direction, Collection<? extends Entity> candidates, Predicate<Entity> excluded, double maximum) {
        if (!Double.isFinite(maximum) || maximum < 0 || !Double.isFinite(direction.lengthSquared()) || direction.lengthSquared() < 1.0E-12)
            return null;
        Location reference = eye.clone();
        reference.setYaw(0);
        reference.setPitch(0);
        Vector normalized = direction.clone().normalize();
        Display selected = null;
        double nearest = Math.min(32, maximum);
        for (Entity candidate : candidates) {
            if (!(candidate instanceof Display display) || !(display instanceof BlockDisplay || display instanceof ItemDisplay)
                    || !display.isValid() || !display.getWorld().equals(eye.getWorld()) || excluded.test(display)) continue;
            Matrix4f transform = VehicleModel.relativeTransform(reference, display);
            if (!transform.isFinite() || Math.abs(transform.determinant()) < 1.0E-12) continue;
            Matrix4f inverse = transform.invert();
            Vector3f start = inverse.transformPosition(new Vector3f());
            Vector3f ray = inverse.transformDirection(new Vector3f((float) normalized.getX(), (float) normalized.getY(), (float) normalized.getZ()));
            double hit = intersection(start, ray, display instanceof BlockDisplay ? 0 : -0.5, display instanceof BlockDisplay ? 1 : 0.5);
            if (hit >= 0 && hit <= nearest && (selected == null || hit < nearest
                    || hit == nearest && display.getUniqueId().compareTo(selected.getUniqueId()) < 0)) {
                selected = display;
                nearest = hit;
            }
        }
        if (selected == null) return null;
        return new Hit(selected, new VehicleVector(eye.getX() + normalized.getX() * nearest,
                eye.getY() + normalized.getY() * nearest, eye.getZ() + normalized.getZ() * nearest), nearest);
    }

    static double intersection(Vector3f start, Vector3f direction, double low, double high) {
        double minimum = 0;
        double maximum = 32;
        for (int axis = 0; axis < 3; axis++) {
            double origin = start.get(axis);
            double ray = direction.get(axis);
            if (!Double.isFinite(origin) || !Double.isFinite(ray)) return -1;
            if (Math.abs(ray) < 1.0E-9) {
                if (origin < low || origin > high) return -1;
                continue;
            }
            double first = (low - origin) / ray;
            double second = (high - origin) / ray;
            minimum = Math.max(minimum, Math.min(first, second));
            maximum = Math.min(maximum, Math.max(first, second));
            if (minimum > maximum) return -1;
        }
        return minimum;
    }
}
