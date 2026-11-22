package org.encinet.mik.feature.world.plot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.encinet.mik.feature.world.plot.PlotPlayerContext.Access;
import org.encinet.mik.shared.role.RolePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlotPlayerContextTest {
    @Test
    void resolvesOnlyCurrentCellWithLiveMembershipAndChildOverrides(@TempDir Path directory) throws Exception {
        var world = mock(World.class);
        var player = mock(Player.class);
        var worldId = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var parent = UUID.randomUUID();
        var child = UUID.randomUUID();
        var childOwner = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(player.getUniqueId()).thenReturn(actor);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64, 1));
        try (var store = new PlotRepository(directory.resolve("plots.sqlite"))) {
            store.open();
            var registry = new PlotRegistry(store);
            registry.put(new Plot(parent, worldId, UUID.randomUUID(), "Parent", 1,
                    Set.of(Cell.at(0, 64, 0), Cell.at(-4, 64, 0)), Map.of(), Map.of("member.place", true), null));
            registry.put(new Plot(child, worldId, childOwner, "Workshop", 2,
                    Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of("member.break", true), parent));
            assertEquals(new PlotPlayerContext("Workshop", Access.NEWCOMER, false, false, false),
                    PlotPlayerContext.read(registry, player));

            when(player.hasPermission(RolePermissions.MEMBER)).thenReturn(true);
            assertEquals(new PlotPlayerContext("Workshop", Access.MEMBER, true, true, true),
                    PlotPlayerContext.read(registry, player));

            registry.put(new Plot(child, worldId, childOwner, "Workshop", 2, Set.of(Cell.at(0, 64, 0)),
                    Map.of(actor, Plot.Role.COLLABORATOR), Map.of(PlotAccessPolicy.playerKey(actor, "place"), false), parent));
            assertEquals(new PlotPlayerContext("Workshop", Access.COLLABORATOR, true, false, true),
                    PlotPlayerContext.read(registry, player));

            when(player.getLocation()).thenReturn(new Location(world, 4, 64, 1));
            assertEquals(new PlotPlayerContext("", Access.OUTSIDE, true, true, true),
                    PlotPlayerContext.read(registry, player));
        }
    }
}
