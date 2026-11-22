package org.encinet.mik.shared.menu.runtime.input;

import org.encinet.mik.shared.menu.runtime.space.FloatingMenuSurfaceGeometry;

import java.util.List;
import java.util.function.Function;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiRotation;

/** Picks published window planes before considering controls, so empty content protects its background. */
public final class MenuWindowInput {
    public record Surface(String path, FloatingMenuPose pose, FloatingMenuSize size) { }

    private MenuWindowInput() { }

    public static String frontmost(List<Surface> surfaces, Vector eye, Vector direction,
                            Function<FloatingMenuPose, Vector> position, UiRotation orientation, double scale) {
        double nearest = Double.POSITIVE_INFINITY;
        String path = null;
        for (var surface : surfaces) {
            var pose = surface.pose();
            var angles = orientation.compose(pose.rotation()).angles();
            double actualScale = scale * pose.scale();
            var hit = FloatingMenuSurfaceGeometry.intersect(eye, direction, position.apply(pose),
                    (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                    surface.size().width() * actualScale, surface.size().height() * actualScale, 0);
            if (hit.isPresent() && hit.get().distance() < nearest) {
                nearest = hit.get().distance(); path = surface.path();
            }
        }
        return path;
    }
}
