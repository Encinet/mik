package org.encinet.mik.module.elevator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorGroundTest {

    @TempDir
    Path temporary;

    @Test
    void rejectsLowOutliersAndHighRoofsInFavorOfASupportedGroundCluster() {
        List<IronElevatorGround.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 3; index++) samples.add(new IronElevatorGround.Sample(20, index % 2 == 0 ? 4 : 8));
        for (int index = 0; index < 12; index++) samples.add(new IronElevatorGround.Sample(64 + index % 3, index % 2 == 0 ? 8 : 16));
        for (int index = 0; index < 17; index++) samples.add(new IronElevatorGround.Sample(110, index % 2 == 0 ? 4 : 32));
        assertEquals(65, IronElevatorGround.reference(samples, 10));
    }

    @Test
    void requiresAgreementAcrossRadiiAndUsesMedianOnContinuouslySlopedTerrain() {
        List<IronElevatorGround.Sample> samples = new ArrayList<>();
        for (int index = 0; index < 8; index++) samples.add(new IronElevatorGround.Sample(30, 4));
        for (int index = 0; index < 12; index++) samples.add(new IronElevatorGround.Sample(64, index % 2 == 0 ? 8 : 16));
        assertEquals(64, IronElevatorGround.reference(samples, 10));
        assertEquals(62, IronElevatorGround.reference(List.of(new IronElevatorGround.Sample(50, 4),
                new IronElevatorGround.Sample(56, 8), new IronElevatorGround.Sample(62, 16),
                new IronElevatorGround.Sample(68, 32), new IronElevatorGround.Sample(74, 32)), 10));
        assertEquals(10, IronElevatorGround.reference(List.of(), 10));
        assertEquals(1, IronElevatorFloors.groundIndex(List.of(50, 62, 80), 64));
    }

    @Test
    void looksPastTreeTrunksToActualGroundRatherThanTreeTops() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int radius : List.of(4, 8, 16, 32, 64, 128)) {
            for (int offsetX : List.of(-radius, 0, radius)) {
                for (int offsetZ : List.of(-radius, 0, radius)) {
                    if (offsetX == 0 && offsetZ == 0) continue;
                    world.terrain.put(new IronElevatorTestWorld.Position(offsetX, 0, offsetZ), 80);
                    world.put(offsetX, 64, offsetZ, Material.GRASS_BLOCK, Set.of(BlockFace.UP));
                    for (int height = 65; height <= 80; height++)
                        world.put(offsetX, height, offsetZ, Material.OAK_LOG, Set.of(BlockFace.UP));
                }
            }
        }
        assertEquals(64, IronElevatorGround.estimate(world.block(0, 40, 0), 20));
        assertEquals(48, world.terrainReads);
    }

    @Test
    void doesNotUseLakesAsGroundAndFallsBackInVoidOrUnloadedAreas() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int radius : List.of(4, 8, 16, 32, 64, 128, 256, 512)) {
            for (int offsetX : List.of(-radius, 0, radius)) {
                for (int offsetZ : List.of(-radius, 0, radius)) {
                    if (offsetX == 0 && offsetZ == 0) continue;
                    world.put(offsetX, 64, offsetZ, Material.WATER, Set.of());
                }
            }
        }
        assertEquals(20, IronElevatorGround.estimate(world.block(0, 40, 0), 20));
        world.unloadedChunks.addAll(Set.of("-2,-2", "-2,0", "-2,2", "0,-2", "0,2", "2,-2", "2,0", "2,2"));
        assertDoesNotThrow(() -> IronElevatorGround.estimate(world.block(0, 40, 0), 20));
    }

    @Test
    void sharesTerrainEstimateAndNonAnchorGroundOverrideAcrossTheWholeShaft() throws Exception {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(40, 50, 64, 80)) {
            world.iron(height);
            world.put(1, height, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        }
        IronElevatorFloors floors = new IronElevatorFloors();
        floors.scan(world.block(0, 50, 0), world.origin(50));
        int terrainReads = world.terrainReads;
        world.terrainHeight = 120;
        floors.invalidate();
        assertEquals(List.of(-2, -1, 1, 2), floors.scan(world.block(1, 80, 0), world.origin(80))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(terrainReads, world.terrainReads);
        Path config = temporary.resolve("iron-elevators.yml");
        Files.writeString(config, "ground-floors:\n  '" + world.worldId + "':\n    '1,0': 50\n");
        floors.load(config);
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 80, 0), world.origin(80))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(terrainReads, world.terrainReads);
    }

    @Test
    void retriesSparseTerrainEvidenceWhenMoreChunksBecomeAvailable() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(40, 64, 80)) world.iron(height);
        AtomicLong clock = new AtomicLong();
        IronElevatorFloors floors = new IronElevatorFloors(clock::get);
        floors.platform(world.block(0, 40, 0));
        for (int chunkX = -8; chunkX <= 8; chunkX++) {
            for (int chunkZ = -8; chunkZ <= 8; chunkZ++) {
                if (chunkX != 0 || chunkZ != 0) world.unloadedChunks.add(chunkX + "," + chunkZ);
            }
        }
        world.terrainHeight = 120;
        assertEquals(List.of(1, 2, 3), floors.scan(world.block(0, 40, 0), world.origin(40))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        world.unloadedChunks.clear();
        world.terrainHeight = 64;
        clock.set(2_000_000_001L);
        assertEquals(List.of(-1, 1, 2), floors.scan(world.block(0, 40, 0), world.origin(40))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void findsGroundBelowABroadRoofWithoutMistakingNearbyBasementRoomsForGround() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.terrainHeight = 120;
        for (int height : List.of(40, 64, 80, 120)) world.iron(height);
        for (int radius : List.of(4, 8, 16, 32, 64, 128)) {
            for (int offsetX : List.of(-radius, 0, radius)) {
                for (int offsetZ : List.of(-radius, 0, radius)) {
                    if (offsetX == 0 && offsetZ == 0) continue;
                    world.put(offsetX, 64, offsetZ, Material.STONE, Set.of(BlockFace.UP));
                    world.put(offsetX, 80, offsetZ, Material.STONE, Set.of(BlockFace.UP));
                    if (radius <= 8) world.put(offsetX, 40, offsetZ, Material.STONE, Set.of(BlockFace.UP));
                }
            }
        }
        IronElevatorFloors floors = new IronElevatorFloors();
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 120, 0), world.origin(120))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 40, 0), world.origin(40))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void doesNotPermanentlyDeclareTheRooftopGroundWhenOnlyRoofSamplesAreAvailable() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.terrainHeight = 120;
        for (int height : List.of(40, 80, 120)) world.iron(height);
        var estimate = IronElevatorGround.measure(world.block(0, 40, 0), List.of(40, 80, 120));
        assertEquals(40, estimate.height());
        assertFalse(estimate.stable());
        assertEquals(List.of(1, 2, 3), new IronElevatorFloors().scan(world.block(0, 120, 0), world.origin(120))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void allowsGenuineSurfaceTerrainAboveBasementFloors() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.terrainHeight = 80;
        for (int height : List.of(40, 60, 80)) world.iron(height);
        for (int radius : List.of(4, 8, 16, 32, 64, 128)) {
            for (int offsetX : List.of(-radius, 0, radius)) {
                for (int offsetZ : List.of(-radius, 0, radius)) {
                    if (offsetX != 0 || offsetZ != 0)
                        world.put(offsetX, 80, offsetZ, Material.GRASS_BLOCK, Set.of(BlockFace.UP));
                }
            }
        }
        assertEquals(List.of(-2, -1, 1), new IronElevatorFloors().scan(world.block(0, 40, 0), world.origin(40))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void looksBeyondOneHundredTwentyEightBlocksPastABroadGardenRoof() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.terrainHeight = 200;
        for (int height : List.of(40, 64, 100, 160, 200)) world.iron(height);
        surface(world, 0, 0, List.of(4, 8, 16, 32, 64, 128), 200, Material.GRASS_BLOCK);
        surface(world, 0, 0, List.of(256, 512), 64, Material.GRASS_BLOCK);
        var floors = new IronElevatorFloors();
        assertEquals(List.of(-1, 1, 2, 3, 4), floors.scan(world.block(0, 200, 0), world.origin(200))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(List.of(-1, 1, 2, 3, 4), floors.scan(world.block(0, 64, 0), world.origin(64))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(64, world.terrainReads);
    }

    @Test
    void preservesGroundFromTheEntranceWhenTheRooftopAddsANewReferenceColumn() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(40, 64, 100, 160, 200)) world.iron(height);
        var floors = new IronElevatorFloors();
        assertEquals(List.of(-1, 1, 2, 3, 4), floors.scan(world.block(0, 64, 0), world.origin(64))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        int terrainReads = world.terrainReads;
        world.put(-1, 200, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        world.terrainHeight = 200;
        surface(world, -1, 0, List.of(4, 8, 16, 32, 64, 128, 256, 512), 200, Material.GRASS_BLOCK);
        floors.invalidate(world.block(-1, 200, 0));
        assertEquals(List.of(-1, 1, 2, 3, 4), floors.scan(world.block(-1, 200, 0), world.origin(200))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(terrainReads, world.terrainReads);
    }

    @Test
    void preservesGroundWhenThePreviousReferenceColumnIsRemoved() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(40, 64, 100, 200)) {
            world.iron(height);
            world.put(-1, height, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        }
        var floors = new IronElevatorFloors();
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 64, 0), world.origin(64))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        int terrainReads = world.terrainReads;
        for (int height : List.of(40, 64, 100, 200)) {
            world.put(-1, height, 0, Material.AIR, Set.of());
            floors.invalidate(world.block(-1, height, 0));
        }
        world.terrainHeight = 200;
        surface(world, 0, 0, List.of(4, 8, 16, 32, 64, 128, 256, 512), 200, Material.GRASS_BLOCK);
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 200, 0), world.origin(200))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(terrainReads, world.terrainReads);
    }

    @Test
    void checksLowerEntrancesEvenWhenTheRooftopUsesNaturalMaterials() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.terrainHeight = 200;
        for (int height : List.of(40, 64, 100, 200)) world.iron(height);
        surface(world, 0, 0, List.of(4, 8, 16, 32, 64, 128, 256, 512), 200, Material.GRASS_BLOCK);
        for (int radius : List.of(4, 8, 16, 32, 64, 128)) {
            for (int offsetX : List.of(-radius, 0, radius)) {
                for (int offsetZ : List.of(-radius, 0, radius)) {
                    if (offsetX == 0 && offsetZ == 0) continue;
                    world.put(offsetX, 64, offsetZ, Material.STONE, Set.of(BlockFace.UP));
                    if (radius <= 8) world.put(offsetX, 40, offsetZ, Material.STONE, Set.of(BlockFace.UP));
                }
            }
        }
        var estimate = IronElevatorGround.measure(world.block(0, 200, 0), List.of(40, 64, 100, 200));
        assertEquals(64, estimate.height());
        assertTrue(estimate.stable());
    }

    @Test
    void doesNotFreezeAGardenRoofAsGroundWhenTheExtendedChunksAreUnloaded() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.terrainHeight = 200;
        for (int height : List.of(40, 64, 100, 200)) world.iron(height);
        surface(world, 0, 0, List.of(4, 8, 16, 32, 64, 128), 200, Material.GRASS_BLOCK);
        surface(world, 0, 0, List.of(256, 512), 64, Material.GRASS_BLOCK);
        for (int radius : List.of(256, 512)) {
            for (int offsetX : List.of(-radius, 0, radius)) {
                for (int offsetZ : List.of(-radius, 0, radius)) {
                    if (offsetX != 0 || offsetZ != 0)
                        world.unloadedChunks.add((offsetX >> 4) + "," + (offsetZ >> 4));
                }
            }
        }
        AtomicLong clock = new AtomicLong();
        var floors = new IronElevatorFloors(clock::get);
        var estimate = IronElevatorGround.measure(world.block(0, 200, 0), List.of(40, 64, 100, 200));
        assertEquals(40, estimate.height());
        assertFalse(estimate.stable());
        assertEquals(48, world.terrainReads);
        assertEquals(List.of(1, 2, 3, 4), floors.scan(world.block(0, 200, 0), world.origin(200))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        world.unloadedChunks.clear();
        clock.set(2_000_000_001L);
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 200, 0), world.origin(200))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void doesNotExpandOrResampleAnAlreadyReliableGroundReference() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(40, 64, 100, 200)) world.iron(height);
        AtomicLong clock = new AtomicLong();
        var floors = new IronElevatorFloors(clock::get);
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 64, 0), world.origin(64))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(48, world.terrainReads);
        world.terrainHeight = 200;
        clock.set(2_000_000_001L);
        assertEquals(List.of(-1, 1, 2, 3), floors.scan(world.block(0, 200, 0), world.origin(200))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        assertEquals(48, world.terrainReads);
    }

    @Test
    void doesNotShareGroundWithSeparateShaftsOrOtherWorlds() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : List.of(40, 64, 100)) world.iron(height);
        for (int height : List.of(70, 100, 140))
            world.put(8, height, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        var floors = new IronElevatorFloors();
        assertEquals(List.of(-1, 1, 2), floors.scan(world.block(0, 64, 0), world.origin(64))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        world.terrainHeight = 100;
        assertEquals(List.of(-1, 1, 2), floors.scan(world.block(8, 140, 0), world.origin(140))
                .stream().map(IronElevatorFloors.Floor::number).toList());
        IronElevatorTestWorld other = new IronElevatorTestWorld();
        other.terrainHeight = 100;
        for (int height : List.of(70, 100, 140)) other.iron(height);
        assertEquals(List.of(-1, 1, 2), floors.scan(other.block(0, 140, 0), other.origin(140))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    private static void surface(IronElevatorTestWorld world, int centerX, int centerZ, List<Integer> radii,
                                int height, Material material) {
        for (int radius : radii) {
            for (int offsetX : List.of(-radius, 0, radius)) {
                for (int offsetZ : List.of(-radius, 0, radius)) {
                    if (offsetX == 0 && offsetZ == 0) continue;
                    int blockX = centerX + offsetX;
                    int blockZ = centerZ + offsetZ;
                    world.terrain.put(new IronElevatorTestWorld.Position(blockX, 0, blockZ), height);
                    world.put(blockX, height, blockZ, material, Set.of(BlockFace.UP));
                }
            }
        }
    }
}
