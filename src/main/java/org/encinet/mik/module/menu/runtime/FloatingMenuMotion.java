package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuRing;
import org.encinet.mik.module.menu.FloatingMenuPose;

final class FloatingMenuMotion {
    private FloatingMenuMotion() { }

    static double progress(double progress) {
        return Math.clamp(progress, 0, 1);
    }

    static double positionFactor(boolean viewerCentered, double progress) {
        return viewerCentered ? 1 : progress(progress);
    }

    static double decorationPositionFactor(boolean fixed, FloatingMenuDecoration.Transition transition, double progress) {
        return positionFactor(fixed || transition == FloatingMenuDecoration.Transition.TRACKING, progress);
    }

    static byte textOpacity(double progress) {
        return (byte) Math.max(4, Math.round(255 * progress(progress)));
    }

    static FloatingMenuPoint approachRing(FloatingMenuPoint current, FloatingMenuPoint target, double fraction) {
        if (!localRingTransition(current, target)) return target;
        return FloatingMenuRing.approach(current, target, fraction);
    }

    static FloatingMenuPose approachOrbit(FloatingMenuPose current, FloatingMenuPose target, double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1)
            throw new IllegalArgumentException("Invalid orbit transition");
        if (Math.abs(current.yawDegrees() - target.yawDegrees()) < 0.001
                && Math.abs(current.pitchDegrees() - target.pitchDegrees()) < 0.001
                && Math.abs(current.up() - target.up()) < 0.001
                && Math.abs(Math.hypot(current.right(), current.forward())
                        - Math.hypot(target.right(), target.forward())) < 0.001) return target;
        double yaw = current.yawDegrees() + (target.yawDegrees() - current.yawDegrees()) * fraction;
        double pitch = current.pitchDegrees() + (target.pitchDegrees() - current.pitchDegrees()) * fraction;
        double radius = Math.hypot(current.right(), current.forward());
        radius += (Math.hypot(target.right(), target.forward()) - radius) * fraction;
        double up = current.up() + (target.up() - current.up()) * fraction;
        double radians = Math.toRadians(yaw);
        return FloatingMenuPose.oriented(new FloatingMenuPoint(radius * Math.sin(radians), up,
                -radius * Math.cos(radians)), yaw, pitch);
    }

    static boolean localRingTransition(FloatingMenuPoint current, FloatingMenuPoint target) {
        double currentRadius = Math.hypot(current.right(), current.forward());
        double targetRadius = Math.hypot(target.right(), target.forward());
        double currentAngle = Math.toDegrees(Math.atan2(current.right(), -current.forward()));
        double targetAngle = Math.toDegrees(Math.atan2(target.right(), -target.forward()));
        double difference = Math.abs(Math.IEEEremainder(targetAngle - currentAngle, 360));
        return difference <= 12 && Math.abs(currentRadius - targetRadius) <= 0.2
                && Math.abs(current.up() - target.up()) <= 0.2;
    }
}
