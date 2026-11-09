package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import java.util.Optional;

/** World hosts supply a fixed frame and a finite viewport, without viewer-distance fitting or camera-relative movement. */
final class MenuWorldPresentation {
    private MenuWorldPresentation() { }

    static MenuPresentation.Frame prepare(MenuPresentation presentation, WorldMenuSurface surface, MenuScene scene,
                                           double typography, UiThemeColors theme, int entityCapacity) {
        requireScene(scene);
        var geometry = presentation.worldLayout(scene,surface.viewport(),typography,theme,entityCapacity);
        return new MenuPresentation.Frame(geometry,Optional.of(anchor(surface,geometry)));
    }

    static void requireScene(MenuScene scene) {
        if (scene.spatialFrame() != FloatingMenuSpatialFrame.IN_FRONT || scene.popover() != null || scene.titleVisible())
            throw new IllegalArgumentException("World panels use a planar scene; open a foreground child for a separate detail or editor");
        if (scene.decorations().values().stream().anyMatch(FloatingMenuDecoration::worldAnchored))
            throw new IllegalArgumentException("A world panel's content belongs to its declared surface");
    }

    static FloatingMenuAnchorResolver.Anchor anchor(WorldMenuSurface surface, SceneGeometry geometry) {
        var normal = surface.rotation().apply(new UiVector3(0,0,1));
        var right = surface.rotation().apply(new UiVector3(1,0,0));
        double rear = geometry.bounds().surfaces().stream().mapToDouble(FloatingMenuAnchorResolver.Surface::minimumForward).min().orElse(0);
        double nodeScale = geometry.poses().values().stream().mapToDouble(FloatingMenuPose::scale).max().orElse(1);
        double decorationScale = geometry.decorations(geometry.definition()).values().stream()
                .filter(decoration -> decoration.placement() instanceof FloatingMenuPlacement.Local)
                .mapToDouble(decoration -> ((FloatingMenuPlacement.Local)decoration.placement()).pose().scale()).max().orElse(1);
        var animation = geometry.definition().animation();
        double motion = (animation.entryOffset() + animation.pressOffset())
                * Math.max(nodeScale,decorationScale);
        Location origin = surface.position(0,0,(Math.max(0,-rear) + motion) * surface.scale());
        return new FloatingMenuAnchorResolver.Anchor(origin,new Vector(-normal.right(),-normal.up(),-normal.forward()),
                new Vector(right.right(),right.up(),right.forward()),surface.scale(),surface.reach(),0);
    }

    static Optional<FloatingMenuSurfaceGeometry.Hit> hit(WorldMenuSurface surface, Location origin, UiSize content, Location eye) {
        if (!surface.center().getWorld().equals(eye.getWorld())) return Optional.empty();
        var normal = surface.rotation().apply(new UiVector3(0,0,1));
        Vector facing = new Vector(normal.right(),normal.up(),normal.forward());
        if (eye.getDirection().dot(facing) >= -1e-6 || eye.toVector().subtract(origin.toVector()).dot(facing) <= 0)
            return Optional.empty();
        var angles = surface.rotation().angles();
        return FloatingMenuSurfaceGeometry.intersect(eye.toVector(),eye.getDirection(),origin.toVector(),
                (float) angles.yaw(),(float) angles.pitch(),(float) angles.roll(),
                content.width() * UiSceneContent.WORLD_PER_UI * surface.scale(),
                content.height() * UiSceneContent.WORLD_PER_UI * surface.scale(),.02 * surface.scale())
                .filter(hit -> hit.distance() <= surface.reach() && FloatingMenuWorldOcclusion.clearToSurface(eye,hit.distance()));
    }

    static boolean nearby(WorldMenuSurface surface, Player player) {
        if (!surface.center().getWorld().equals(player.getWorld())) return false;
        Location center = surface.center();
        if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4,center.getBlockZ() >> 4)) return false;
        double distance = surface.reach() + Math.hypot(surface.size().width(),surface.size().height()) / 2;
        return player.getEyeLocation().distanceSquared(center) <= distance * distance;
    }
}
