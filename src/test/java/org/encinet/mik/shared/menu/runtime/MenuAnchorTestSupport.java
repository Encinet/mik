package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;

/** Pure reference and collision passes; the presentation applies viewing comfort before publication. */
final class MenuAnchorTestSupport {
    private MenuAnchorTestSupport() { }

    static FloatingMenuAnchorResolver.Anchor front(Location eye, Vector forward, FloatingMenuAnchorResolver.SceneBounds bounds) {
        return front(eye, forward, bounds, 1, FloatingMenuFieldOfView.DEFAULT);
    }

    static FloatingMenuAnchorResolver.Anchor front(Location eye, Vector forward, FloatingMenuAnchorResolver.SceneBounds bounds,
                                                   double scale) {
        return front(eye, forward, bounds, scale, FloatingMenuFieldOfView.DEFAULT);
    }

    static FloatingMenuAnchorResolver.Anchor front(Location eye, Vector forward, FloatingMenuAnchorResolver.SceneBounds bounds,
                                                   double scale, FloatingMenuFieldOfView fov) {
        return FloatingMenuAnchorResolver.fit(eye, FloatingMenuAnchorResolver.reference(eye, forward, bounds, scale, fov), bounds);
    }

    static FloatingMenuAnchorResolver.Anchor arc(Location eye, Vector forward, double scale, FloatingMenuAnchorResolver.SceneBounds bounds) {
        return arc(eye, forward, scale, bounds, FloatingMenuFieldOfView.DEFAULT);
    }

    static FloatingMenuAnchorResolver.Anchor arc(Location eye, Vector forward, double scale, FloatingMenuAnchorResolver.SceneBounds bounds,
                                                 FloatingMenuFieldOfView fov) {
        return FloatingMenuAnchorResolver.fit(eye, FloatingMenuAnchorResolver.frontArcReference(eye, forward, scale, bounds, fov), bounds);
    }
}
