package org.encinet.mik.integration.axiom;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class AxiomVehicleProtectionTest {
    @Test void modificationAndDeletionEventsAreCancelledForManagedEntitiesOnly() throws Exception {
        var entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, arguments) -> { throw new UnsupportedOperationException(method.getName()); });
        var event = new EntityEvent(entity);
        var getter = AxiomVehicleProtection.entityGetter(EntityEvent.class);
        AxiomVehicleProtection.protect(event, getter, candidate -> candidate == entity);
        assertTrue(event.isCancelled());
        var source = new EntityEvent(entity);
        AxiomVehicleProtection.protect(source, getter, candidate -> false);
        assertFalse(source.isCancelled());
        source.setCancelled(true);
        AxiomVehicleProtection.protect(source, getter, candidate -> false);
        assertTrue(source.isCancelled());
    }

    @Test void incompatibleEventApisAreRejectedWithoutAxiomOnTheTestClasspath() {
        assertThrows(NoSuchMethodException.class, () -> AxiomVehicleProtection.entityGetter(UncancellableEvent.class));
        assertThrows(NoSuchMethodException.class, () -> AxiomVehicleProtection.entityGetter(WrongEntityEvent.class));
    }

    public static class EntityEvent extends Event implements Cancellable {
        private final Entity entity;
        private boolean cancelled;
        EntityEvent(Entity entity) { this.entity = entity; }
        public Entity getEntity() { return entity; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean value) { cancelled = value; }
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }
    public static class UncancellableEvent extends Event {
        public Entity getEntity() { return null; }
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }
    public static class WrongEntityEvent extends Event implements Cancellable {
        public String getEntity() { return "unsupported"; }
        @Override public boolean isCancelled() { return false; }
        @Override public void setCancelled(boolean value) { }
        @Override public HandlerList getHandlers() { return new HandlerList(); }
    }
}
