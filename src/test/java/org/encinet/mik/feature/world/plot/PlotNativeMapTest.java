package org.encinet.mik.feature.world.plot;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotNativeMapTest {
    private final UUID playerId = UUID.randomUUID(), worldId = UUID.randomUUID();
    private final AtomicInteger inventoryRefreshes = new AtomicInteger();
    private final PlayerInventory inventory = proxy(PlayerInventory.class, (name, _) -> {
        throw new AssertionError("The map must not read or modify real inventory items: " + name);
    });
    private World currentWorld = world(worldId);
    private final Player player = proxy(Player.class, (name, _) -> switch (name) {
        case "getUniqueId" -> playerId;
        case "getWorld" -> currentWorld;
        case "getInventory" -> inventory;
        case "updateInventory" -> { inventoryRefreshes.incrementAndGet(); yield null; }
        default -> throw new AssertionError(name);
    });
    private final InventoryView inventoryView = proxy(InventoryView.class, (name, args) -> switch (name) {
        case "getPlayer" -> player;
        case "getInventory", "getTopInventory", "getBottomInventory" -> inventory;
        case "convertSlot" -> (int) args[0] == 45 ? 40 : args[0];
        case "getType" -> InventoryType.CRAFTING;
        default -> throw new AssertionError(name);
    });
    private final List<byte[]> patches = new ArrayList<>();
    private int restores;
    private final PlotNativeMapViewer.Output output = new PlotNativeMapViewer.Output() {
        public void show(Player player, PlotEditorMap.Scene scene, byte[] changedPixels) { patches.add(changedPixels); }
        public void restore(Player player) { restores++; }
    };

    @Test void terrainRemainsVisibleAndContoursPreserveCourtyardHoles() {
        var fixture = PlotMapTestSupport.scene(0);
        boolean[] saved = new boolean[PlotMapViewport.COUNT], candidate = new boolean[PlotMapViewport.COUNT];
        for (int z = 20; z < 60; z++) for (int x = 20; x < 60; x++) {
            if (x >= 30 && x < 50 && z >= 30 && z < 50) continue;
            saved[z * 128 + x] = candidate[z * 128 + x] = true;
        }
        var raster = PlotMapTestSupport.nativeRaster();
        var unchanged = scene(fixture, saved, candidate);
        byte[] pixels = raster.render(unchanged);
        assertEquals(128 * 128, pixels.length);
        assertEquals(pixels[80 * 128 + 80], pixels[40 * 128 + 40]);
        assertNotEquals(pixels[40 * 128 + 40], pixels[40 * 128 + 29]);
        byte[] added = raster.render(scene(fixture, new boolean[pixels.length], candidate));
        byte[] removed = raster.render(scene(fixture, saved, new boolean[pixels.length]));
        assertNotEquals(pixels[20 * 128 + 20], added[20 * 128 + 20]);
        assertNotEquals(added[20 * 128 + 20], removed[20 * 128 + 20]);
    }

    @Test void unchangedPixelsAreNotResentAndWorldChangesRestoreTheOffhand() {
        try (var cache = new PlotPreviewCache(Runnable::run)) {
            var viewer = new PlotNativeMapViewer(cache, output, _ -> player, PlotMapTestSupport.nativeRaster());
            var scene = PlotMapTestSupport.scene(0);
            viewer.show(player, scene);
            assertEquals(PlotMapViewport.COUNT, patches.getFirst().length);
            viewer.show(player, scene);
            assertNull(patches.getLast());
            assertTrue(viewer.visible(playerId));
            currentWorld = world(UUID.randomUUID());
            viewer.tick();
            assertFalse(viewer.visible(playerId));
            assertEquals(1, restores);
            viewer.hide(playerId);
            assertEquals(1, restores);
        }
    }

    @Test void unexploredPixelsStayTransparentAndInitialProgressDisappearsAfterFirstCompleteSurface() {
        var source = PlotMapTestSupport.scene(0);
        var empty = scene(source, new boolean[PlotMapViewport.COUNT], new boolean[PlotMapViewport.COUNT]);
        var unknown = withTerrain(empty, new PlotMapTerrain.Snapshot(2,
                java.util.Collections.nCopies(PlotMapViewport.COUNT, 0), PlotMapViewport.COUNT / 4, 0));
        assertTrue(allBlank(PlotMapTestSupport.nativeRaster().render(unknown)));
        try (var cache = new PlotPreviewCache(Runnable::run)) {
            var viewer = new PlotNativeMapViewer(cache, output, _ -> player, PlotMapTestSupport.nativeRaster());
            viewer.show(player, unknown);
            assertEquals(25, viewer.progress(playerId).orElseThrow());
            viewer.show(player, empty);
            assertTrue(viewer.progress(playerId).isEmpty());
            viewer.show(player, unknown);
            assertTrue(viewer.progress(playerId).isEmpty());
        }
    }

    @Test void changingLayerClearsTheOldContourUntilTheReplacementImageIsReady() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        try (var cache = new PlotPreviewCache(tasks::add)) {
            var viewer = new PlotNativeMapViewer(cache, output, _ -> player, PlotMapTestSupport.nativeRaster());
            var scene = PlotMapTestSupport.scene(0);
            viewer.show(player, scene);
            tasks.remove().run();
            viewer.show(player, scene);
            assertFalse(allBlank(patches.getLast()));
            var other = new PlotEditorMap.Scene(scene.stamp(), scene.viewport(), scene.layer() + 4, scene.first(),
                    scene.regions(), scene.points(), scene.terrain(), scene.pixels(), false);
            viewer.show(player, other);
            assertTrue(allBlank(patches.getLast()));
            viewer.hide(playerId);
            tasks.remove().run();
            assertEquals(1, restores);
        }
    }

    @Test void completedSurfaceIsPublishedEvenWhenEveryRefreshHasANewerTerrainRevision() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        try (var cache = new PlotPreviewCache(tasks::add)) {
            var viewer = new PlotNativeMapViewer(cache, output, _ -> player, PlotMapTestSupport.nativeRaster());
            var source = PlotMapTestSupport.scene(0);
            viewer.show(player, source);
            assertTrue(allBlank(patches.getLast()));
            tasks.remove().run();
            var newer = withTerrain(source, new PlotMapTerrain.Snapshot(source.terrain().revision() + 1,
                    source.terrain().colors(), source.terrain().read(), 0));
            viewer.show(player, newer);
            assertNotNull(patches.getLast());
            assertFalse(allBlank(patches.getLast()));
            assertEquals(1, tasks.size());
            viewer.hide(playerId);
            tasks.remove().run();
        }
    }

    @Test void closingOrExpiringAViewCancelsPendingRendering() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        try (var cache = new PlotPreviewCache(tasks::add)) {
            var viewer = new PlotNativeMapViewer(cache, output, _ -> player, PlotMapTestSupport.nativeRaster());
            var scene = PlotMapTestSupport.scene(0);
            viewer.show(player, scene);
            assertEquals(1, tasks.size());
            assertTrue(allBlank(patches.getLast()));
            viewer.hide(playerId);
            tasks.remove().run();
            assertEquals(1, patches.size());
            viewer.show(player, scene);
            for (int tick = 0; tick < 31; tick++) viewer.tick();
            assertFalse(viewer.visible(playerId));
            assertEquals(2, restores);
            tasks.remove().run();
            assertEquals(2, patches.size());
        }
    }

    @Test void inventoryUseRestoresActualStateAndOnlyBlocksTransfersOfTheMaskedOffhand() {
        try (var cache = new PlotPreviewCache(Runnable::run)) {
            var viewer = new PlotNativeMapViewer(cache, output, _ -> player, PlotMapTestSupport.nativeRaster());
            var scene = PlotMapTestSupport.scene(0);
            viewer.show(player, scene);
            var offhand = click(45);
            viewer.onClick(offhand);
            assertTrue(offhand.isCancelled());
            assertFalse(viewer.visible(playerId));
            assertEquals(1, restores);
            assertEquals(1, inventoryRefreshes.get());
            viewer.show(player, scene);
            assertFalse(viewer.visible(playerId));
            viewer.onClose(new InventoryCloseEvent(inventoryView));
            viewer.show(player, scene);
            var ordinary = click(10);
            viewer.onClick(ordinary);
            assertFalse(ordinary.isCancelled());
            assertEquals(2, restores);
        }
    }

    private InventoryClickEvent click(int rawSlot) {
        return new InventoryClickEvent(inventoryView, InventoryType.SlotType.QUICKBAR, rawSlot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }
    private static PlotEditorMap.Scene scene(PlotEditorMap.Scene source, boolean[] saved, boolean[] candidate) {
        return new PlotEditorMap.Scene(source.stamp(), source.viewport(), source.layer(), source.first(),
                new PlotEditorMap.Regions(List.of(), null, 0, false, false, source.regions().sourceIndex()),
                source.points(), source.terrain(), new PlotEditorMap.Pixels(saved, candidate), false);
    }
    private static PlotEditorMap.Scene withTerrain(PlotEditorMap.Scene source, PlotMapTerrain.Snapshot terrain) {
        return new PlotEditorMap.Scene(source.stamp(), source.viewport(), source.layer(), source.first(), source.regions(),
                source.points(), terrain, source.pixels(), false);
    }
    private static boolean allBlank(byte[] pixels) { for (byte pixel : pixels) if (pixel != 0) return false; return true; }
    private static World world(UUID id) { return proxy(World.class, (name, _) -> {
        if (name.equals("getUID")) return id;
        throw new AssertionError(name);
    }); }
    @FunctionalInterface private interface Call { Object apply(String name, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (_, method, args) -> call.apply(method.getName(), args)));
    }
}
