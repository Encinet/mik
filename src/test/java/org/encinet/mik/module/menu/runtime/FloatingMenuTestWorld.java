package org.encinet.mik.module.menu.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

final class FloatingMenuTestWorld {
    private final Map<Position, List<BoundingBox>> boxes = new HashMap<>();
    private BiPredicate<Integer, Integer> loaded = (chunkX, chunkZ) -> true;
    private int blockReads;
    private int rayReads;
    private final World world = createWorld();

    void block(int blockX, int blockY, int blockZ, BoundingBox... shape) {
        boxes.put(new Position(blockX, blockY, blockZ), List.of(shape));
    }

    void loaded(BiPredicate<Integer, Integer> predicate) {
        loaded = predicate;
    }

    int blockReads() { return blockReads; }
    int rayReads() { return rayReads; }

    World world() {
        return world;
    }

    private World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isChunkLoaded" -> loaded.test((int) args[0], (int) args[1]);
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "getBlockAt" -> {
                        blockReads++;
                        yield block(boxes.getOrDefault(new Position((int) args[0], (int) args[1], (int) args[2]), List.of()));
                    }
                    case "rayTraceBlocks" -> {
                        rayReads++;
                        Location start = (Location) args[0];
                        Vector direction = (Vector) args[1];
                        double maximum = (double) args[2];
                        RayTraceResult nearest = null;
                        for (var entry : boxes.entrySet()) {
                            Position position = entry.getKey();
                            for (BoundingBox box : entry.getValue()) {
                                RayTraceResult hit = box.clone().shift(position.x(), position.y(), position.z())
                                        .rayTrace(start.toVector(), direction, maximum);
                                if (hit != null) {
                                    maximum = hit.getHitPosition().distance(start.toVector());
                                    nearest = hit;
                                }
                            }
                        }
                        yield nearest;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString", "getName" -> "menu-test";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static Block air() { return block(List.of()); }

    private static Block block(List<BoundingBox> boxes) {
        VoxelShape shape = (VoxelShape) Proxy.newProxyInstance(VoxelShape.class.getClassLoader(),
                new Class<?>[]{VoxelShape.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBoundingBoxes" -> boxes;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCollisionShape" -> shape;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private record Position(int x, int y, int z) { }
}
