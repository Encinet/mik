package org.encinet.mik.feature.player.afk;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.encinet.mik.integration.axiom.AxiomGizmoService;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AfkPassiveObservationTest {
    @Test
    void observationClearsExistingAfkWithoutRefreshingOldRewardsAndReleasesAutomaticSuppression() throws Exception {
        Fixture f = new Fixture();
        AfkPlayerSession session = new AfkPlayerSession(0, f.worldId, 0, 64, 0);
        session.enterAfk(180_000, false);
        f.sessions().put(f.id, session);
        f.states().put(f.id, new AfkState(f.id, null, AfkSource.AUTOMATIC, 180_000));
        var first = f.module.beginPassiveObservation(f.player);
        assertFalse(f.module.isAfk(f.id));
        assertFalse(f.module.isActivityEligible(f.id));
        assertTrue(f.suppressions().isSuppressed(f.id));
        assertFalse(session.activity().isSuspended());
        var second = f.module.beginPassiveObservation(f.player);
        first.close();
        first.close();
        assertTrue(f.suppressions().isSuppressed(f.id));
        second.close();
        assertFalse(f.suppressions().isSuppressed(f.id));
        assertFalse(f.module.isActivityEligible(f.id), "passive observation must not renew expired gameplay outcomes");
    }

    @Test
    void observationExcludesOtherwiseEligiblePlayersFromActivityRewards() throws Exception {
        Fixture f = new Fixture();
        f.sessions().put(f.id, new AfkPlayerSession(System.nanoTime() / 1_000_000, f.worldId, 0, 64, 0));
        assertTrue(f.module.isActivityEligible(f.id));
        var lease = f.module.beginPassiveObservation(f.player);
        assertFalse(f.module.isActivityEligible(f.id));
        lease.close();
        assertTrue(f.module.isActivityEligible(f.id));
    }

    @Test
    void repeatedAfkCommandsRemoveIdleExemptionWithoutClearingAfkOrRenewingRewards() throws Exception {
        Fixture f = new Fixture();
        long now = System.nanoTime() / 1_000_000;
        var session = new AfkPlayerSession(now - 20 * 60_000, f.worldId, 0, 64, 0);
        session.enterAfk(now - 10 * 60_000, false);
        f.sessions().put(f.id, session);
        f.states().put(f.id, new AfkState(f.id, null, AfkSource.MANUAL, now - 10 * 60_000));
        assertTrue(f.module.isIdleAfk(f.id));
        for (int index = 0; index < 3; index++) {
            f.module.onPlayerCommand(new org.bukkit.event.player.PlayerCommandPreprocessEvent(f.player, "/afk", java.util.Set.of()));
            assertFalse(f.module.isIdleAfk(f.id));
            assertTrue(f.module.isAfk(f.id));
            assertFalse(f.module.isActivityEligible(f.id));
        }
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE, session.activity().check(now));
    }

    @Test
    void manualAfkAllowsOnlyTheObservationReturnAndNormalProtectionResumesAfterClosing() throws Exception {
        Fixture f = new Fixture();
        var lease = f.module.beginPassiveObservation(f.player);
        f.states().put(f.id, new AfkState(f.id, null, AfkSource.MANUAL, 0));
        var command = f.teleport(PlayerTeleportEvent.TeleportCause.COMMAND);
        f.module.onPlayerMove(command);
        assertTrue(command.isCancelled());
        var returning = f.teleport(PlayerTeleportEvent.TeleportCause.SPECTATE);
        f.module.onPlayerMove(returning);
        assertFalse(returning.isCancelled());
        lease.close();
        var after = f.teleport(PlayerTeleportEvent.TeleportCause.SPECTATE);
        f.module.onPlayerMove(after);
        assertTrue(after.isCancelled());
        assertTrue(f.module.isAfk(f.id), "ending observation must keep the requested manual AFK state");
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        final UUID worldId = UUID.randomUUID();
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        final AfkModule module = new AfkModule(mock(org.bukkit.plugin.java.JavaPlugin.class), mock(LanguageService.class), mock(AxiomGizmoService.class));

        Fixture() {
            when(world.getUID()).thenReturn(worldId);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn("Staff");
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(player.getCurrentInput()).thenReturn(mock(org.bukkit.Input.class));
        }

        PlayerTeleportEvent teleport(PlayerTeleportEvent.TeleportCause cause) {
            return new PlayerTeleportEvent(player, new Location(world, 0, 64, 0), new Location(world, 10, 64, 0), cause);
        }

        Map<UUID, AfkState> states() throws Exception { return field("states"); }
        Map<UUID, AfkPlayerSession> sessions() throws Exception { return field("playerSessions"); }
        AfkSuppressionRegistry suppressions() throws Exception { return field("suppressions"); }

        @SuppressWarnings("unchecked") // Access fixture state without starting Bukkit scheduling or presentation.
        <T> T field(String name) throws Exception {
            var field = AfkModule.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(module);
        }
    }
}
