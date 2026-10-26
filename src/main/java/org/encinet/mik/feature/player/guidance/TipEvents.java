package org.encinet.mik.feature.player.guidance;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** On-demand Bukkit subscriptions and bounded transfer from event threads to the main thread. */
final class TipEvents implements AutoCloseable {
    @FunctionalInterface
    interface Registrar {
        /** Return the unregister action for this event type. */
        Runnable register(Class<? extends Event> type, Consumer<Event> handler);
    }

    private final Registrar registrar;
    // Scheduling returns the cancellation action for the queued task.
    private final Function<Runnable, Runnable> scheduleOnMain;
    private final Predicate<Player> currentSession;
    private final Consumer<RuntimeException> errors;
    private final Map<Class<? extends Event>, EventGroup> groups = new HashMap<>();
    private final Map<Player, Dispatch> pending = new IdentityHashMap<>();
    private volatile boolean closed;
    private long sequence;

    TipEvents(Registrar registrar, Function<Runnable, Runnable> scheduleOnMain,
              Predicate<Player> currentSession, Consumer<RuntimeException> errors) {
        this.registrar = registrar;
        this.scheduleOnMain = scheduleOnMain;
        this.currentSession = currentSession;
        this.errors = errors;
    }

    <C> void register(Object owner, Tip<C> tip) {
        if (closed) throw new IllegalStateException("Tip events are closed");
        for (Tip.EventBinding<?, C> binding : tip.triggers()) register(owner, tip, binding);
    }

    private <E extends Event, C> void register(Object owner, Tip<C> tip, Tip.EventBinding<E, C> binding) {
        if (binding.trigger().chance() == 0) return;
        EventGroup group = groups.computeIfAbsent(binding.trigger().eventType(), EventGroup::new);
        var updated = new java.util.ArrayList<>(group.subscriptions);
        updated.add(new Subscription<>(owner, tip, binding));
        group.subscriptions = List.copyOf(updated);
    }

    void forget(Player player) {
        synchronized (pending) {
            Dispatch dispatch = pending.remove(player);
            if (dispatch != null) dispatch.cancel.run();
        }
    }

    private <E extends Event, C> void enqueue(Player player, Subscription<E, C> subscription, E event, C context) {
        synchronized (pending) {
            if (closed || !subscription.active) return;
            Dispatch dispatch = pending.get(player);
            boolean first = dispatch == null;
            if (first) {
                dispatch = new Dispatch(player);
                pending.put(player, dispatch);
            }
            Observation<?, ?> previous = dispatch.observations.get(subscription);
            int occurrences = previous == null ? 1 : Math.min((int) TipState.MAX_PRESSURE, previous.occurrences + 1);
            dispatch.observations.put(subscription, new Observation<>(subscription, event, context, sequence++, occurrences));
            if (!first) return;
            try {
                dispatch.cancel = scheduleOnMain.apply(dispatch);
            } catch (RuntimeException error) {
                pending.remove(player);
                errors.accept(error);
            }
        }
    }

    void close(Object owner) {
        groups.values().removeIf(group -> {
            group.subscriptions.stream().filter(it -> it.owner == owner).forEach(it -> it.active = false);
            group.subscriptions = group.subscriptions.stream().filter(it -> it.active).toList();
            if (!group.subscriptions.isEmpty()) return false;
            group.unregister.run();
            return true;
        });
        synchronized (pending) {
            pending.values().removeIf(dispatch -> {
                dispatch.observations.keySet().removeIf(it -> !it.active);
                if (!dispatch.observations.isEmpty()) return false;
                dispatch.cancel.run();
                return true;
            });
        }
    }

    @Override
    public void close() {
        closed = true;
        groups.values().forEach(group -> {
            group.subscriptions.forEach(it -> it.active = false);
            group.subscriptions = List.of();
            group.unregister.run();
        });
        groups.clear();
        synchronized (pending) {
            pending.values().forEach(it -> it.cancel.run());
            pending.clear();
        }
    }

    private static boolean cancelled(Event event) {
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }

    private void failed(Tip<?> tip, RuntimeException error) {
        errors.accept(new IllegalStateException("Tip event trigger failed: " + tip.id(), error));
    }

    private final class EventGroup {
        private volatile List<Subscription<?, ?>> subscriptions = List.of();
        private final Runnable unregister;

        private EventGroup(Class<? extends Event> type) {
            unregister = registrar.register(type, event -> {
                // Some event subclasses share a HandlerList; a raw executor must check the requested type.
                if (closed || !type.isInstance(event) || cancelled(event)) return;
                for (Subscription<?, ?> subscription : subscriptions) subscription.receive(event);
            });
        }
    }

    private final class Subscription<E extends Event, C> {
        private final Object owner;
        private final Tip<C> tip;
        private final Tip.EventBinding<E, C> binding;
        private volatile boolean active = true;

        private Subscription(Object owner, Tip<C> tip, Tip.EventBinding<E, C> binding) {
            this.owner = owner;
            this.tip = tip;
            this.binding = binding;
        }

        private void receive(Event raw) {
            if (!active) return;
            try {
                E event = binding.trigger().eventType().cast(raw);
                if (!binding.trigger().matches(event)) return;
                Player player = binding.trigger().recipient(event);
                if (player != null) enqueue(player, this, event, binding.context().apply(event));
            } catch (RuntimeException error) {
                failed(tip, error);
            }
        }
    }

    private record Observation<E extends Event, C>(Subscription<E, C> subscription, E event, C context,
                                                   long sequence, int occurrences) {
        private Tip<C> tip() { return subscription.tip; }
        private double chance() { return subscription.binding.trigger().chance(); }
        private boolean valid() {
            return subscription.active && !cancelled(event) && subscription.binding.trigger().verify(event);
        }
        private void offer(Player player) {
            tip().observe(player, context, subscription.binding.trigger(), occurrences);
        }
    }

    private final class Dispatch implements Runnable {
        private final Player player;
        private final Map<Subscription<?, ?>, Observation<?, ?>> observations = new LinkedHashMap<>();
        private Runnable cancel = () -> {};

        private Dispatch(Player player) { this.player = player; }

        @Override
        public void run() {
            List<Observation<?, ?>> ready;
            synchronized (pending) {
                if (!pending.remove(player, this)) return;
                ready = List.copyOf(observations.values());
            }
            if (closed || !currentSession.test(player)) return;
            Map<Tip<?>, Observation<?, ?>> selected = new LinkedHashMap<>();
            for (Observation<?, ?> observation : ready) {
                if (closed) return;
                try {
                    if (!observation.valid()) continue;
                    Observation<?, ?> previous = selected.get(observation.tip());
                    if (previous == null || observation.chance() > previous.chance()
                            || observation.chance() == previous.chance() && observation.sequence > previous.sequence) {
                        selected.put(observation.tip(), observation);
                    }
                } catch (RuntimeException error) {
                    failed(observation.tip(), error);
                }
            }
            for (Observation<?, ?> observation : selected.values()) {
                if (closed || !observation.subscription.active) continue;
                try {
                    observation.offer(player);
                } catch (RuntimeException error) {
                    failed(observation.tip(), error);
                }
            }
        }
    }
}
