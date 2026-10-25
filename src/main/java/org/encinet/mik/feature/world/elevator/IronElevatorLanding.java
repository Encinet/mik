package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.Comparator;
import java.util.function.Predicate;

final class IronElevatorLanding {

    private static final double EPSILON = 1.0E-6;
    private static final double[] EDGE_OFFSETS = {0.05, 0.5, 0.95};
    static final Size DEFAULT_SIZE = new Size(0.6, 1.8, 0.6);

    static Size size(Player player) {
        BoundingBox body = player.getBoundingBox();
        return new Size(body.getWidthX(), Math.max(body.getHeight(), body.getWidthX() * 3), body.getWidthZ());
    }

    record Size(double widthX, double height, double widthZ) {
        BoundingBox at(Location location) {
            return new BoundingBox(
                    location.getX() - widthX / 2, location.getY() + EPSILON,
                    location.getZ() - widthZ / 2,
                    location.getX() + widthX / 2, location.getY() + height,
                    location.getZ() + widthZ / 2);
        }
    }

    static Block platformAt(Location feet) {
        World world = feet.getWorld();
        if (world == null || !world.isChunkLoaded(feet.getBlockX() >> 4, feet.getBlockZ() >> 4)) {
            return null;
        }
        int platformY = (int) Math.floor(feet.getY() - EPSILON);
        if (platformY < world.getMinHeight() || platformY >= world.getMaxHeight()
                || Math.abs(feet.getY() - platformY - 1) > 0.1) {
            return null;
        }
        Block block = world.getBlockAt(feet.getBlockX(), platformY, feet.getBlockZ());
        return block.getType() == Material.IRON_BLOCK ? block : null;
    }

    static Location find(Block source, int direction, Location origin, Size size,
                         Predicate<BoundingBox> extraCollision) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("Direction must be -1 or 1");
        }
        var platforms = IronElevatorShaft.scan(source).platforms().stream()
                .filter(platform -> direction * (platform.height() - source.getY()) > 0)
                .sorted(Comparator.comparingInt(platform -> direction * platform.height())).toList();
        for (IronElevatorPlatform platform : platforms) {
            Location landing = on(platform, origin, size, extraCollision);
            if (landing != null) {
                return landing;
            }
        }
        return null;
    }

    static Location on(Block platform, Location origin, Size size,
                       Predicate<BoundingBox> extraCollision) {
        IronElevatorPlatform group = IronElevatorPlatform.resolve(platform);
        return group == null ? null : on(group, origin, size, extraCollision);
    }

    static Location on(IronElevatorPlatform platform, Location origin, Size size,
                       Predicate<BoundingBox> extraCollision) {
        if (!platform.anchor().getWorld().equals(origin.getWorld())) return null;
        Location landing = origin.clone();
        landing.setY(platform.height() + 1);
        return supported(platform, landing) && isSafe(landing, size, extraCollision) ? landing : null;
    }

    static boolean usable(IronElevatorPlatform platform) {
        Usability search = new Usability(platform);
        while (!search.step()) { }
        return search.usable;
    }

    static final class Usability {
        private final IronElevatorPlatform platform;
        private final Location probe;
        private int block;
        private int offset = -1;
        private boolean done;
        private boolean usable;

        Usability(IronElevatorPlatform platform) {
            this.platform = platform;
            probe = new Location(platform.anchor().getWorld(), 0, platform.height() + 1, 0);
        }

        boolean usable() { return usable; }

        boolean step() {
            if (done) return true;
            if (block == platform.blocks().size()) return done = true;
            Block current = platform.blocks().get(block);
            probe.setX(current.getX() + (offset < 0 ? 0.5 : EDGE_OFFSETS[offset / 3]));
            probe.setZ(current.getZ() + (offset < 0 ? 0.5 : EDGE_OFFSETS[offset % 3]));
            if (offset < 0 && !supported(platform, probe)) {
                block++;
                return false;
            }
            if (isSafe(probe, DEFAULT_SIZE, body -> false)) {
                usable = true;
                return done = true;
            }
            if (++offset == 9) {
                block++;
                offset = -1;
            }
            return false;
        }
    }

    private static boolean supported(IronElevatorPlatform platform, Location feet) {
        World world = feet.getWorld();
        if (!world.isChunkLoaded(feet.getBlockX() >> 4, feet.getBlockZ() >> 4)) return false;
        Block support = world.getBlockAt(feet.getBlockX(), platform.height(), feet.getBlockZ());
        return support.getType() == Material.IRON_BLOCK && platform.contains(support);
    }

    static boolean isSafe(Location feet, Size size, Predicate<BoundingBox> extraCollision) {
        World world = feet.getWorld();
        BoundingBox body = size.at(feet);
        if (body.getMaxY() > world.getMaxHeight()) {
            return false;
        }
        for (int blockX = (int) Math.floor(body.getMinX()); blockX < body.getMaxX(); blockX++) {
            for (int blockZ = (int) Math.floor(body.getMinZ()); blockZ < body.getMaxZ(); blockZ++) {
                if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                    return false;
                }
                for (int blockY = Math.max(world.getMinHeight(), feet.getBlockY() - 1);
                     blockY < body.getMaxY(); blockY++) {
                    Block block = world.getBlockAt(blockX, blockY, blockZ);
                    BoundingBox localBody = body.clone().shift(-blockX, -blockY, -blockZ);
                    if (block.getCollisionShape().overlaps(localBody)
                            || (isHazard(block.getType())
                            && localBody.overlaps(new BoundingBox(0, 0, 0, 1, 1, 1)))) {
                        return false;
                    }
                }
            }
        }
        return !extraCollision.test(body);
    }

    private static boolean isHazard(Material material) {
        return switch (material) {
            case WATER, LAVA, FIRE, SOUL_FIRE, POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE -> true;
            default -> false;
        };
    }
}
