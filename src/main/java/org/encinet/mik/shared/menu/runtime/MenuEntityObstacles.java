package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;

import java.util.ArrayList;
import java.util.List;

/** Main-thread entity sampling. Model adapters produce the same eye-relative geometry as terrain. */
final class MenuEntityObstacles {
    private MenuEntityObstacles() { }

    static List<Box> capture(Player viewer, Location eye, double maximumDistance) {
        if (viewer == null) return List.of();
        List<Box> boxes = new ArrayList<>();
        Location billboardView = null;
        // Displays and marker stands can have models far outside their zero-sized physics boxes.
        for (var entity : eye.getWorld().getEntitiesByClasses(Display.class, ArmorStand.class)) {
            if (!visible(viewer, entity)) continue;
            if (entity instanceof ArmorStand stand) {
                boxes.addAll(MenuArmorStandObstacles.boxes(eye, stand, maximumDistance));
                continue;
            }
            Display display = (Display) entity;
            if (!MenuDisplayObstacles.hasContent(display)) continue;
            Location modelView = eye;
            if (display.getBillboard() != Display.Billboard.FIXED) {
                if (billboardView == null) {
                    var camera = viewer.getEyeLocation();
                    billboardView = eye.clone();
                    billboardView.setYaw(camera.getYaw());
                    billboardView.setPitch(camera.getPitch());
                }
                modelView = billboardView;
            }
            Box box = MenuDisplayObstacles.box(modelView, display, maximumDistance);
            if (box != null) boxes.add(box);
        }
        double radius = maximumDistance + MenuBodyObstacles.SAMPLING_PADDING;
        for (var entity : eye.getWorld().getNearbyEntities(eye, radius, radius, radius,
                MenuEntityObstacles::hasBody)) {
            if (!visible(viewer, entity)) continue;
            Box box = MenuBodyObstacles.box(eye, entity, maximumDistance);
            if (box != null) boxes.add(box);
        }
        return List.copyOf(boxes);
    }

    private static boolean visible(Player viewer, Entity entity) {
        return entity != viewer && entity.isValid() && viewer.canSee(entity);
    }

    /** New visible entity types participate by default; control entities and particle effects do not. */
    private static boolean hasBody(Entity entity) {
        return !(entity instanceof Display || entity instanceof ArmorStand || entity instanceof Marker || entity instanceof Interaction
                || entity instanceof AreaEffectCloud || entity instanceof LightningStrike);
    }
}
