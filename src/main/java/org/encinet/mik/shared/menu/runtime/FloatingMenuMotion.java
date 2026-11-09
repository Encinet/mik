package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuRing;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;

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
        return (byte) Math.round(255 * progress(progress));
    }

    static boolean inputReady(double reveal, double contentAlpha) {
        return progress(reveal) * progress(contentAlpha) >= 0.15;
    }

    /** Entry motion changes only depth; reading lines and target spacing retain their final layout. */
    static double entryDepth(double reveal, FloatingMenuAnimation animation) {
        double visible = progress(reveal);
        return animation.entryOffset() == 0 || visible == 1 ? 0 : -animation.entryOffset() * (1 - visible);
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
                && Math.abs(Math.IEEEremainder(current.rollDegrees() - target.rollDegrees(), 360)) < 0.001
                && Math.abs(current.scale() - target.scale()) < .001
                && Math.abs(current.up() - target.up()) < 0.001
                && Math.abs(Math.hypot(current.right(), current.forward())
                        - Math.hypot(target.right(), target.forward())) < 0.001) return target;
        double yaw = current.yawDegrees() + (target.yawDegrees() - current.yawDegrees()) * fraction;
        double pitch = current.pitchDegrees() + (target.pitchDegrees() - current.pitchDegrees()) * fraction;
        double radius = Math.hypot(current.right(), current.forward());
        radius += (Math.hypot(target.right(), target.forward()) - radius) * fraction;
        double up = current.up() + (target.up() - current.up()) * fraction;
        double radians = Math.toRadians(yaw);
        double roll = current.rollDegrees() + Math.IEEEremainder(target.rollDegrees() - current.rollDegrees(), 360) * fraction;
        return new FloatingMenuPose(new FloatingMenuPoint(radius * Math.sin(radians), up,
                -radius * Math.cos(radians)), yaw, pitch, roll, step(current.scale(), target.scale(), fraction));
    }

    static FloatingMenuPose approachPose(FloatingMenuPose current, FloatingMenuPose target, double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1) throw new IllegalArgumentException("Invalid pose transition");
        var point = new FloatingMenuPoint(step(current.right(), target.right(), fraction), step(current.up(), target.up(), fraction),
                step(current.forward(), target.forward(), fraction));
        double scale = step(current.scale(), target.scale(), fraction);
        var a = current.rotation();
        var b = target.rotation();
        double dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        boolean aligned = dot >= 1 - 1e-10;
        if (point.equals(target.point()) && scale == target.scale() && aligned) return target;
        return FloatingMenuPose.transformed(point, aligned ? b : a.interpolate(b, fraction), scale);
    }

    private static double step(double current, double target, double fraction) {
        double next = current + (target - current) * fraction;
        return Math.abs(target - next) < .001 ? target : next;
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
