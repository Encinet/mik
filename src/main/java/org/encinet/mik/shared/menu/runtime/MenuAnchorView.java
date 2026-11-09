package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.MenuScene;

/** Captures the view once before layout, placement and resource allocation use it. */
record MenuAnchorView(Location playerPosition, Location eye, Location readingEye, Vector forward, double eyeHeight) {
    static MenuAnchorView capture(Player player, MenuScene scene) {
        Location eye = player.getEyeLocation().clone();
        double posedHeight = player.getEyeHeight();
        double height = scene.viewpoint().eyeHeight(posedHeight, player.getEyeHeight(true));
        Location readingEye = eye.clone().add(0, height - posedHeight, 0);
        Vector forward = readingEye.getDirection().setY(0);
        if (!Double.isFinite(forward.getX()) || !Double.isFinite(forward.getZ()) || forward.lengthSquared() < 1.0E-8) {
            double yaw = Math.toRadians(readingEye.getYaw());
            forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return new MenuAnchorView(player.getLocation().clone(), eye, readingEye, forward.normalize(), height);
    }
}
