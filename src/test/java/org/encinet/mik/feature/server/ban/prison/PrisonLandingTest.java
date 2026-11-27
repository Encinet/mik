package org.encinet.mik.feature.server.ban.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrisonLandingTest {
    private final World world = mock(World.class);
    private final WorldBorder border = mock(WorldBorder.class);
    private final Block floor = mock(Block.class);
    private final Block air = mock(Block.class);

    PrisonLandingTest() {
        var support = mock(BlockData.class);
        when(support.isFaceSturdy(BlockFace.UP, BlockSupport.FULL)).thenReturn(true);
        when(floor.getBlockData()).thenReturn(support);
        when(floor.getType()).thenReturn(Material.STONE);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        var noSupport = mock(BlockData.class);
        when(air.getBlockData()).thenReturn(noSupport);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getUID()).thenReturn(new UUID(1, 2));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                call.<Integer>getArgument(1) == 64 ? floor : air);
    }

    @Test
    void findsAnotherStandingPositionWhenThePreferredColumnIsBlocked() {
        when(world.getBlockAt(2, 65, 2)).thenReturn(floor);
        var sector = new PrisonCell.Sector(0, 0, 65, 66);
        var point = PrisonLanding.findInSector(world, sector);
        assertNotNull(point);
        assertNotEquals(new Location(world, 2.5, 65, 2.5), point);
        assertTrue(PrisonLanding.valid(room(), point));
    }

    @Test
    void rejectsHazardousOrMissingFloorsAndPointsOutsideTheWorldBorder() {
        var point = new Location(world, 2.5, 65, 2.5);
        assertTrue(PrisonLanding.valid(room(), point));
        when(floor.getType()).thenReturn(Material.MAGMA_BLOCK);
        assertFalse(PrisonLanding.valid(room(), point));
        when(world.getBlockAt(2, 64, 2)).thenReturn(air);
        assertFalse(PrisonLanding.valid(room(), point));
        when(world.getBlockAt(2, 64, 2)).thenReturn(floor);
        when(floor.getType()).thenReturn(Material.STONE);
        when(border.isInside(any(Location.class))).thenReturn(false);
        assertFalse(PrisonLanding.valid(room(), point));
    }

    @Test
    void neverReadsBlocksInAnUnloadedChunk() {
        assertNull(PrisonLanding.findInSector(world, new PrisonCell.Sector(16, 0, 65, 66)));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    private Plot room() {
        return new Plot(new UUID(2, 0), world.getUID(), new UUID(2, 3), "Room",
                0, Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of(), null);
    }
}
