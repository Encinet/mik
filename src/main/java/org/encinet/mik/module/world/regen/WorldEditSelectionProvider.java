package org.encinet.mik.module.world.regen;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/** Reads WorldEdit's current selection without using WorldEdit for regeneration. */
final class WorldEditSelectionProvider implements RegenSelectionProvider {

    @Override
    public RegenSelection selection(Player player) throws RegenSelectionProvider.IncompleteSelectionException {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        com.sk89q.worldedit.world.World selectionWorld = session.getSelectionWorld();
        if (selectionWorld == null) {
            throw new RegenSelectionProvider.IncompleteSelectionException();
        }

        try {
            Region region = session.getSelection(selectionWorld).clone();
            World world = BukkitAdapter.adapt(selectionWorld);
            BlockVector3 minimum = region.getMinimumPoint();
            BlockVector3 maximum = region.getMaximumPoint();
            int originChunkX = world.equals(player.getWorld())
                    ? player.getLocation().getBlockX() >> 4
                    : region.getCenter().toBlockPoint().x() >> 4;
            int originChunkZ = world.equals(player.getWorld())
                    ? player.getLocation().getBlockZ() >> 4
                    : region.getCenter().toBlockPoint().z() >> 4;
            List<RegenChunkPos> chunks = region.getChunks().stream()
                    .map(chunk -> new RegenChunkPos(chunk.x(), chunk.z()))
                    .sorted(Comparator.comparingLong(chunk -> distanceSquared(
                            chunk, originChunkX, originChunkZ)))
                    .toList();
            return new Selection(world, region, minimum, maximum, chunks,
                    world.getMinHeight(), world.getMaxHeight(), region instanceof CuboidRegion);
        } catch (IncompleteRegionException exception) {
            throw new RegenSelectionProvider.IncompleteSelectionException();
        }
    }

    private static long distanceSquared(RegenChunkPos chunk, int originX, int originZ) {
        long dx = (long) chunk.x() - originX;
        long dz = (long) chunk.z() - originZ;
        return dx * dx + dz * dz;
    }

    private record Selection(
            World world,
            Region region,
            BlockVector3 minimum,
            BlockVector3 maximum,
            List<RegenChunkPos> chunks,
            int worldMinHeight,
            int worldMaxHeight,
            boolean cuboid
    ) implements RegenSelection {

        private Selection {
            chunks = List.copyOf(chunks);
        }

        @Override
        public long volume() {
            return region.getVolume();
        }

        @Override
        public int minX() {
            return minimum.x();
        }

        @Override
        public int minY() {
            return minimum.y();
        }

        @Override
        public int minZ() {
            return minimum.z();
        }

        @Override
        public int maxX() {
            return maximum.x();
        }

        @Override
        public int maxY() {
            return maximum.y();
        }

        @Override
        public int maxZ() {
            return maximum.z();
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return region.contains(BlockVector3.at(x, y, z));
        }

        @Override
        public boolean fullyContainsBox(
                int minimumX,
                int minimumY,
                int minimumZ,
                int maximumX,
                int maximumY,
                int maximumZ
        ) {
            return cuboid
                    && minimumX >= minX() && maximumX <= maxX()
                    && minimumY >= minY() && maximumY <= maxY()
                    && minimumZ >= minZ() && maximumZ <= maxZ();
        }
    }
}
