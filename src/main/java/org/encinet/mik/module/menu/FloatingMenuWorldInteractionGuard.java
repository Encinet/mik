package org.encinet.mik.module.menu;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Protects the world behind a visible menu from accidental clicks.
 *
 * <p>The angle is measured from the player's current view ray to the captured
 * center of the menu scene. It therefore follows the actual scene direction
 * after small player movements without turning into a permanent HUD lock.</p>
 */
final class FloatingMenuWorldInteractionGuard {
    static final double UNIFIED_HALF_ANGLE_DEGREES = 45.0;
    static final FloatingMenuWorldInteractionGuard UNIFIED =
            new FloatingMenuWorldInteractionGuard(UNIFIED_HALF_ANGLE_DEGREES);

    private static final double MINIMUM_VECTOR_LENGTH_SQUARED = 1.0E-8;

    private final double halfAngleDegrees;
    private final double minimumCosine;

    FloatingMenuWorldInteractionGuard(double halfAngleDegrees) {
        if (!Double.isFinite(halfAngleDegrees)
                || halfAngleDegrees <= 0.0 || halfAngleDegrees >= 90.0) {
            throw new IllegalArgumentException("Guard half-angle must be between 0 and 90 degrees");
        }
        this.halfAngleDegrees = halfAngleDegrees;
        this.minimumCosine = Math.cos(Math.toRadians(halfAngleDegrees));
    }

    boolean contains(Location eye, Location sceneCenter) {
        if (eye == null || sceneCenter == null || eye.getWorld() == null
                || !eye.getWorld().equals(sceneCenter.getWorld())) {
            return false;
        }
        return contains(eye.getDirection(), sceneCenter.toVector().subtract(eye.toVector()));
    }

    /** Extends the shared safety cone when a declared panoramic scene occupies more of the view. */
    boolean contains(Location eye, Location sceneCenter, FloatingMenuFraming framing) {
        if (eye == null || sceneCenter == null || eye.getWorld() == null
                || !eye.getWorld().equals(sceneCenter.getWorld()) || framing == null) {
            return false;
        }
        return contains(eye.getDirection(), sceneCenter.toVector().subtract(eye.toVector()),
                framing);
    }

    boolean contains(Vector viewDirection, Vector towardScene, FloatingMenuFraming framing) {
        if (framing == null) return false;
        double sceneHalfAngle = Math.max(framing.horizontalHalfAngleDegrees(),
                framing.verticalHalfAngleDegrees());
        double cosine = Math.cos(Math.toRadians(Math.max(halfAngleDegrees, sceneHalfAngle)));
        return contains(viewDirection, towardScene, cosine);
    }

    boolean contains(Vector viewDirection, Vector towardScene) {
        return contains(viewDirection, towardScene, minimumCosine);
    }

    private boolean contains(Vector viewDirection, Vector towardScene,
                             double requiredCosine) {
        if (!finite(viewDirection) || !finite(towardScene)) return false;
        double viewLengthSquared = viewDirection.lengthSquared();
        if (viewLengthSquared < MINIMUM_VECTOR_LENGTH_SQUARED) return false;
        double sceneLengthSquared = towardScene.lengthSquared();
        if (sceneLengthSquared < MINIMUM_VECTOR_LENGTH_SQUARED) return true;
        double cosine = viewDirection.dot(towardScene)
                / Math.sqrt(viewLengthSquared * sceneLengthSquared);
        return cosine >= requiredCosine;
    }

    private static boolean finite(Vector vector) {
        return vector != null && Double.isFinite(vector.getX())
                && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ());
    }
}
