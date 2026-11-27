package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.encinet.mik.feature.world.plot.PlotPrisons;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrisonServiceTest {
    private static final UUID ACCOUNT = new UUID(1, 1);
    private static final UUID WORLD = new UUID(1, 2);
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);
    private final BanManager bans = mock(BanManager.class);
    private final PlotPrisons plots = mock(PlotPrisons.class);

    PrisonServiceTest() {
        when(world.getUID()).thenReturn(WORLD);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(player.getUniqueId()).thenReturn(ACCOUNT);
        when(bans.active(ACCOUNT, null)).thenReturn(Optional.of(new BanRecord(1, ACCOUNT, "Jailed", "jailed",
                "reason", "Admin", Instant.EPOCH, Instant.EPOCH, null, null, BanRecord.Origin.MIK)));
    }

    @Test
    void guardPermitsMovingOnlinePrisonersAndTakingAnEmptyPrisonOffline() throws Exception {
        var before = List.of(room(0));
        var after = List.of(room(8));
        try (var bukkit = mockStatic(Bukkit.class); var service = service()) {
            service.worldLoaded(world);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            assertTrue(service.mayPublishPlots(before, after));
            assertFalse(service.mayPublishPlots(before, List.of()));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            assertTrue(service.mayPublishPlots(before, List.of()));
            verify(bans, never()).pardon(any(), any(), any(), any());
        }
    }

    @Test
    void movementImmediatelyUsesPublishedGeometryAndLiveBanState() {
        var before = List.of(room(0));
        when(plots.regions()).thenReturn(before);
        var first = new Location(world, .5, 65, .5);
        var next = new Location(world, 8.5, 65, .5);
        try (var service = service()) {
            service.worldLoaded(world);
            assertTrue(service.canMoveTo(player, first));
            assertFalse(service.canMoveTo(player, next));
            when(plots.regions()).thenReturn(List.of(room(8)));
            assertFalse(service.canMoveTo(player, first));
            assertTrue(service.canMoveTo(player, next));
            when(bans.active(ACCOUNT, null)).thenReturn(Optional.empty());
            assertTrue(service.canMoveTo(player, first));
        }
    }

    private PrisonService service() {
        return new PrisonService(mock(JavaPlugin.class), mock(LanguageService.class), bans, plots);
    }

    private static Region room(int x) {
        return new Region(new Plot(new UUID(2, x), WORLD, ACCOUNT, "Room",
                0, Set.of(Cell.at(x, 64, 0)), Map.of(), Map.of(), null), List.of());
    }
}
