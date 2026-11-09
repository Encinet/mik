package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiAttachmentPlacement;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiTextMetrics;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** Interface reading regions win over world overlays, independently of wall depth or panel opacity. */
final class MenuWorldOverlayMask implements Predicate<Location> {
    private static final double CLEARANCE = .01;
    private static final double PARTICLE_RADIUS = .12;
    private final UUID world;
    private final UiRotation rotation;
    private final MenuViewProjection view;
    private final List<UiAttachmentPlacement.Rect> regions;

    MenuWorldOverlayMask(Location eye, List<MenuSurfaceAttachment.Surface> surfaces) {
        world = eye.getWorld().getUID();
        rotation = UiRotation.degrees(eye.getYaw() + 180, -eye.getPitch(), 0);
        UiVector3 origin = MenuSurfaceAttachment.point(eye.toVector());
        view = new MenuViewProjection(origin, origin.add(MenuSurfaceAttachment.point(eye.getDirection())), rotation);
        regions = surfaces.stream().flatMap(surface -> view.project(surface).stream()).map(projected -> {
            var rect = projected.rect();
            return new UiAttachmentPlacement.Rect(rect.x()-CLEARANCE, rect.y()-CLEARANCE,
                    rect.width()+CLEARANCE*2, rect.height()+CLEARANCE*2);
        }).toList();
    }

    /** One snapshot per effect update; small particle quads include a margin at panel edges. */
    @Override public boolean test(Location point) {
        if (point.getWorld() == null || !world.equals(point.getWorld().getUID())) return false;
        return !overlaps(new MenuSurfaceAttachment.Surface(MenuSurfaceAttachment.point(point.toVector()), rotation,
                new FloatingMenuSize(PARTICLE_RADIUS*2,PARTICLE_RADIUS*2), 1, PARTICLE_RADIUS*2));
    }

    boolean overlaps(MenuSurfaceAttachment.Surface overlay) {
        if (regions.isEmpty()) return false;
        return view.project(overlay).map(projected -> regions.stream()
                .anyMatch(region -> region.intersectionArea(projected.rect()) > 0)).orElse(false);
    }

    /** TextDisplay is bottom anchored; its culling width/height are not the painted text bounds. */
    static MenuSurfaceAttachment.Surface textSurface(Location at, float yaw, float pitch, FloatingMenuDecoration.Text text) {
        var extent = UiTextMetrics.measure(text.text(), Math.max(80,Math.round(text.displayWidth()*64)));
        double pixel = .025*text.scale();
        double width = (extent.widthPixels()+4)*pixel, height = (extent.lines()*10+4)*pixel;
        var center = at.toVector().add(FloatingMenuSurfaceGeometry.panelUp(yaw,pitch).multiply(height/2));
        return new MenuSurfaceAttachment.Surface(MenuSurfaceAttachment.point(center), UiRotation.degrees(yaw,pitch,0),
                new FloatingMenuSize(width,height),1);
    }
}
