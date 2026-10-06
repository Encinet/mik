package org.encinet.mik.module.vehicle.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class VehicleUseEvent extends Event implements Cancellable {
    public enum Action { CAPTURE, SPAWN, ENTER, SERVICE, REMOVE }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final UUID vehicle;
    private final Location location;
    private final Action action;
    private boolean cancelled;

    public VehicleUseEvent(Player player, UUID vehicle, Location location, Action action) {
        this.player = player;
        this.vehicle = vehicle;
        this.location = location.clone();
        this.action = action;
    }
    public Player getPlayer() { return player; }
    public UUID getVehicleId() { return vehicle; }
    public Location getLocation() { return location.clone(); }
    public Action getAction() { return action; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
