package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

final class FloatingMenuTestWorld {
    private final Map<Position, List<BoundingBox>> boxes = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private BiPredicate<Integer, Integer> loaded = (_, _) -> true;
    private int blockReads;
    private int rayReads;
    private Vector forcedRayHit;
    private final World world = createWorld();

    void block(int blockX, int blockY, int blockZ, BoundingBox... shape) {
        boxes.put(new Position(blockX, blockY, blockZ), List.of(shape));
    }

    void loaded(BiPredicate<Integer, Integer> predicate) {
        loaded = predicate;
    }

    void entity(Entity entity) { entities.add(entity); }

    int blockReads() { return blockReads; }
    int rayReads() { return rayReads; }
    void rayHit(Vector hit) { forcedRayHit = hit.clone(); }

    World world() {
        return world;
    }

    private World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isChunkLoaded" -> loaded.test((int) args[0], (int) args[1]);
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "getEntitiesByClasses" -> entities.stream()
                            .filter(entity -> java.util.Arrays.stream((Class<?>[]) args[0]).anyMatch(type -> type.isInstance(entity))).toList();
                    case "getNearbyEntities" -> entities.stream()
                            .filter(entity -> entityFilter(args[4]).test(entity))
                            .filter(entity -> entity.getBoundingBox().overlaps(BoundingBox.of((Location) args[0],
                                    (double) args[1], (double) args[2], (double) args[3]))).toList();
                    case "getBlockAt" -> {
                        if (!loaded.test((int) args[0] >> 4, (int) args[2] >> 4))
                            throw new AssertionError("Menu sampling accessed an unloaded chunk");
                        blockReads++;
                        yield block(boxes.getOrDefault(new Position((int) args[0], (int) args[1], (int) args[2]), List.of()));
                    }
                    case "rayTraceBlocks" -> {
                        rayReads++;
                        if (forcedRayHit != null) yield new RayTraceResult(forcedRayHit);
                        Location start = (Location) args[0];
                        Vector direction = (Vector) args[1];
                        double maximum = (double) args[2];
                        RayTraceResult nearest = null;
                        for (var entry : boxes.entrySet()) {
                            Position position = entry.getKey();
                            for (BoundingBox box : entry.getValue()) {
                                RayTraceResult hit = ray(box.clone().shift(position.x(), position.y(), position.z()),start.toVector(),direction,maximum);
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

    @SuppressWarnings("unchecked") // World.getNearbyEntities declares this argument as Predicate<? super Entity>.
    private static java.util.function.Predicate<Entity> entityFilter(Object predicate) {
        return (java.util.function.Predicate<Entity>) predicate;
    }

    static Block air() { return block(List.of()); }

    /** Zero direction components at a box edge must not produce NaN/zero-distance fake hits. */
    private static RayTraceResult ray(BoundingBox box, Vector start, Vector direction, double maximum) {
        double near = 0, far = maximum;
        double[] at = {start.getX(),start.getY(),start.getZ()}, delta = {direction.getX(),direction.getY(),direction.getZ()};
        double[] min = {box.getMinX(),box.getMinY(),box.getMinZ()}, max = {box.getMaxX(),box.getMaxY(),box.getMaxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1e-12) {
                if (at[axis] < min[axis] || at[axis] > max[axis]) return null;
            } else {
                double a = (min[axis] - at[axis]) / delta[axis], b = (max[axis] - at[axis]) / delta[axis];
                near = Math.max(near,Math.min(a,b)); far = Math.min(far,Math.max(a,b));
                if (near > far) return null;
            }
        }
        return new RayTraceResult(start.clone().add(direction.clone().multiply(near)));
    }

    private static Block block(List<BoundingBox> boxes) {
        VoxelShape shape = (VoxelShape) Proxy.newProxyInstance(VoxelShape.class.getClassLoader(),
                new Class<?>[]{VoxelShape.class}, (_, method, _) -> switch (method.getName()) {
                    case "getBoundingBoxes" -> boxes;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (_, method, _) -> switch (method.getName()) {
                    case "getCollisionShape" -> shape;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private record Position(int x, int y, int z) { }
}
