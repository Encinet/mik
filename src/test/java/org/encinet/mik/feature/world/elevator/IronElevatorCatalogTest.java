package org.encinet.mik.feature.world.elevator;

import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorCatalogTest {

    @TempDir
    Path temporary;

    @Test
    void numbersBasementsBelowTerrainWithoutFloorZero() {
        IronElevatorTestWorld world = floors(40, 50, 64, 80);
        var result = IronElevatorTestSupport.floors(new IronElevatorCatalog(), world.block(0, 64, 0));

        assertEquals(List.of(-2, -1, 1, 2), result.stream().map(IronElevatorCatalog.Floor::number).toList());
    }

    @Test
    void doesNotUseMinecraftYZeroAsTheGroundFloor() {
        assertEquals(2, IronElevatorGround.groundIndex(List.of(-50, -30, -10, 5), -10));
        assertEquals(-1, IronElevatorCatalog.floorNumber(1, 2));
        assertEquals(1, IronElevatorCatalog.floorNumber(2, 2));
    }

    @Test
    void terrainEstimateAvoidsHighRoofOutliersAndHasFallback() {
        var heights = List.of(64,64,64,64,100,100,100,100,100,100,100,100,100,100,100,100);
        var samples = java.util.stream.IntStream.range(0,heights.size())
                .mapToObj(index -> new IronElevatorGround.Sample(heights.get(index),index % 2 == 0 ? 8 : 16)).toList();
        assertEquals(64,IronElevatorGround.reference(samples,10));
        assertEquals(10, IronElevatorGround.reference(List.of(), 10));
    }

    @Test
    void preservesTerrainReferenceWhileUpperFloorsAreAdded() {
        IronElevatorTestWorld world = floors(40, 64, 80);
        IronElevatorCatalog levels = new IronElevatorCatalog();
        IronElevatorTestSupport.floors(levels, world.block(0, 64, 0));
        world.terrainHeight = 120;
        world.iron(100);
        levels.invalidate(world.block(0, 100, 0));

        assertEquals(List.of(-1, 1, 2, 3), IronElevatorTestSupport.floors(levels, world.block(0, 64, 0))
                .stream().map(IronElevatorCatalog.Floor::number).toList());
    }

    @Test
    void acceptsExplicitGroundFloorForAmbiguousBuildings() throws IOException {
        IronElevatorTestWorld world = floors(40, 50, 64, 80);
        Path config = temporary.resolve("iron-elevators.yml");
        Files.writeString(config, "ground-floors:\n  '" + world.worldId + "':\n    '0,0': 50\n");
        IronElevatorCatalog levels = new IronElevatorCatalog();
        levels.installRules(IronElevatorCatalog.readRules(config));

        assertEquals(List.of(-1, 1, 2, 3), IronElevatorTestSupport.floors(levels, world.block(0, 50, 0))
                .stream().map(IronElevatorCatalog.Floor::number).toList());
    }

    @Test
    void netherRoofDoesNotBecomeTheDefaultGroundFloor() {
        IronElevatorTestWorld world = floors(20, 40, 80);
        world.environment = World.Environment.NETHER;
        world.terrainHeight = 127;

        assertEquals(List.of(1, 2, 3), IronElevatorTestSupport.floors(new IronElevatorCatalog(), world.block(0, 40, 0))
                .stream().map(IronElevatorCatalog.Floor::number).toList());
    }

    @Test
    void includesBothFloorsOfATwoFloorElevator() {
        IronElevatorTestWorld world = floors(64, 80);
        var result = IronElevatorTestSupport.floors(new IronElevatorCatalog(), world.block(0, 64, 0));

        assertEquals(List.of(64, 80), result.stream().map(floor -> floor.level().height()).toList());
        assertEquals(List.of(1, 2), result.stream().map(IronElevatorCatalog.Floor::number).toList());
    }

    @Test
    void requiresAtLeastTwoUsableFloorsForThePanel() {
        IronElevatorTestWorld world = floors(20);

        assertTrue(IronElevatorTestSupport.floors(new IronElevatorCatalog(), world.block(0, 20, 0)).isEmpty());

        world.iron(40);
        world.put(0, 41, 0, Material.STONE, Set.of(BlockFace.UP));

        assertTrue(IronElevatorTestSupport.floors(new IronElevatorCatalog(), world.block(0, 20, 0)).isEmpty());
    }

    @Test
    void rejectsMalformedGroundOverridesInsteadOfSilentlyRenumbering() throws IOException {
        Path config = temporary.resolve("invalid.yml");
        Files.writeString(config, "ground-floors: invalid\n");
        assertThrows(IllegalArgumentException.class, () -> IronElevatorCatalog.readRules(config));
        Files.writeString(config, "ground-floors: [\n");
        assertThrows(IllegalStateException.class, () -> IronElevatorCatalog.readRules(config));
    }

    @Test
    void explicitGroundHeightMustMatchARealUsableFloor() throws IOException {
        var world = floors(40,64,80);
        Path config = temporary.resolve("missing-floor.yml");
        Files.writeString(config,"ground-floors:\n  '" + world.worldId + "':\n    '0,0': 50\n");
        var catalog = new IronElevatorCatalog();
        catalog.installRules(IronElevatorCatalog.readRules(config));
        assertThrows(IllegalArgumentException.class,() -> IronElevatorTestSupport.floors(catalog,world.block(0,40,0)));
    }

    private static IronElevatorTestWorld floors(int... heights) {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : heights) {
            world.iron(height);
        }
        return world;
    }
}
