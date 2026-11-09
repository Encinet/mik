package org.encinet.mik.feature.world.plot;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
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
    private final List<PlotBoundaryFrame> frames = new ArrayList<>();
    private final PlotSpatialPreview preview = new PlotSpatialPreview(cache, (_, frame) -> frames.add(frame));

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
        assertFalse(frames.getLast().lines().isEmpty());
        assertEquals(2, frames.getLast().labels().size());
        assertTrue(viewer.particles().size() <= 96);
        assertTrue(viewer.particles().stream().allMatch(particle -> distanceSquared(particle, 0, 66, 0) <= 24 * 24 + 1.0E-6));
        assertTrue(viewer.particles().stream().anyMatch(particle -> particle.color().equals(Color.fromRGB(96, 220, 125))));
        assertTrue(frames.getLast().lines().stream().anyMatch(line -> line.material() == org.bukkit.Material.CYAN_CONCRETE));
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
        assertFalse(frames.getLast().lines().isEmpty());
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
        assertTrue(viewer.particles().size() <= 96);
        var contour = frames.getLast().lines().stream().filter(line -> line.material() == org.bukkit.Material.PURPLE_CONCRETE)
                .map(PlotBoundaryFrame.Line::edge).toList();
        assertTrue(contour.stream().anyMatch(edge -> edge.first().horizontal() == 4 && edge.first().forward() == 4));
        assertTrue(contour.stream().noneMatch(edge -> edge.at(.5).horizontal() > 4 && edge.at(.5).forward() > 4));
        assertTrue(viewer.particles().stream().anyMatch(sample -> sample.color().equals(Color.fromRGB(255, 192, 75))));
    }

    @Test
    void unchangedSavedAndSelectedBoundsDoNotDrawTwoOverlappingContours() {
        Viewer viewer = viewer(0, 66, 0);
        Set<Cell> cells = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0));
        preview.show(viewer.player(), plot(viewer.world(), cells),
                new PlotPosition(viewer.world(), 0, 64, 0), new PlotPosition(viewer.world(), 7, 67, 3),
                PlotSelectionShape.fromCells(viewer.world(), cells));
        assertTrue(frames.getLast().lines().stream().anyMatch(line -> line.material() == org.bukkit.Material.CYAN_CONCRETE));
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
        assertTrue(frames.getLast().lines().stream().anyMatch(line -> line.edge().first().horizontal() == 8 || line.edge().second().horizontal() == 8));
    }

    @Test
    void coincidentCornersShareOneReadableLabelAndMarker() {
        Viewer viewer = viewer(0, 66, 0);
        var point = new PlotPosition(viewer.world(), 0, 64, 0);
        preview.show(viewer.player(), null, point, point);
        assertEquals(1, frames.getLast().labels().size());
        assertEquals("A/B", frames.getLast().labels().getFirst().name());
        assertTrue(frames.getLast().lines().stream().noneMatch(line -> line.material() == org.bukkit.Material.YELLOW_CONCRETE));
    }

    @Test
    void changedMidFloorSectionKeepsItsDraftColorWhenNoThreeDimensionalCornerIsNearby() {
        Viewer viewer = viewer(-1, 65, 64);
        Set<Cell> saved = new java.util.HashSet<>(), candidate = new java.util.HashSet<>();
        for (int y = 0; y < 32; y++) for (int z = 0; z < 32; z++) for (int x = 0; x < 3; x++) {
            candidate.add(new Cell(x, y, z));
            if (x < 2) saved.add(new Cell(x, y, z));
        }
        preview.show(viewer.player(), plot(viewer.world(), saved), null, null,
                new PlotSelectionShape(viewer.world(), null, candidate));
        var frame = frames.getLast();
        assertFalse(frame.lines().isEmpty());
        assertTrue(frame.lines().stream().allMatch(line -> line.material() == org.bukkit.Material.PURPLE_CONCRETE));
        assertTrue(frame.lines().stream().allMatch(line -> Math.abs(line.edge().first().vertical() - 65.06) < .001));
        assertTrue(frame.lines().size() <= PlotBoundaryFrame.MAX_LINES);
    }

    @Test
    void pendingGeometryDoesNotBlockOrAccessThePlayerFromTheWorker() throws Exception {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        try (var cache = new PlotPreviewCache(tasks::add)) {
            var asynchronous = new PlotSpatialPreview(cache, (_, frame) -> frames.add(frame));
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
            assertFalse(frames.getLast().lines().isEmpty());
            assertTrue(viewer.particles().size() <= 96);
        }
    }

    private static double distanceSquared(Sample sample, double horizontal, double vertical, double forward) {
        return Math.pow(sample.horizontal() - horizontal, 2) + Math.pow(sample.vertical() - vertical, 2)
                + Math.pow(sample.forward() - forward, 2);
    }

    private static Plot plot(UUID world, Set<Cell> cells) {
        return new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Workshop", 0,
                cells, Map.of(), Map.of(), null);
    }

    private static Viewer viewer(double horizontal, double vertical, double forward) {
        Thread caller = Thread.currentThread();
        UUID worldId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        List<Sample> particles = new ArrayList<>();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (_, method, _) -> {
                    if (method.getName().equals("getUID")) return worldId;
                    throw new AssertionError("Unexpected world access: " + method.getName());
                });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (_, method, arguments) -> {
                    assertSame(caller, Thread.currentThread(), method.getName());
                    return switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getUniqueId" -> playerId;
                    case "getLocation", "getEyeLocation" -> new Location(world, horizontal, vertical, forward);
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
