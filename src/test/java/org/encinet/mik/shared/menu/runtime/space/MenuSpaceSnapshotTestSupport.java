package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

public final class MenuSpaceSnapshotTestSupport {
    private MenuSpaceSnapshotTestSupport() { }

    public static MenuCollisionSpace capture(Location eye, BoundingBox region) {
        var origin = eye.toVector();
        var terrain = new MenuBlockCollisionSnapshot(eye.getWorld()).capture(region.clone().shift(origin), origin);
        if (!terrain.complete()) throw new AssertionError("Test terrain does not cover the fit");
        return new MenuSpaceSnapshot(region, terrain.boxes().stream().map(MenuOcclusionGeometry.Box::envelope).toList());
    }
}
