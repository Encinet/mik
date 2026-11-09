package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** One reading calibration shared by parent content and its independently measured companions. */
record MenuReadingParameters(UiVector3 origin, double scale, double nominalScale, FloatingMenuFieldOfView fieldOfView) {
    MenuReadingParameters {
        java.util.Objects.requireNonNull(origin);
        if (!Double.isFinite(scale) || scale <= 0 || !Double.isFinite(nominalScale) || nominalScale <= 0)
            throw new IllegalArgumentException("Invalid menu reading parameters");
        java.util.Objects.requireNonNull(fieldOfView);
    }

    static MenuReadingParameters from(Location eye, FloatingMenuAnchorResolver.Anchor anchor, FloatingMenuFieldOfView fov, double interfaceScale) {
        var delta = anchor.origin().toVector().subtract(eye.toVector());
        var origin = new UiVector3(delta.dot(anchor.right()), delta.getY(), -delta.dot(anchor.forward()));
        return new MenuReadingParameters(origin, anchor.spatialScale(), MenuViewportLayout.scale(interfaceScale), fov);
    }

    /** Pure calibration for geometry-only callers and a temporarily occluded first frame. */
    static MenuReadingParameters estimated(FloatingMenuAnchorResolver.SceneBounds bounds,
                                            FloatingMenuFieldOfView fov, double interfaceScale) {
        double distance = MenuViewportLayout.distance(fov);
        double scale = Math.min(MenuViewportLayout.scale(interfaceScale), bounds.readableScale(distance, 0, fov.readingFraming()));
        return new MenuReadingParameters(new UiVector3(0, 0, -distance), scale, MenuViewportLayout.scale(interfaceScale), fov);
    }

    UiSize viewport(UiSize declared, FloatingMenuPose source) {
        var center = origin.add(new UiVector3(source.right(), source.up(), source.forward()).multiply(scale));
        double depth = Math.max(scale * .1,
                Math.sqrt(center.right() * center.right() + center.up() * center.up() + center.forward() * center.forward()));
        return MenuViewportLayout.viewport(declared, fieldOfView, depth, scale, nominalScale);
    }
}
