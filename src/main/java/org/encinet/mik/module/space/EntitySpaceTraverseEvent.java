package org.encinet.mik.module.space;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

import java.util.Objects;

/**
 * Fired before an entity root and its complete passenger tree cross a spatial link.
 * The event entity is always the outermost vehicle, or the independently moving entity.
 */
public final class EntitySpaceTraverseEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SpaceTransition transition;
    private boolean cancelled;

    public EntitySpaceTraverseEvent(Entity entity, SpaceTransition transition) {
        super(entity);
        this.transition = Objects.requireNonNull(transition, "transition");
    }

    public SpaceTransition transition() {
        return transition;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
