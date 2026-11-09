package org.encinet.mik.feature.world.plot;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotEditorMapTest {
    private final UUID playerId = UUID.randomUUID(), worldId = UUID.randomUUID();
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
            (_, method, _) -> switch (method.getName()) {
                case "getUID" -> worldId;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                default -> throw new AssertionError(method.getName());
            });
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (_, method, _) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, -1, 65, -1);
                default -> throw new AssertionError(method.getName());
            });

    @Test void mapNavigationNeverChangesDraftAndKeepsEachCornersHeight() {
        try (var cache = new PlotPreviewCache(Runnable::run)) {
            var map = new PlotEditorMap(cache);
            var state = new PlotSelectionState();
            state.bind(playerId, null, worldId);
            state.mark(playerId, true, new PlotPosition(worldId, -4, 64, -4));
            state.mark(playerId, false, new PlotPosition(worldId, 7, 79, 7));
            var draft = state.draft(playerId, null);
            var scene = map.scene(player, null, draft);
            assertNotNull(scene.pixels());
            map.zoom(player, null, -1);
            map.pan(player, null, 1, -1);
            map.layer(player, null, -100);
            assertEquals(-64, map.scene(player, null, draft).layer());
            map.layer(player, null, 1000);
            assertEquals(316, map.scene(player, null, draft).layer());
            map.near(player, null);
            scene = map.scene(player, null, draft);
            assertEquals(1, scene.viewport().step());
            assertEquals(64, scene.layer());
            assertSame(draft, state.draft(playerId, null));
            assertEquals(79, scene.points().second().y());
            map.disable();
        }
    }

    @Test void browsingRectanglesPreservesTheSelectionMapAndDraft() {
        try (var cache = new PlotPreviewCache(Runnable::run)) {
            var map = new PlotEditorMap(cache);
            var state = new PlotSelectionState();
            state.bind(playerId, null, worldId);
            Set<PlotGeometry.Cell> cells = new HashSet<>();
            for (int n = 0; n < 8; n++) cells.add(new PlotGeometry.Cell(n * 100, n * 5, 0));
            state.initialize(playerId, worldId, cells);
            var draft = state.draft(playerId, null);
            map.openRegions(player, null, true);
            var scene = map.scene(player, null, draft);
            var region = scene.regions().items().get(7);
            map.selectRegion(player, null, region);
            scene = map.scene(player, null, draft);
            assertEquals(region.bounds().minimumY(), scene.layer());
            assertTrue(scene.viewport().index(region.bounds().minimumX(), region.bounds().minimumZ()) >= 0);
            var viewport = scene.viewport();
            map.regionPosition(player, null, 6);
            scene = map.scene(player, null, draft);
            assertEquals(region,scene.regions().selected());
            assertEquals(viewport,scene.viewport());
            assertEquals(6, scene.regions().sourceIndex());
            assertSame(draft, state.draft(playerId, null));
        }
    }

}
