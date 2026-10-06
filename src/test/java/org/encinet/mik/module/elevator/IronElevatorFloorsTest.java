package org.encinet.mik.module.elevator;

import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorFloorsTest {

    @TempDir
    Path temporary;

    @Test
    void numbersBasementsBelowTerrainWithoutFloorZero() {
        IronElevatorTestWorld world = floors(40, 50, 64, 80);
        var result = new IronElevatorFloors().scan(world.block(0, 64, 0), world.origin(64));

        assertEquals(List.of(-2, -1, 1, 2), result.stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void doesNotUseMinecraftYZeroAsTheGroundFloor() {
        assertEquals(2, IronElevatorFloors.groundIndex(List.of(-50, -30, -10, 5), -10));
        assertEquals(-1, IronElevatorFloors.floorNumber(1, 2));
        assertEquals(1, IronElevatorFloors.floorNumber(2, 2));
    }

    @Test
    void terrainEstimateAvoidsHighRoofOutliersAndHasFallback() {
        assertEquals(64, IronElevatorFloors.terrainReference(
                List.of(64, 64, 64, 64, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100), 10));
        assertEquals(10, IronElevatorFloors.terrainReference(List.of(), 10));
    }

    @Test
    void preservesTerrainReferenceWhileUpperFloorsAreAdded() {
        IronElevatorTestWorld world = floors(40, 64, 80);
        IronElevatorFloors levels = new IronElevatorFloors();
        levels.scan(world.block(0, 64, 0), world.origin(64));
        world.terrainHeight = 120;
        world.iron(100);
        levels.invalidate(world.block(0, 100, 0));

        assertEquals(List.of(-1, 1, 2, 3), levels.scan(world.block(0, 64, 0), world.origin(64))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void acceptsExplicitGroundFloorForAmbiguousBuildings() throws IOException {
        IronElevatorTestWorld world = floors(40, 50, 64, 80);
        Path config = temporary.resolve("iron-elevators.yml");
        Files.writeString(config, "ground-floors:\n  '" + world.worldId + "':\n    '0,0': 50\n");
        IronElevatorFloors levels = new IronElevatorFloors();
        levels.load(config);

        assertEquals(List.of(-1, 1, 2, 3), levels.scan(world.block(0, 50, 0), world.origin(50))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void netherRoofDoesNotBecomeTheDefaultGroundFloor() {
        IronElevatorTestWorld world = floors(20, 40, 80);
        world.environment = World.Environment.NETHER;
        world.terrainHeight = 127;

        assertEquals(List.of(1, 2, 3), new IronElevatorFloors().scan(world.block(0, 40, 0), world.origin(40))
                .stream().map(IronElevatorFloors.Floor::number).toList());
    }

    @Test
    void requiresThreeUsableFloorsForThePanel() {
        IronElevatorTestWorld world = floors(20, 40);

        assertTrue(new IronElevatorFloors().scan(world.block(0, 20, 0), world.origin(20)).isEmpty());
    }

    @Test
    void rejectsMalformedGroundOverridesInsteadOfSilentlyRenumbering() throws IOException {
        Path config = temporary.resolve("invalid.yml");
        Files.writeString(config, "ground-floors: invalid\n");
        assertThrows(IllegalArgumentException.class, () -> new IronElevatorFloors().load(config));
        Files.writeString(config, "ground-floors: [\n");
        assertThrows(IllegalStateException.class, () -> new IronElevatorFloors().load(config));
    }

    private static IronElevatorTestWorld floors(int... heights) {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (int height : heights) {
            world.iron(height);
        }
        return world;
    }
}
