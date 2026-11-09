package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;

final class FloatingMenuViewProjection {
    private FloatingMenuViewProjection() { }

    static FloatingMenuPose pose(FloatingMenuSpatialFrame frame, FloatingMenuFieldOfView fieldOfView,
                                 FloatingMenuPose pose) {
        return pose(frame, fieldOfView, 1, pose);
    }

    static FloatingMenuPose pose(FloatingMenuSpatialFrame frame, FloatingMenuFieldOfView fieldOfView,
                                 double interfaceScale, FloatingMenuPose pose) {
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0)
            throw new IllegalArgumentException("Interface scale must be positive and finite");
        if (!frame.viewerCentered()) return pose;
        double factor = fieldOfView.projectionFactor() * interfaceScale;
        if (Math.abs(factor - 1) < 1.0E-12) return pose;
        double yaw = Math.toRadians(pose.yawDegrees());
        double projectedYaw = Math.toDegrees(Math.atan2(Math.sin(yaw) * factor, Math.cos(yaw)));
        projectedYaw = pose.yawDegrees() + Math.IEEEremainder(projectedYaw - pose.yawDegrees(), 360);
        return new FloatingMenuPose(new FloatingMenuPoint(pose.right(), pose.up(), pose.forward() / factor),
                projectedYaw, pose.pitchDegrees(), pose.rollDegrees(), pose.scale());
    }
}
