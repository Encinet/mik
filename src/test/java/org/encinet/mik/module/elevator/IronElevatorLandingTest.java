package org.encinet.mik.module.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IronElevatorLandingTest {

    @Test
    void selectsNearestSafeFloorInBothDirectionsAndKeepsView() {
        Column column = new Column();
        column.iron(0, 10, 0);
        column.iron(0, 30, 0);
        column.iron(0, 50, 0);
        Location origin = new Location(column.world, 0.4, 31, 0.6, 75, -20);

        Location up = column.find(30, 1, origin);
        Location down = column.find(30, -1, origin);

        assertEquals(51, up.getY());
        assertEquals(11, down.getY());
        assertEquals(origin.getX(), up.getX());
        assertEquals(origin.getZ(), up.getZ());
        assertEquals(origin.getYaw(), up.getYaw());
        assertEquals(origin.getPitch(), up.getPitch());
    }

    @Test
    void allowsRailsWithEmptyCollisionShape() {
        Column column = floors();
        column.put(0, 21, 0, Material.RAIL, List.of());

        assertEquals(21, column.find(10, 1, column.origin()).getY());
    }

    @Test
    void respectsActualChainAndFenceShapesWithoutMovingThePlayerSideways() {
        for (Material material : List.of(Material.IRON_CHAIN, Material.OAK_FENCE)) {
            Column column = floors();
            column.put(0, 21, 0, material,
                    List.of(new BoundingBox(0.375, 0, 0.375, 0.625, 1.5, 0.625)));

            assertNull(column.find(10, 1, column.origin()), material.toString());
            Location origin = new Location(column.world, 0.05, 11, 0.05, 25, -15);
            Location landing = column.find(10, 1, origin);

            assertNotNull(landing, material.toString());
            assertEquals(21, landing.getY());
            assertEquals(origin.getX(), landing.getX());
            assertEquals(origin.getZ(), landing.getZ());
            assertEquals(origin.getYaw(), landing.getYaw());
            assertEquals(origin.getPitch(), landing.getPitch());
        }
    }

    @Test
    void allowsThinObstacleAwayFromBodyWithoutMovingPlayer() {
        Column column = floors();
        column.put(0, 21, 0, Material.OAK_TRAPDOOR,
                List.of(new BoundingBox(0, 0, 0, 1, 1, 0.1875)));

        Location landing = column.find(10, 1, column.origin());

        assertEquals(0.5, landing.getX());
        assertEquals(0.5, landing.getZ());
    }

    @Test
    void skipsFloorWithBlockedHeadroomEvenWhenSneaking() {
        Column column = floors();
        column.put(0, 22, 0, Material.STONE, List.of(fullBlock()));
        column.iron(0, 30, 0);

        assertEquals(31, column.find(10, 1, column.origin()).getY());
    }

    @Test
    void rejectsFluidAndFireEvenWithoutCollisionShape() {
        for (Material hazard : List.of(Material.WATER, Material.LAVA, Material.FIRE)) {
            Column column = floors();
            column.put(0, 21, 0, hazard, List.of());

            assertNull(column.find(10, 1, column.origin()), hazard.toString());
        }
    }

    @Test
    void requiresSameColumnAndHonorsWorldHeightLimits() {
        Column column = new Column();
        column.iron(0, -64, 0);
        column.iron(0, 382, 0);
        column.iron(1, -50, 0);

        assertNull(column.find(-64, -1, column.origin()));
        assertNull(column.find(-64, 1, column.origin()));
        assertNull(column.find(382, 1, column.origin()));
    }

    @Test
    void detectsStandingPlatformButNotAirbornePlayers() {
        Column column = floors();

        assertEquals(10, IronElevatorLanding.platformAt(column.origin()).getY());
        assertNull(IronElevatorLanding.platformAt(column.origin().add(0, 0.4, 0)));
        assertNull(IronElevatorLanding.platformAt(column.origin().add(1, 0, 0)));
    }

    @Test
    void skipsLandingsRejectedByEntityOrWorldBorderCollision() {
        Column column = floors();

        assertNull(IronElevatorLanding.find(column.block(0, 10, 0), 1, column.origin(),
                IronElevatorLanding.DEFAULT_SIZE, body -> true));
    }

    private static Column floors() {
        Column column = new Column();
        column.iron(0, 10, 0);
        column.iron(0, 20, 0);
        return column;
    }

    private static BoundingBox fullBlock() {
        return new BoundingBox(0, 0, 0, 1, 1, 1);
    }

    private record Position(int blockX, int blockY, int blockZ) {
    }

    private record Cell(Material material, List<BoundingBox> boxes) {
    }

    private static final class Column {
        private final Map<Position, Cell> cells = new HashMap<>();
        private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 384;
                    case "isChunkLoaded" -> true;
                    case "getBlockAt" -> block((int) arguments[0], (int) arguments[1], (int) arguments[2]);
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        void iron(int blockX, int blockY, int blockZ) {
            put(blockX, blockY, blockZ, Material.IRON_BLOCK, List.of(fullBlock()));
        }

        void put(int blockX, int blockY, int blockZ, Material material, List<BoundingBox> boxes) {
            cells.put(new Position(blockX, blockY, blockZ), new Cell(material, boxes));
        }

        Location origin() {
            return new Location(world, 0.5, 11, 0.5);
        }

        Location find(int sourceY, int direction, Location origin) {
            return IronElevatorLanding.find(block(0, sourceY, 0), direction, origin,
                    IronElevatorLanding.DEFAULT_SIZE, body -> false);
        }

        Block block(int blockX, int blockY, int blockZ) {
            Cell cell = cells.getOrDefault(new Position(blockX, blockY, blockZ),
                    new Cell(Material.AIR, List.of()));
            VoxelShape shape = new VoxelShape() {
                @Override
                public Collection<BoundingBox> getBoundingBoxes() {
                    return cell.boxes();
                }

                @Override
                public boolean overlaps(BoundingBox other) {
                    return cell.boxes().stream().anyMatch(box -> box.overlaps(other));
                }
            };
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getX" -> blockX;
                        case "getY" -> blockY;
                        case "getZ" -> blockZ;
                        case "getWorld" -> world;
                        case "getType" -> cell.material();
                        case "getCollisionShape" -> shape;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
