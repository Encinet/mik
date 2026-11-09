package org.encinet.mik.shared.menu.runtime;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.OminousItemSpawner;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.encinet.mik.shared.menu.runtime.space.MenuModelGeometry;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;
import org.joml.Matrix4d;

/** Native body bounds plus conservative envelopes for models and animations outside their physics boxes. */
final class MenuBodyObstacles {
    // Covers a trident's 27-pixel shaft as well as the smaller bobbing, swelling and sprite envelopes below.
    static final double SAMPLING_PADDING = 2;

    private MenuBodyObstacles() { }

    static Box box(Location eye, Entity entity, double maximumDistance) {
        if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return null;
        if (entity instanceof LivingEntity living && living.isInvisible() && !equipped(living)) return null;
        if (entity instanceof Item item && item.getItemStack().isEmpty()) return null;
        if (entity instanceof OminousItemSpawner spawner && spawner.getItem().isEmpty()) return null;
        if (entity instanceof Trident) {
            var location = entity.getLocation();
            var offset = location.toVector().subtract(eye.toVector());
            // ThrownTridentRenderer and TridentModel: retain the long shaft and its yaw/pitch.
            var pose = new Matrix4d().translate(offset.getX(), offset.getY(), offset.getZ())
                    .rotateY(Math.toRadians(location.getYaw() - 90)).rotateZ(Math.toRadians(location.getPitch() + 90))
                    .scale(1.0 / 16);
            return MenuModelGeometry.transform(new BoundingBox(-2.5, -4, -.5, 2.5, 27, .5), pose, maximumDistance);
        }
        var bounds = entity.getBoundingBox().clone();
        // Reserve the whole animation cycle so the menu does not follow individual bob/spin/fuse phases.
        if (entity instanceof Item || entity instanceof EnderSignal || entity instanceof OminousItemSpawner)
            bounds.expand(.35, .5, .35);
        else if (entity instanceof EnderCrystal) bounds.expand(0, 1, 0);
        else if (entity instanceof TNTPrimed) bounds.expand(.17);
        else if (entity instanceof FallingBlock) bounds.expand(.01);
        else if (entity instanceof Projectile) bounds.expand(.25);
        if (bounds.getVolume() <= 1.0E-10) return null;
        Box box = MenuOcclusionGeometry.box(bounds, eye.toVector());
        return MenuModelGeometry.inRange(box.envelope(), maximumDistance) ? box : null;
    }

    private static boolean equipped(LivingEntity entity) {
        var equipment = entity.getEquipment();
        if (equipment == null) return false;
        for (var slot : EquipmentSlot.values()) {
            if (entity.canUseEquipmentSlot(slot) && !equipment.getItem(slot).isEmpty()) return true;
        }
        return false;
    }
}
