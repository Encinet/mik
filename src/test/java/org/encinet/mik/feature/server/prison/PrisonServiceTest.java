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
import java.util.concurrent.CompletableFuture;

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
        when(player.getWidth()).thenReturn(0.6);
        when(player.getHeight()).thenReturn(1.8);
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

    @Test
    void rejectsBodyOverlapAndExternalTeleportEvenWhenFeetStayInsideTheRoom() {
        when(plots.regions()).thenReturn(List.of(room(0)));
        try (var service = service()) {
            service.worldLoaded(world);
            var inside = new Location(world, 3.5, 65, 0.5);
            assertTrue(service.canMoveTo(player, inside));
            assertFalse(service.canMoveTo(player, new Location(world, 3.9, 65, 0.5)));
            assertFalse(service.canMoveTo(player, new Location(world, 3.5, 66.5, 0.5)));
            assertFalse(service.canTeleportTo(player, inside));
            when(bans.active(ACCOUNT, null)).thenReturn(Optional.empty());
            assertTrue(service.canTeleportTo(player, inside));
        }
    }

    @Test
    void allowsOnlyTheCurrentRelocationTargetAndConsumesThePermit() {
        when(plots.regions()).thenReturn(List.of(room(8)));
        var old = new Location(world, 0.5, 65, 0.5);
        var target = new Location(world, 8.5, 65, 0.5);
        when(player.getLocation()).thenReturn(old);
        try (var landing = mockStatic(PrisonLanding.class); var service = spy(service())) {
            service.worldLoaded(world);
            landing.when(() -> PrisonLanding.valid(any(), eq(target))).thenReturn(true);
            doReturn(CompletableFuture.completedFuture(target)).when(service).prepare(any());
            when(player.teleport(eq(target))).thenAnswer(call -> {
                var destination = call.<Location>getArgument(0);
                assertFalse(service.canTeleportTo(player, destination.clone().add(1, 0, 0)));
                assertTrue(service.canTeleportTo(player, destination));
                assertFalse(service.canTeleportTo(player, destination));
                when(player.getLocation()).thenReturn(destination);
                return true;
            });

            assertNotNull(service.update(player, false));
            verify(player).teleport(target);
            assertFalse(service.canTeleportTo(player, target));
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
