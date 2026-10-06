package org.encinet.mik.module.plot;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class PlotSpatialPreviewTest {
    private final PlotPreviewCache cache = new PlotPreviewCache(Runnable::run);
    private final PlotSpatialPreview preview = new PlotSpatialPreview(cache);

    @AfterEach
    void clearCache() {
        cache.close();
    }

    @Test
    void selectionAndCoreArePrivateBoundedAndUseDifferentColors() {
        Viewer viewer = viewer(0, 66, 0);
        Plot plot = plot(viewer.world(), Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0)));
        preview.show(viewer.player(), plot,
                Map.of(viewer.playerId(), new PlotPosition(viewer.world(), 0, 64, 0)),
                Map.of(viewer.playerId(), new PlotPosition(viewer.world(), 7, 67, 3)));
        assertTrue(viewer.particles().size() > 100);
        assertTrue(viewer.particles().size() <= 256);
        assertTrue(viewer.particles().stream().allMatch(particle -> distanceSquared(particle, 0, 66, 0) <= 24 * 24 + 1.0E-6));
        assertTrue(viewer.particles().stream().anyMatch(particle -> particle.color().equals(Color.fromRGB(96, 220, 125))));
        assertTrue(viewer.particles().stream().anyMatch(particle -> particle.color().equals(Color.fromRGB(66, 202, 221))));
        assertTrue(viewer.particles().stream().anyMatch(particle -> particle.color().equals(Color.fromRGB(255, 192, 75))));
    }

    @Test
    void foreignWorldPlotsAndSelectionsAreNeverDrawn() {
        Viewer viewer = viewer(0, 66, 0);
        UUID foreign = UUID.randomUUID();
        preview.show(viewer.player(), plot(foreign, Set.of(new Cell(0, 16, 0))),
                Map.of(viewer.playerId(), new PlotPosition(foreign, 0, 64, 0)),
                Map.of(viewer.playerId(), new PlotPosition(foreign, 3, 67, 3)));
        assertTrue(viewer.particles().isEmpty());
        preview.show(viewer.player(), null,
                Map.of(viewer.playerId(), new PlotPosition(foreign, 0, 64, 0)),
                Map.of(viewer.playerId(), new PlotPosition(viewer.world(), 3, 67, 3)));
        assertTrue(viewer.particles().size() <= 16);
    }

    @Test
    void creationSelectionShowsItsOutlineBeforeAPlotExists() {
        Viewer viewer = viewer(0, 66, 0);
        preview.show(viewer.player(), null, null, null,
                new PlotSelectionShape(viewer.world(), null, Set.of(new Cell(0, 16, 0))));
        assertTrue(viewer.particles().size() > 50);
        assertTrue(viewer.particles().stream().allMatch(particle -> particle.horizontal() >= 0 && particle.horizontal() <= 4
                && particle.vertical() >= 64 && particle.vertical() <= 68
                && particle.forward() >= 0 && particle.forward() <= 4));
    }

    @Test
    void editedCompositeContoursRemainBoundedAndKeepTheirConcaveBoundary() {
        Viewer viewer = viewer(0, 66, 0);
        Set<Cell> cells = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(0, 16, 1));
        preview.show(viewer.player(), plot(viewer.world(), Set.of(new Cell(0, 16, 0))),
                new PlotPosition(viewer.world(), 9, 64, 0), new PlotPosition(viewer.world(), 11, 67, 3),
                new PlotSelectionShape(viewer.world(), null, cells));
        assertTrue(viewer.particles().size() <= 256);
        List<Sample> contour = viewer.particles().stream()
                .filter(sample -> sample.color().equals(Color.fromRGB(205, 125, 255))).toList();
        assertTrue(contour.stream().anyMatch(sample -> sample.horizontal() == 4 && sample.forward() == 4));
        assertTrue(contour.stream().noneMatch(sample -> sample.horizontal() > 4 && sample.forward() > 4));
        assertTrue(viewer.particles().stream().anyMatch(sample -> sample.color().equals(Color.fromRGB(255, 192, 75))));
    }

    @Test
    void unchangedSavedAndSelectedBoundsDoNotDrawTwoOverlappingContours() {
        Viewer viewer = viewer(0, 66, 0);
        Set<Cell> cells = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0));
        preview.show(viewer.player(), plot(viewer.world(), cells),
                new PlotPosition(viewer.world(), 0, 64, 0), new PlotPosition(viewer.world(), 7, 67, 3),
                PlotSelectionShape.fromCells(viewer.world(), cells));
        assertTrue(viewer.particles().stream().anyMatch(sample -> sample.color().equals(Color.fromRGB(66, 202, 221))));
        assertTrue(viewer.particles().stream().noneMatch(sample -> sample.color().equals(Color.fromRGB(205, 125, 255))));
    }

    @Test
    void geometryCacheEvictionAndChangedConstructionRemainSafe() {
        Viewer viewer = viewer(0, 66, 0);
        for (int index = 0; index < 24; index++) {
            preview.show(viewer.player(), plot(viewer.world(), Set.of(new Cell(index, 16, 0))),
                    Map.of(), Map.of());
        }
        viewer.particles().clear();
        preview.show(viewer.player(), plot(viewer.world(), Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0))),
                Map.of(), Map.of());
        assertTrue(viewer.particles().stream().anyMatch(particle -> particle.horizontal() == 8));
    }

    @Test
    void pendingGeometryDoesNotBlockOrAccessThePlayerFromTheWorker() throws Exception {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        try (var cache = new PlotPreviewCache(tasks::add)) {
            var asynchronous = new PlotSpatialPreview(cache);
            Viewer viewer = viewer(0, 66, 0);
            Plot plot = plot(viewer.world(), Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0)));
            asynchronous.show(viewer.player(), plot, Map.of(), Map.of());
            assertTrue(viewer.particles().isEmpty());
            for (int count = 0; count < 100; count++) asynchronous.show(viewer.player(), plot, Map.of(), Map.of());
            assertEquals(1, tasks.size());
            Thread worker = new Thread(tasks.remove(), "spatial-preview-test");
            worker.start();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertTrue(viewer.particles().isEmpty());
            asynchronous.show(viewer.player(), plot, Map.of(), Map.of());
            assertFalse(viewer.particles().isEmpty());
            assertTrue(viewer.particles().size() <= 256);
        }
    }

    private static double distanceSquared(Sample sample, double horizontal, double vertical, double forward) {
        return Math.pow(sample.horizontal() - horizontal, 2) + Math.pow(sample.vertical() - vertical, 2)
                + Math.pow(sample.forward() - forward, 2);
    }

    private static Plot plot(UUID world, Set<Cell> cells) {
        return new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Workshop", false, 0,
                cells, Map.of(), Map.of(), null);
    }

    private static Viewer viewer(double horizontal, double vertical, double forward) {
        Thread caller = Thread.currentThread();
        UUID worldId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        List<Sample> particles = new ArrayList<>();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getUID")) return worldId;
                    throw new AssertionError("Unexpected world access: " + method.getName());
                });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> {
                    assertSame(caller, Thread.currentThread(), method.getName());
                    return switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getUniqueId" -> playerId;
                    case "getEyeLocation" -> new Location(world, horizontal, vertical, forward);
                    case "spawnParticle" -> {
                        assertEquals(Particle.DUST, arguments[0]);
                        assertEquals(1, arguments[4]);
                        Particle.DustOptions options = (Particle.DustOptions) arguments[9];
                        particles.add(new Sample((double) arguments[1], (double) arguments[2],
                                (double) arguments[3], options.getColor()));
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected player access: " + method.getName());
                    };
                });
        return new Viewer(worldId, playerId, player, particles);
    }

    private record Viewer(UUID world, UUID playerId, Player player, List<Sample> particles) {
    }

    private record Sample(double horizontal, double vertical, double forward, Color color) {
    }
}
