package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuAction;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiFrame;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Resolve a collection before its boundary action, so an exhausted list never scrolls another list. */
final class MenuPaginationInput {
    record Region(UiFrame.Pagination binding, FloatingMenuPose pose, FloatingMenuSize size) { }

    private MenuPaginationInput() { }

    static FloatingMenuAction action(List<Region> regions, String target, String prefix,
                                     FloatingMenuInteraction interaction,
                                     Function<Region, Optional<FloatingMenuSurfaceGeometry.Hit>> intersect) {
        if (!interaction.scroll()) return null;
        Region owner = null;
        if (target != null) for (var region : regions) {
            String path = prefix + region.binding().path();
            if ((target.equals(path) || target.startsWith(path + "/"))
                    && (owner == null || path.length() > prefix.length() + owner.binding().path().length())) owner = region;
        }
        if (owner != null) return owner.binding().action(interaction);

        double distance = Double.POSITIVE_INFINITY;
        double centerDistance = Double.POSITIVE_INFINITY;
        for (var region : regions) {
            var hit = intersect.apply(region);
            if (hit.isEmpty()) continue;
            var value = hit.orElseThrow();
            boolean nearer = value.distance() < distance - 1e-6;
            boolean samePlane = Math.abs(value.distance() - distance) <= 1e-6;
            boolean nested = owner != null && region.binding().path().startsWith(owner.binding().path() + "/");
            if (nearer || samePlane && (nested || owner == null
                    || !owner.binding().path().startsWith(region.binding().path() + "/")
                    && value.normalizedCenterDistance() < centerDistance)) {
                owner = region;
                distance = value.distance();
                centerDistance = value.normalizedCenterDistance();
            }
        }
        return owner == null ? null : owner.binding().action(interaction);
    }
}
