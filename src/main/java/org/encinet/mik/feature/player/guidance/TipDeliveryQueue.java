package org.encinet.mik.feature.player.guidance;

import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Coalesces accepted offers into one next-main-thread delivery task for all affected players. */
final class TipDeliveryQueue implements AutoCloseable {
    private final Function<Runnable, Runnable> scheduleOnMain;
    private final Consumer<Player> deliver;
    private final Consumer<RuntimeException> errors;
    private final Map<UUID, Player> waiting = new LinkedHashMap<>();
    private Runnable cancelTask;
    private boolean closed;

    TipDeliveryQueue(Function<Runnable, Runnable> scheduleOnMain, Consumer<Player> deliver,
                     Consumer<RuntimeException> errors) {
        this.scheduleOnMain = scheduleOnMain;
        this.deliver = deliver;
        this.errors = errors;
    }

    void request(Player player) {
        if (closed) return;
        waiting.put(player.getUniqueId(), player);
        if (cancelTask != null) return;
        try {
            cancelTask = scheduleOnMain.apply(this::flush);
        } catch (RuntimeException error) {
            waiting.clear();
            errors.accept(error);
        }
    }

    void forget(Player player) {
        UUID id = player.getUniqueId();
        if (waiting.get(id) == player) waiting.remove(id);
        if (waiting.isEmpty() && cancelTask != null) {
            cancelTask.run();
            cancelTask = null;
        }
    }

    boolean pending(Player player) {
        return waiting.get(player.getUniqueId()) == player;
    }

    private void flush() {
        cancelTask = null;
        List<Player> players = List.copyOf(waiting.values());
        waiting.clear();
        for (Player player : players) {
            if (closed) return;
            try { deliver.accept(player); }
            catch (RuntimeException error) { errors.accept(error); }
        }
    }

    @Override
    public void close() {
        closed = true;
        if (cancelTask != null) cancelTask.run();
        cancelTask = null;
        waiting.clear();
    }
}
