package org.encinet.mik.module.elevator;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

record IronElevatorWall(Block platform, BlockFace direction, int offset, IronElevatorPlatform footprint) {

    private static final List<BlockFace> DIRECTIONS = List.of(
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    IronElevatorWall(Block platform, BlockFace direction) {
        this(platform, direction, 1);
    }

    IronElevatorWall(Block platform, BlockFace direction, int offset) {
        this(platform, direction, offset, new IronElevatorPlatform(List.of(platform)));
    }

    IronElevatorWall {
        if (offset < 1 || offset > 4) throw new IllegalArgumentException("Wall offset must be within four blocks");
        if (footprint == null || !footprint.contains(platform))
            throw new IllegalArgumentException("Wall anchor must belong to its platform");
    }

    static IronElevatorWall find(Block source) {
        IronElevatorPlatform platform = IronElevatorPlatform.resolve(source);
        return platform == null ? null : find(platform);
    }

    static IronElevatorWall find(IronElevatorPlatform platform) {
        Mount mount = findMount(platform);
        return mount == null ? null : mount.wall();
    }

    record Mount(IronElevatorWall wall, Surface surface) {
        IronElevatorPanelLayout layout(int levels) {
            return surface.layout(levels, wall.preferredSideways());
        }
    }

    static Mount findMount(IronElevatorPlatform platform) {
        double centerX = platform.centerX();
        double centerZ = platform.centerZ();
        for (int offset = 1; offset <= 4; offset++) {
            List<IronElevatorWall> candidates = new ArrayList<>();
            for (Block source : platform.blocks()) {
                for (BlockFace direction : DIRECTIONS) {
                    int neighborX = source.getX() + direction.getModX();
                    int neighborZ = source.getZ() + direction.getModZ();
                    if (!source.getWorld().isChunkLoaded(neighborX >> 4, neighborZ >> 4)) continue;
                    Block neighbor = source.getWorld().getBlockAt(neighborX, source.getY(), neighborZ);
                    if (platform.contains(neighbor)) continue;
                    candidates.add(new IronElevatorWall(source, direction, offset, platform));
                }
            }
            candidates.sort(Comparator.comparingDouble(wall -> wall.centerDistance(centerX, centerZ)));
            for (IronElevatorWall wall : candidates) {
                Surface surface = wall.surface();
                if (surface != null) return new Mount(wall, surface);
            }
        }
        return null;
    }

    private double centerDistance(double centerX, double centerZ) {
        Location center = position(0, 0);
        double deltaX = center.getX() - centerX;
        double deltaZ = center.getZ() - centerZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    double preferredSideways() {
        return (footprint.centerX() - platform.getX() - 0.5) * -direction.getModZ()
                + (footprint.centerZ() - platform.getZ() - 0.5) * direction.getModX();
    }

    boolean isFlat() {
        return surface() != null;
    }

    record Surface(int firstColumn, int lastColumn, int height) {
        int width() {
            return lastColumn - firstColumn + 1;
        }

        IronElevatorPanelLayout layout(int levels) {
            return layout(levels, (firstColumn + lastColumn) / 2.0);
        }

        IronElevatorPanelLayout layout(int levels, double preferredSideways) {
            return IronElevatorPanelLayout.fit(firstColumn - 0.5, lastColumn + 0.5, -1, height - 1,
                    levels, preferredSideways);
        }

        boolean contains(IronElevatorPanelLayout layout) {
            return layout.fits(firstColumn - 0.5, lastColumn + 0.5, -1, height - 1);
        }
    }

    Surface surface() {
        if (!touchesPlatform(0) || !available(0, 1) || !available(0, 2)) return null;
        int minimumColumn = 0;
        int maximumColumn = 0;
        while (minimumColumn > -3 && touchesPlatform(minimumColumn - 1)) minimumColumn--;
        while (maximumColumn < 3 && touchesPlatform(maximumColumn + 1)) maximumColumn++;
        boolean[][] cells = new boolean[7][3];
        for (int column = minimumColumn; column <= maximumColumn; column++) {
            for (int above = 1; above <= 3; above++)
                cells[column + 3][above - 1] = column == 0 && above <= 2 || available(column, above);
        }
        Surface best = new Surface(0, 0, 2);
        for (int height = 2; height <= 3; height++) {
            if (!columnFits(cells[3], height)) break;
            int first = 0;
            int last = 0;
            while (first > minimumColumn && columnFits(cells[first + 2], height)) first--;
            while (last < maximumColumn && columnFits(cells[last + 4], height)) last++;
            Surface candidate = new Surface(first, last, height);
            if (candidate.width() >= best.width()) best = candidate;
        }
        return best;
    }

    private boolean touchesPlatform(int sideways) {
        int blockX = platform.getX() - direction.getModZ() * sideways;
        int blockZ = platform.getZ() + direction.getModX() * sideways;
        return footprint.contains(blockX, blockZ)
                && !footprint.contains(blockX + direction.getModX(), blockZ + direction.getModZ());
    }

    private static boolean columnFits(boolean[] cells, int height) {
        for (int above = 0; above < height; above++) if (!cells[above]) return false;
        return true;
    }

    private boolean available(int sideways, int above) {
        int blockX = platform.getX() + direction.getModX() * offset - direction.getModZ() * sideways;
        int blockZ = platform.getZ() + direction.getModZ() * offset + direction.getModX() * sideways;
        int height = platform.getY() + above;
        if (height < platform.getWorld().getMinHeight() || height >= platform.getWorld().getMaxHeight()
                || !platform.getWorld().isChunkLoaded(blockX >> 4, blockZ >> 4)) return false;
        Block block = platform.getWorld().getBlockAt(blockX, height, blockZ);
        if (!block.getBlockData().isFaceSturdy(direction.getOppositeFace(), BlockSupport.FULL)) return false;
        for (int distance = 1; distance <= offset; distance++) {
            int frontX = blockX - direction.getModX() * distance;
            int frontZ = blockZ - direction.getModZ() * distance;
            if (!platform.getWorld().isChunkLoaded(frontX >> 4, frontZ >> 4)) return false;
            Block front = platform.getWorld().getBlockAt(frontX, height, frontZ);
            if (front.getCollisionShape().overlaps(new BoundingBox(0.05, 0.01, 0.05, 0.95, 0.99, 0.95)))
                return false;
        }
        return true;
    }

    Location position(double sideways, double above) {
        return platform.getLocation().add(0.5 + direction.getModX() * (offset - 0.52),
                2 + above, 0.5 + direction.getModZ() * (offset - 0.52))
                .add(right().multiply(sideways));
    }

    Vector right() {
        return new Vector(-direction.getModZ(), 0, direction.getModX());
    }

    Vector normal() {
        return new Vector(-direction.getModX(), 0, -direction.getModZ());
    }

    float yaw() {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> -90;
            default -> throw new IllegalStateException("Wall direction must be horizontal");
        };
    }

    record Hit(double sideways, double above, double distance) {
    }

    Hit hit(Location eye) {
        if (!platform.getWorld().equals(eye.getWorld())) {
            return null;
        }
        Vector normal = normal();
        Vector direction = eye.getDirection();
        double denominator = direction.dot(normal);
        Vector center = position(0, 0).toVector();
        Vector offset = eye.toVector().subtract(center);
        if (denominator >= -1.0E-6 || offset.dot(normal) <= 0) {
            return null;
        }
        double distance = -offset.dot(normal) / denominator;
        if (distance <= 0 || distance > 5) {
            return null;
        }
        Vector hit = eye.toVector().add(direction.multiply(distance)).subtract(center);
        return new Hit(hit.dot(right()), hit.getY(), distance);
    }
}
