package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuFieldOfView;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSpatialFrame;

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
        return FloatingMenuPose.oriented(new FloatingMenuPoint(pose.right(), pose.up(), pose.forward() / factor),
                Math.toDegrees(Math.atan2(Math.sin(yaw) * factor, Math.cos(yaw))), pose.pitchDegrees());
    }
}
