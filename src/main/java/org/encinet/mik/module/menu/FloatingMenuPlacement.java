package org.encinet.mik.module.menu;

import org.bukkit.Location;

import java.util.Objects;

/** Selects the coordinate space in which a non-interactive scene node lives. */
public sealed interface FloatingMenuPlacement
        permits FloatingMenuPlacement.Local, FloatingMenuPlacement.World {

    static Local local(FloatingMenuPoint point) {
        return local(FloatingMenuPose.at(point));
    }

    static Local local(FloatingMenuPose pose) {
        return new Local(pose);
    }

    static World world(Location location) {
        Objects.requireNonNull(location, "location");
        return world(location, location.getYaw(), location.getPitch());
    }

    static World world(Location location, double yawDegrees, double pitchDegrees) {
        return new World(location, yawDegrees, pitchDegrees);
    }

    /** Coordinates relative to the menu frame captured when the scene opens. */
    record Local(FloatingMenuPose pose) implements FloatingMenuPlacement {
        public Local {
            pose = Objects.requireNonNull(pose, "pose");
        }
    }

    /** Absolute coordinates in the player's current Bukkit world. */
    record World(Location location, double yawDegrees,
                 double pitchDegrees) implements FloatingMenuPlacement {
        public World {
            location = Objects.requireNonNull(location, "location").clone();
            if (location.getWorld() == null) {
                throw new IllegalArgumentException("World placement requires a world");
            }
            if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY())
                    || !Double.isFinite(location.getZ()) || !Double.isFinite(yawDegrees)
                    || !Double.isFinite(pitchDegrees)) {
                throw new IllegalArgumentException("World placement must be finite");
            }
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }
}
