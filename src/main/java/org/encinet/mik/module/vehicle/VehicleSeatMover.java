package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftArmorStand;
import org.bukkit.entity.ArmorStand;

final class VehicleSeatMover {
    static void move(ArmorStand seat, Location destination) {
        var entity = ((CraftArmorStand) seat).getHandle();
        entity.setOldPosAndRot();
        entity.setPos(destination.getX(), destination.getY(), destination.getZ());
        entity.setYRot(destination.getYaw());
        for (var passenger : entity.getPassengers()) entity.positionRider(passenger);
    }
}
