package org.encinet.mik.module.vehicle.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class VehicleMoveEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID vehicle;
    private final UUID owner;
    private final Player driver;
    private final Location from;
    private final Location to;
    private boolean cancelled;

    public VehicleMoveEvent(UUID vehicle, UUID owner, Player driver, Location from, Location to) {
        this.vehicle = vehicle;
        this.owner = owner;
        this.driver = driver;
        this.from = from.clone();
        this.to = to.clone();
    }
    public UUID getVehicleId() { return vehicle; }
    public UUID getOwnerId() { return owner; }
    public Player getDriver() { return driver; }
    public Location getFrom() { return from.clone(); }
    public Location getTo() { return to.clone(); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
