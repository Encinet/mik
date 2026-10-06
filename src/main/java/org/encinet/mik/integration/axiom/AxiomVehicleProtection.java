package org.encinet.mik.integration.axiom;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class AxiomVehicleProtection implements Listener {
    private record Binding(Class<? extends Event> event, Method entity) { }
    private final JavaPlugin plugin;
    private final Predicate<Entity> protectedEntity;
    private final Listener entityListener = new Listener() { };
    private Plugin attached;

    public AxiomVehicleProtection(JavaPlugin plugin, Predicate<Entity> protectedEntity) {
        this.plugin = plugin;
        this.protectedEntity = protectedEntity;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Plugin axiom = plugin.getServer().getPluginManager().getPlugin("AxiomPaper");
        if (axiom != null && axiom.isEnabled()) attach(axiom);
    }

    @EventHandler public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("AxiomPaper")) attach(event.getPlugin());
    }
    @EventHandler public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() == attached) detach();
    }

    private void attach(Plugin axiom) {
        if (attached == axiom) return;
        detach();
        try {
            List<Binding> bindings = new ArrayList<>();
            for (String name : List.of("AxiomManipulateEntityEvent", "AxiomRemoveEntityEvent")) {
                Class<? extends Event> event = Class.forName("com.moulberry.axiom.event." + name, false,
                        axiom.getClass().getClassLoader()).asSubclass(Event.class);
                Method getter = entityGetter(event);
                bindings.add(new Binding(event, getter));
            }
            for (Binding binding : bindings) plugin.getServer().getPluginManager().registerEvent(binding.event(), entityListener,
                    EventPriority.HIGHEST, (listener, event) -> {
                        if (!binding.event().isInstance(event)) return;
                        try { protect(event, binding.entity(), protectedEntity); }
                        catch (ReflectiveOperationException exception) { throw new EventException(exception); }
                    }, plugin, true);
            attached = axiom;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            detach();
            plugin.getLogger().warning("Axiom entity event protection unavailable; group import remains supported: " + exception);
        }
    }

    static Method entityGetter(Class<? extends Event> event) throws NoSuchMethodException {
        if (!Cancellable.class.isAssignableFrom(event)) throw new NoSuchMethodException("Axiom event is not cancellable");
        Method getter = event.getMethod("getEntity");
        if (!Entity.class.isAssignableFrom(getter.getReturnType())) throw new NoSuchMethodException("Axiom entity getter has an unsupported return type");
        return getter;
    }

    static void protect(Event event, Method getter, Predicate<Entity> protectedEntity) throws ReflectiveOperationException {
        if (event instanceof Cancellable cancellable && getter.invoke(event) instanceof Entity entity && protectedEntity.test(entity))
            cancellable.setCancelled(true);
    }

    private void detach() { HandlerList.unregisterAll(entityListener); attached = null; }
    public void close() { detach(); HandlerList.unregisterAll(this); }
}
