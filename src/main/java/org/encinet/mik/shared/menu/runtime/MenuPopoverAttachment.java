package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.encinet.mik.shared.menu.ui.UiSize;
import java.util.List;

/** Keeps an explicit companion stable while its selected source or surrounding geometry changes. */
final class MenuPopoverAttachment {
    @FunctionalInterface interface WorldConstraint {
        double fit(MenuSurfaceAttachment.Placement placement, double readingScale);
    }
    private String sourceId;
    private MenuSurfaceAttachment.Surface source;
    private MenuSurfaceAttachment anchor;
    private MenuSurfaceAttachment.Placement placed;
    private Environment environment;
    private Viewport viewport;
    private record WorldCheck(UiVector3 eye, MenuSurfaceAttachment.Placement placement, double scale, long epoch) { }
    private WorldCheck worldCheck;
    private double worldScale = 1;
    private boolean blocked;
    private record Environment(MenuSurfaceAttachment.Surface source, UiVector3 eye, FloatingMenuSize size,
                               List<MenuSurfaceAttachment.Surface> obstacles) { }

    /** Source-local view coordinates follow the menu, including after a selection replacement. */
    private record Viewport(MenuSurfaceAttachment plane, UiVector3 eye) {
        static Viewport capture(MenuSurfaceAttachment.Pointer pointer, MenuSurfaceAttachment.Surface source) {
            return new Viewport(MenuSurfaceAttachment.relative(source, pointer.center(), pointer.rotation(), 1),
                    source.rotation().inverse().apply(pointer.eye().subtract(source.center())).multiply(1 / source.scale()));
        }
        MenuSurfaceAttachment.Pointer place(MenuSurfaceAttachment.Surface source, FloatingMenuFieldOfView fov) {
            var placed = plane.place(source);
            var origin = source.center().add(source.rotation().apply(eye.multiply(source.scale())));
            double depth = -placed.rotation().inverse().apply(placed.center().subtract(origin)).forward();
            var framing = fov.readingFraming();
            var size = new UiSize(1.8 * depth * framing.horizontalTangent(), 1.8 * depth * framing.verticalTangent());
            double gap = Math.min(.08 * source.scale(), Math.min(size.width(), size.height()) / 16);
            return new MenuSurfaceAttachment.Pointer(placed.center(), placed.rotation(), size, gap, origin);
        }
        Viewport retarget(MenuSurfaceAttachment.Surface previous, MenuSurfaceAttachment.Surface next, FloatingMenuFieldOfView fov) {
            return capture(place(previous, fov), next);
        }
    }

    void reset() {
        sourceId = null; source = null; anchor = null; placed = null; environment = null; viewport = null;
        worldCheck = null; worldScale = 1; blocked = false;
    }
    MenuSurfaceAttachment.Placement placement() { return placed; }
    double readingScale() { return anchor == null ? 1 : anchor.readingScale() * worldScale; }
    boolean blocked() { return blocked; }

    void update(Location eye, String id, MenuSurfaceAttachment.Surface next, FloatingMenuSize size,
                FloatingMenuFieldOfView fov, List<MenuSurfaceAttachment.Surface> obstacles, long worldEpoch, WorldConstraint world) {
        var surroundings = new Environment(next, MenuSurfaceAttachment.point(eye.toVector()), size, List.copyOf(obstacles));
        if (anchor == null) {
            var pointer = MenuSurfaceAttachment.pointer(eye, next, fov).orElseGet(() -> {
                Location towards = eye.clone().setDirection(MenuSurfaceAttachment.vector(next.center()).subtract(eye.toVector()));
                return MenuSurfaceAttachment.pointer(towards, next, fov).orElse(null);
            });
            if (pointer == null) { blocked = true; placed = null; worldCheck = null; return; }
            viewport = Viewport.capture(pointer, next);
            anchor = MenuSurfaceAttachment.attach(pointer, next, size, obstacles)
                    .orElseGet(() -> MenuSurfaceAttachment.centered(pointer, next, size, obstacles));
        } else {
            if (!id.equals(sourceId)) {
                anchor = anchor.retarget(source, next);
                viewport = viewport.retarget(source, next, fov);
            }
            if (!surroundings.equals(environment)) anchor = anchor.inFront(surroundings.eye(), next, size, obstacles);
        }
        var constrained = anchor.constrained(viewport.place(next, fov), next, size);
        if (constrained != anchor) anchor = constrained.inFront(surroundings.eye(), next, size, obstacles);
        environment = surroundings;
        sourceId = id; source = next;
        var proposal = anchor.place(next);
        var check = new WorldCheck(surroundings.eye(), proposal, anchor.readingScale(), worldEpoch);
        if (!check.equals(worldCheck)) {
            double factor = world.fit(proposal, anchor.readingScale());
            blocked = !Double.isFinite(factor) || factor <= 1e-6 || factor > 1;
            worldScale = blocked ? 1 : factor;
            worldCheck = check;
        }
        placed = blocked ? null : new MenuSurfaceAttachment.Placement(surroundings.eye()
                .add(proposal.center().subtract(surroundings.eye()).multiply(worldScale)), proposal.rotation());
    }
}
