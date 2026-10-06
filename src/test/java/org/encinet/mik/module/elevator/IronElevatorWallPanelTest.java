package org.encinet.mik.module.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronElevatorWallPanelTest {

    @Test
    void protectsTheWholePanelIncludingGapsAndDisabledButtons() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.EAST);
        IronElevatorPlatform platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        IronElevatorWall wall = IronElevatorWall.find(platform);
        IronElevatorPanelLayout layout = wall.surface().layout(82);
        for (double above : List.of(layout.centerAbove(), layout.floor(0, layout.pageSize()).above(),
                layout.navigation(0).above())) {
            Player viewer = viewer(world, world.origin(20), wall.position(layout.centerSideways(), above));
            assertNotNull(IronElevatorWallPanel.hitAt(viewer, platform, wall, layout));
        }
        var disabled = IronElevatorWallPanel.floorButton(layout, 0, layout.pageSize(), 40, false);
        var hit = IronElevatorWallPanel.hitAt(viewer(world, world.origin(20),
                wall.position(disabled.sideways(), disabled.above())), platform, wall, layout);
        assertEquals(disabled, IronElevatorWallPanel.buttonAt(hit, List.of(disabled)));
    }

    @Test
    void retainsTheWallAndHitTargetAcrossNeighboringPlatformBlocks() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.put(1, 20, 0, Material.IRON_BLOCK, Set.of(BlockFace.values()));
        for (int sideways = -1; sideways <= 1; sideways++) {
            for (int above = 1; above <= 2; above++)
                world.put(2, 20 + above, sideways, Material.STONE, Set.of(BlockFace.WEST));
        }
        IronElevatorPlatform first = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        IronElevatorPlatform second = IronElevatorPlatform.resolve(world.block(1, 20, 0));
        IronElevatorWall wall = IronElevatorWall.find(first);
        assertNotNull(wall);
        assertEquals(wall, IronElevatorWall.find(second));
        IronElevatorPanelLayout layout = wall.surface().layout(20);
        assertNotNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20), wall.position(0, 0.3)),
                first, wall, layout));
        assertNotNull(IronElevatorWallPanel.hitAt(viewer(world, new Location(world.world, 1.5, 21, 0.5),
                wall.position(0, 0.3)), first, wall, layout));
    }

    @Test
    void doesNotProtectOutsideThePanelThroughObstaclesOrAfterLeavingThePlatform() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.EAST);
        IronElevatorPlatform platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        IronElevatorWall wall = IronElevatorWall.find(platform);
        IronElevatorPanelLayout layout = wall.surface().layout(20);
        assertNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20), wall.position(0, 1)),
                platform, wall, layout));
        assertNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20).add(0, 0.5, 0),
                wall.position(0, 0.3)), platform, wall, layout));
        world.obstruction = new RayTraceResult(wall.position(0, 0.3).toVector());
        assertNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20), wall.position(0, 0.3)),
                platform, wall, layout));
    }

    @Test
    void denseButtonsHaveDistinctHitAreasAndLabelsContainOnlyNumbers() {
        var serializer = PlainTextComponentSerializer.plainText();
        assertEquals("1", serializer.serialize(IronElevatorWallPanel.floorLabel(1, true, false)));
        assertEquals("-2", serializer.serialize(IronElevatorWallPanel.floorLabel(-2, false, true)));
        assertEquals("20", serializer.serialize(IronElevatorWallPanel.floorLabel(20, false, false)));
        for (var layout : List.of(IronElevatorPanelLayout.fit(-1.5, 1.5, -1, 1, 20),
                IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 1, 20),
                IronElevatorPanelLayout.fit(-0.5, 1.5, -1, 2, 20))) {
            List<IronElevatorWallPanel.Button> buttons = java.util.stream.IntStream.range(0, layout.pageSize())
                    .mapToObj(slot -> IronElevatorWallPanel.floorButton(layout, slot, layout.pageSize(),
                            10 + slot * 4, true)).toList();
            for (var button : buttons) {
                var hit = new IronElevatorWall.Hit(button.sideways(), button.above() - 0.045, 1);
                assertEquals(button, IronElevatorWallPanel.buttonAt(hit, buttons));
            }
        }
    }

    @Test
    void protectsTheLowReachablePanelButNotTheWallNearThePlayersFeet() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.EAST);
        var platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        var geometry = new IronElevatorPanelGeometry(() -> 0);
        var plan = geometry.plan(platform, 20);
        var layout = plan.layout();
        var wall = plan.wall();
        Location bottom = wall.position(layout.navigation(0).sideways(), layout.navigation(0).above());
        assertTrue(bottom.getY() - layout.halfHeight() >= 22);
        assertNotNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20), bottom), platform, wall, layout));
        assertNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20), wall.position(0, -0.2)),
                platform, wall, layout));
        world.put(1, 22, 0, Material.AIR, Set.of());
        assertNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20), bottom), platform, wall, layout));
    }

    @Test
    void rejectsOldPanelBoundsAfterTheWallNarrows() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        world.iron(20);
        world.wall(20, BlockFace.EAST);
        IronElevatorPlatform platform = IronElevatorPlatform.resolve(world.block(0, 20, 0));
        IronElevatorWall wall = IronElevatorWall.find(platform);
        IronElevatorPanelLayout layout = wall.surface().layout(20);
        world.put(1, 21, -1, Material.AIR, Set.of());
        assertNull(IronElevatorWallPanel.hitAt(viewer(world, world.origin(20), wall.position(0, 0)),
                platform, wall, layout));
    }

    private static Player viewer(IronElevatorTestWorld world, Location feet, Location target) {
        Location eye = feet.clone().add(0, 1.62, 0);
        eye.setDirection(target.toVector().subtract(eye.toVector()));
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getLocation" -> feet.clone();
                    case "getEyeLocation" -> eye.clone();
                    case "getWorld" -> world.world;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
