package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Reusable typed screen definition. It owns per-player state and active flows so
 * modules do not need UUID maps, current-menu checks or reopen methods.
 */
public final class FloatingMenuScreen<S> {
    private final String id;
    private final Function<Player, S> initialState;
    private final FloatingMenuRenderer<S> renderer;
    private final Map<UUID, S> states = new ConcurrentHashMap<>();
    private final Map<UUID, FloatingMenuFlow<S>> flows = new ConcurrentHashMap<>();

    /** Creates a screen whose callers always provide the initial state explicitly. */
    public FloatingMenuScreen(String id, FloatingMenuRenderer<S> renderer) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Screen id must not be blank");
        this.id = id;
        this.initialState = null;
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public FloatingMenuScreen(String id, Function<Player, S> initialState,
                              FloatingMenuRenderer<S> renderer) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Screen id must not be blank");
        this.id = id;
        this.initialState = Objects.requireNonNull(initialState, "initialState");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public String id() { return id; }

    public FloatingMenuFlow<S> open(Player player) {
        if (initialState == null) {
            throw new IllegalStateException(
                    "Screen '" + id + "' requires an explicit initial state");
        }
        S state = states.computeIfAbsent(player.getUniqueId(), ignored ->
                Objects.requireNonNull(initialState.apply(player), "initial state"));
        return open(player, state);
    }

    public FloatingMenuFlow<S> open(Player player, S state) {
        UUID playerId = player.getUniqueId();
        states.put(playerId, Objects.requireNonNull(state, "state"));
        FloatingMenuFlow<S> existing = flows.get(playerId);
        if (existing != null) {
            FloatingMenuState lifecycle = existing.handle().state();
            if (lifecycle == FloatingMenuState.SUSPENDED) {
                existing.present(state);
                return existing;
            }
            if (lifecycle == FloatingMenuState.OPENING || lifecycle == FloatingMenuState.ACTIVE) {
                existing.setState(state);
                return existing;
            }
            flows.remove(playerId, existing);
        }
        FloatingMenuFlow<S> flow = FloatingMenuFlow.open(player, state, context -> {
            states.put(playerId, context.state());
            return renderer.render(context).identifiedBy(id).withLifecycle(
                    (closedPlayer, handle, previous, current, reason) -> {
                        if (current != FloatingMenuState.CLOSED) return;
                        FloatingMenuFlow<S> registered = flows.get(playerId);
                        if (registered != null && registered.handle() != null
                                && registered.handle().id().equals(handle.id())) {
                            flows.remove(playerId, registered);
                        }
                        if (reason == FloatingMenuCloseReason.QUIT
                                || reason == FloatingMenuCloseReason.PLUGIN_DISABLE) {
                            states.remove(playerId);
                        }
                    });
        });
        flows.put(playerId, flow);
        return flow;
    }

    public Optional<S> state(Player player) {
        return state(player.getUniqueId());
    }

    public Optional<S> state(UUID playerId) {
        return Optional.ofNullable(states.get(playerId));
    }

    public Optional<FloatingMenuFlow<S>> flow(Player player) {
        FloatingMenuFlow<S> flow = flows.get(player.getUniqueId());
        if (flow != null && flow.handle().state() == FloatingMenuState.CLOSED) {
            flows.remove(player.getUniqueId(), flow);
            flow = null;
        }
        return Optional.ofNullable(flow);
    }

    public void update(Player player, UnaryOperator<S> update) {
        flow(player).ifPresentOrElse(flow -> flow.update(update), () -> {
            UUID playerId = player.getUniqueId();
            states.computeIfPresent(playerId, (ignored, value) ->
                    Objects.requireNonNull(update.apply(value), "updated state"));
        });
    }

    /**
     * Redraws every open or suspended flow whose state matches {@code selector}.
     * Matching suspended flows are reconciled in their navigation frame and are
     * not brought in front of the player's current child screen.
     */
    public void redrawWhere(Predicate<? super S> selector) {
        Objects.requireNonNull(selector, "selector");
        if (flows.isEmpty()) return;
        FloatingMenus.execute(() -> {
            for (Map.Entry<UUID, FloatingMenuFlow<S>> entry :
                    java.util.List.copyOf(flows.entrySet())) {
                FloatingMenuFlow<S> flow = entry.getValue();
                FloatingMenuState lifecycle = flow.handle().state();
                if (lifecycle == FloatingMenuState.CLOSED) {
                    flows.remove(entry.getKey(), flow);
                } else if (lifecycle != FloatingMenuState.CLOSING
                        && selector.test(flow.state())) {
                    flow.redraw();
                }
            }
        });
    }

    /** Updates and redraws all matching flows while preserving each flow's hierarchy position. */
    public void updateWhere(Predicate<? super S> selector, UnaryOperator<S> update) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(update, "update");
        if (flows.isEmpty()) return;
        FloatingMenus.execute(() -> {
            for (Map.Entry<UUID, FloatingMenuFlow<S>> entry :
                    java.util.List.copyOf(flows.entrySet())) {
                FloatingMenuFlow<S> flow = entry.getValue();
                FloatingMenuState lifecycle = flow.handle().state();
                if (lifecycle == FloatingMenuState.CLOSED) {
                    flows.remove(entry.getKey(), flow);
                } else if (lifecycle != FloatingMenuState.CLOSING
                        && selector.test(flow.state())) {
                    flow.update(update);
                }
            }
        });
    }

    /** Closes every open or suspended flow whose state matches {@code selector}. */
    public void closeWhere(Predicate<? super S> selector) {
        Objects.requireNonNull(selector, "selector");
        if (flows.isEmpty()) return;
        FloatingMenus.execute(() -> {
            for (Map.Entry<UUID, FloatingMenuFlow<S>> entry :
                    java.util.List.copyOf(flows.entrySet())) {
                FloatingMenuFlow<S> flow = entry.getValue();
                FloatingMenuState lifecycle = flow.handle().state();
                if (lifecycle == FloatingMenuState.CLOSED) {
                    flows.remove(entry.getKey(), flow);
                } else if (lifecycle != FloatingMenuState.CLOSING
                        && selector.test(flow.state())) {
                    flow.close();
                }
            }
        });
    }

    /** Removes retained state, intended for player-quit or module-disable cleanup. */
    public void forget(Player player) {
        forget(player.getUniqueId());
    }

    public void forget(UUID playerId) {
        FloatingMenuFlow<S> flow = flows.remove(playerId);
        if (flow != null) flow.close();
        states.remove(playerId);
    }
}
