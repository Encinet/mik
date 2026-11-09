package org.encinet.mik.shared.menu;

import java.util.Objects;
import org.bukkit.Location;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** A fixed, front-facing world plane. Its physical extent and density are independent of the viewer's FOV. */
public record WorldMenuSurface(Location center, UiRotation rotation, FloatingMenuSize size, double scale, double reach) {
    public WorldMenuSurface {
        center = Objects.requireNonNull(center,"center").clone();
        Objects.requireNonNull(center.getWorld(),"world");
        Objects.requireNonNull(rotation,"rotation"); Objects.requireNonNull(size,"size");
        if (!Double.isFinite(center.getX()) || !Double.isFinite(center.getY()) || !Double.isFinite(center.getZ())
                || !Double.isFinite(scale) || scale <= 0 || !Double.isFinite(reach) || reach <= 0 || reach > 32
                || size.width() <= 0 || size.height() <= 0 || size.width() > 16 || size.height() > 16
                || size.width() / (UiSceneContent.WORLD_PER_UI * scale) > 4096
                || size.height() / (UiSceneContent.WORLD_PER_UI * scale) > 4096)
            throw new IllegalArgumentException("Invalid world menu surface");
    }
    @Override public Location center() { return center.clone(); }

    public UiSize viewport() { return new UiSize(size.width() / (UiSceneContent.WORLD_PER_UI * scale),
            size.height() / (UiSceneContent.WORLD_PER_UI * scale)); }

    public Location position(double right, double up, double forward) {
        var offset = rotation.apply(new UiVector3(right,up,forward));
        return center.clone().add(offset.right(),offset.up(),offset.forward());
    }
}
