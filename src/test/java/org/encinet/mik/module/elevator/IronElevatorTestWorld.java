package org.encinet.mik.module.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.bukkit.util.RayTraceResult;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class IronElevatorTestWorld {

    record Position(int blockX, int blockY, int blockZ) {
    }

    private record Cell(Material material, List<BoundingBox> shape, Set<BlockFace> sturdyFaces) {
    }

    private static final Cell AIR = new Cell(Material.AIR, List.of(), Set.of());
    final UUID worldId = UUID.randomUUID();
    final Map<Position, Integer> terrain = new HashMap<>();
    final Set<String> unloadedChunks = new HashSet<>();
    private final Map<Position, Cell> cells = new HashMap<>();
    World.Environment environment = World.Environment.NORMAL;
    int terrainHeight = 64;
    int blockReads;
    int terrainReads;
    RayTraceResult obstruction;
    final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getUID" -> worldId;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 384;
                case "getEnvironment" -> environment;
                case "isChunkLoaded" -> !unloadedChunks.contains(arguments[0] + "," + arguments[1]);
                case "getHighestBlockYAt" -> {
                    terrainReads++;
                    yield terrain.getOrDefault(new Position((int) arguments[0], 0, (int) arguments[1]), terrainHeight);
                }
                case "getBlockAt" -> {
                    blockReads++;
                    if (unloadedChunks.contains(((int) arguments[0] >> 4) + "," + ((int) arguments[2] >> 4)))
                        throw new AssertionError("Read from an unloaded chunk");
                    yield block((int) arguments[0], (int) arguments[1], (int) arguments[2]);
                }
                case "rayTraceBlocks" -> obstruction;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });

    void iron(int height) {
        put(0, height, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
    }

    void put(int blockX, int blockY, int blockZ, Material material, Set<BlockFace> sturdyFaces) {
        List<BoundingBox> shape = material == Material.AIR ? List.of()
                : List.of(new BoundingBox(0, 0, 0, 1, 1, 1));
        cells.put(new Position(blockX, blockY, blockZ), new Cell(material, shape, sturdyFaces));
    }

    void wall(int sourceY, BlockFace direction) {
        wall(sourceY, direction, 1);
    }

    void wall(int sourceY, BlockFace direction, int offset) {
        for (int side = -1; side <= 1; side++) {
            for (int above = 1; above <= 2; above++) {
                put(direction.getModX() * offset + direction.getModZ() * side, sourceY + above,
                        direction.getModZ() * offset - direction.getModX() * side, Material.STONE,
                        Set.of(direction.getOppositeFace()));
            }
        }
    }

    Location origin(int height) {
        return new Location(world, 0.5, height + 1, 0.5);
    }

    Block block(int blockX, int blockY, int blockZ) {
        Position position = new Position(blockX, blockY, blockZ);
        VoxelShape shape = new VoxelShape() {
            @Override
            public Collection<BoundingBox> getBoundingBoxes() {
                return cell(position).shape();
            }

            @Override
            public boolean overlaps(BoundingBox other) {
                return getBoundingBoxes().stream().anyMatch(box -> box.overlaps(other));
            }
        };
        BlockData data = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "isFaceSturdy" -> cell(position).sturdyFaces().contains(arguments[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getX" -> blockX;
                    case "getY" -> blockY;
                    case "getZ" -> blockZ;
                    case "getLocation" -> new Location(world, blockX, blockY, blockZ);
                    case "getWorld" -> world;
                    case "getType" -> cell(position).material();
                    case "getBlockData" -> data;
                    case "getCollisionShape" -> shape;
                    case "equals" -> arguments[0] instanceof Block other && world.equals(other.getWorld())
                            && blockX == other.getX() && blockY == other.getY() && blockZ == other.getZ();
                    case "hashCode" -> position.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Cell cell(Position position) {
        Cell stored = cells.get(position);
        if (stored != null) return stored;
        int height = terrain.getOrDefault(new Position(position.blockX(), 0, position.blockZ()), terrainHeight);
        return position.blockY() == height
                ? new Cell(Material.STONE, List.of(new BoundingBox(0, 0, 0, 1, 1, 1)), Set.of(BlockFace.UP)) : AIR;
    }
}
