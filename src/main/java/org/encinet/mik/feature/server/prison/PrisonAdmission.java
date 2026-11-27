package org.encinet.mik.feature.server.prison;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.encinet.mik.feature.server.ban.BannedLoginAdmission;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

final class PrisonAdmission implements BannedLoginAdmission, Listener, AutoCloseable {
    private final PrisonService service;
    // The plan belongs to an individual connection and dies with that connection.
    private final Map<PlayerConfigurationConnection, Arrival> arrivals = Collections.synchronizedMap(new WeakHashMap<>());
    private boolean closed;

    PrisonAdmission(PrisonService service) { this.service = service; }

    @Override public boolean canConfigure(UUID account) { return service.assignment(account) != null; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(AsyncPlayerSpawnLocationEvent event) {
        var connection = event.getConnection();
        synchronized (arrivals) {
            if (closed) return;
            arrivals.remove(connection);
        }
        UUID account = connection.getProfile().getId();
        if (account == null) return;
        PrisonAssignment inmate = service.assignment(account);
        if (inmate == null) return;
        var preparation = service.prepare(inmate);
        Location point;
        try {
            point = preparation.get();
        } catch (InterruptedException error) {
            preparation.cancel(false);
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException | java.util.concurrent.CancellationException error) {
            return;
        }
        if (point == null || !inmate.sameRoom(service.assignment(account))) return;
        synchronized (arrivals) {
            if (closed) return;
            arrivals.put(connection, new Arrival(inmate, point));
            event.setSpawnLocation(point);
        }
    }

    @Override public boolean canEnter(UUID account, PlayerConfigurationConnection connection) {
        Arrival arrival = arrivals.remove(connection);
        if (arrival == null || !account.equals(arrival.inmate().playerId())) return false;
        PrisonAssignment current = service.assignment(account);
        return arrival.inmate().sameRoom(current)
                && PrisonLanding.valid(current.cell(), arrival.point());
    }

    @Override public void close() {
        synchronized (arrivals) {
            closed = true;
            arrivals.clear();
        }
    }

    private record Arrival(PrisonAssignment inmate, Location point) { }
}
