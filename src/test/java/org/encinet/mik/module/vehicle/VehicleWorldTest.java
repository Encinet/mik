package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VehicleWorldTest {
    @Test void respectsLocalVoxelShapeAndStopsHighSpeedMotion() {
        TestWorld test = new TestWorld();
        test.solid(0, 10, 0, new BoundingBox(0, 0, 0, 0.125, 1, 1));
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(-3, 10, 0));
        body.velocity = new VehicleVector(40, 0, 0);
        new VehicleWorld(test.world, List.of()).move(body,
                new VehiclePhysics.Motion(new VehicleVector(10, 0, 0), new Quaterniond()));
        assertTrue(body.origin().coordinateX() < -1 && body.origin().coordinateX() > -1.1, "origin=" + body.origin());
        assertEquals(0, body.velocity.coordinateX(), 1.0E-9);
        assertTrue(body.blocked);
        assertTrue(body.health < 100);
    }

    @Test void unloadedChunksAreSolidWithoutReadingTheirBlocks() {
        TestWorld test = new TestWorld();
        test.maximumChunkX = 0;
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(13, 10, 0));
        body.velocity = new VehicleVector(20, 0, 0);
        new VehicleWorld(test.world, List.of()).move(body,
                new VehiclePhysics.Motion(new VehicleVector(10, 0, 0), new Quaterniond()));
        assertTrue(body.origin().coordinateX() < 15, "origin=" + body.origin());
        assertEquals(0, test.unloadedReads);
    }

    @Test void wallContactSlidesRatherThanRemovingTangentialTravel() {
        TestWorld test = new TestWorld();
        for (int blockZ = -5; blockZ <= 5; blockZ++) test.solid(0, 10, blockZ, new BoundingBox(0, 0, 0, 1, 1, 1));
        VehicleBody body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(-3, 10, 0));
        body.velocity = new VehicleVector(20, 0, 8);
        new VehicleWorld(test.world, List.of()).move(body,
                new VehiclePhysics.Motion(new VehicleVector(10, 0, 2), new Quaterniond()));
        assertTrue(body.origin().coordinateX() < -1);
        assertTrue(body.origin().coordinateZ() > 1.9);
        assertEquals(8, body.velocity.coordinateZ(), 1.0E-9);
    }

    @Test void otherVehicleCollisionAndSpawnClearanceUseSameSolver() {
        TestWorld test = new TestWorld();
        var parked = VehicleCollision.Box.axisAligned(new VehicleVector(0, 10, -2), new VehicleVector(2, 11, 2));
        VehicleWorld environment = new VehicleWorld(test.world, List.of(parked));
        var overlapping = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(1, 10, 0));
        assertFalse(environment.clear(overlapping));
        var clear = VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(-5, 10, 0));
        assertTrue(environment.clear(clear));
    }

    @Test void waterColumnProducesDepthAndDryBlocksDoNot() {
        TestWorld test = new TestWorld();
        test.water(0, 10, 0);
        test.water(0, 11, 0);
        VehicleWorld environment = new VehicleWorld(test.world, List.of());
        assertTrue(environment.waterDepth(new VehicleVector(0.5, 10.2, 0.5)) > 1.5);
        assertEquals(0, environment.waterDepth(new VehicleVector(1.5, 10.2, 0.5)));
    }

    private static final class TestWorld {
        private record Position(int coordinateX, int coordinateY, int coordinateZ) { }
        private record Cell(Material material, BoundingBox shape) { }
        final Map<Position, Cell> cells = new HashMap<>();
        final World world;
        int maximumChunkX = Integer.MAX_VALUE;
        int unloadedReads;

        TestWorld() {
            WorldBorder border = proxy(WorldBorder.class, (name, arguments) -> switch (name) {
                case "getSize" -> 60000000.0;
                case "getCenter" -> new Location(null, 0, 0, 0);
                default -> null;
            });
            world = proxy(World.class, (name, arguments) -> switch (name) {
                case "isChunkLoaded" -> (int) arguments[0] <= maximumChunkX;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "getWorldBorder" -> border;
                case "getBlockAt" -> {
                    int blockX = (int) arguments[0];
                    if ((blockX >> 4) > maximumChunkX) unloadedReads++;
                    yield block(new Position(blockX, (int) arguments[1], (int) arguments[2]));
                }
                default -> null;
            });
        }
        void solid(int blockX, int blockY, int blockZ, BoundingBox shape) {
            cells.put(new Position(blockX, blockY, blockZ), new Cell(Material.STONE, shape));
        }
        void water(int blockX, int blockY, int blockZ) {
            cells.put(new Position(blockX, blockY, blockZ), new Cell(Material.WATER, null));
        }
        private Block block(Position position) {
            Cell cell = cells.getOrDefault(position, new Cell(Material.AIR, null));
            VoxelShape shape = proxy(VoxelShape.class, (name, arguments) -> name.equals("getBoundingBoxes")
                    ? cell.shape() == null ? List.of() : List.of(cell.shape()) : null);
            BlockData data = cell.material() == Material.WATER ? proxy(Levelled.class, (name, arguments) -> name.equals("getLevel") ? 0 : null)
                    : proxy(BlockData.class, (name, arguments) -> null);
            return proxy(Block.class, (name, arguments) -> switch (name) {
                case "getType" -> cell.material();
                case "getCollisionShape" -> shape;
                case "getBlockData" -> data;
                default -> null;
            });
        }
        private interface Method { Object invoke(String name, Object[] arguments); }
        private static <Value> Value proxy(Class<Value> type, Method method) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (object, called, arguments) -> method.invoke(called.getName(), arguments)));
        }
    }
}
