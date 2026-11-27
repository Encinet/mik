package org.encinet.mik.feature.server.ban.prison;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrisonAdmissionTest {
    private final UUID account = new UUID(1, 1);
    private final UUID worldId = new UUID(1, 2);
    private final World world = mock(World.class);
    private final Location original = new Location(world, 200.5, 64, 200.5);
    private final Location entrance = new Location(world, .5, 65, .5);
    private final PrisonService service = mock(PrisonService.class);

    @Test
    void requiresTheSameConnectionAndRechecksTheAssignedRoomAndLanding() {
        var connection = connection();
        var inmate = inmate(0);
        when(service.assignment(account)).thenReturn(inmate);
        when(service.prepare(inmate)).thenReturn(CompletableFuture.completedFuture(entrance));

        try (var admission = new PrisonAdmission(service); var landing = mockStatic(PrisonLanding.class)) {
            landing.when(() -> PrisonLanding.valid(inmate.cell(), entrance)).thenReturn(true);
            assertTrue(admission.canConfigure(account));
            assertFalse(admission.canEnter(account, connection));
            var event = new AsyncPlayerSpawnLocationEvent(connection, original, false);
            admission.onSpawn(event);
            assertEquals(entrance, event.getSpawnLocation());
            assertFalse(admission.canEnter(account, connection()));
            admission.verifySpawn(event);
            assertTrue(admission.canEnter(account, connection));
            assertFalse(admission.canEnter(account, connection), "A prepared landing is consumed once");

            admission.onSpawn(event);
            event.setSpawnLocation(original);
            admission.verifySpawn(event);
            assertFalse(admission.canEnter(account, connection), "A different final spawn cannot admit the account");

            admission.onSpawn(event);
            when(service.assignment(account)).thenReturn(inmate(4));
            assertFalse(admission.canEnter(account, connection), "Edited geometry invalidates a prepared landing");

            when(service.assignment(account)).thenReturn(inmate);
            admission.onSpawn(event);
            landing.when(() -> PrisonLanding.valid(inmate.cell(), entrance)).thenReturn(false);
            assertFalse(admission.canEnter(account, connection), "A blocked landing cannot admit the account");
        }
    }

    @Test
    void leavesSpawnUntouchedAndRefusesEntryIfThereIsNoSafeLanding() {
        var connection = connection();
        var inmate = inmate(0);
        when(service.assignment(account)).thenReturn(inmate);
        when(service.prepare(inmate)).thenReturn(CompletableFuture.completedFuture(null));
        var event = new AsyncPlayerSpawnLocationEvent(connection, original, false);
        try (var admission = new PrisonAdmission(service)) {
            admission.onSpawn(event);
            assertEquals(original, event.getSpawnLocation());
            assertFalse(admission.canEnter(account, connection));
        }
    }

    private PlayerConfigurationConnection connection() {
        var connection = mock(PlayerConfigurationConnection.class);
        var profile = mock(PlayerProfile.class);
        when(connection.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(account);
        return connection;
    }

    private PrisonAssignment inmate(int x) {
        var plot = new Plot(new UUID(2, 1), worldId, new UUID(2, 2), "Room",
                0, Set.of(Cell.at(x, 64, 0)), Map.of(), Map.of(), null);
        var ban = new BanRecord(1, account, "Jailed", "jailed", "reason", "Admin",
                Instant.EPOCH, Instant.EPOCH, null, null, BanRecord.Origin.MIK);
        return new PrisonAssignment(ban, plot, plot);
    }
}
