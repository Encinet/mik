package org.encinet.mik.module.afk.viewing;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.encinet.mik.module.afk.viewing.ScreenGeometry.Point;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AfkViewingOcclusionTest {
    private final Point eye = new Point(0.5, 1.5, -5);
    private final Point target = new Point(0.5, 1.5, -0.02);

    @Test
    void unobstructedScreenIsVisible() {
        assertTrue(AfkViewingController.hasLineOfSight(world(true, List.of(), new AtomicInteger()), eye, target));
    }

    @Test
    void opaqueWallIsNotVisible() {
        RayTraceResult wall = hit(Material.STONE, new BoundingBox(0, 1, -3, 1, 2, -2));
        assertFalse(AfkViewingController.hasLineOfSight(world(true, List.of(wall), new AtomicInteger()), eye, target));
    }

    @Test
    void transparentGlassDoesNotHideTheScreen() {
        for (Material material : List.of(Material.GLASS, Material.GLASS_PANE, Material.WHITE_STAINED_GLASS)) {
            RayTraceResult glass = hit(material, new BoundingBox(0, 1, -3, 1, 2, -2));
            assertTrue(AfkViewingController.hasLineOfSight(world(true, List.of(glass), new AtomicInteger()), eye, target));
        }
    }

    @Test
    void wallBehindGlassIsStillAnObstruction() {
        RayTraceResult glass = hit(Material.GLASS, new BoundingBox(0, 1, -3, 1, 2, -2));
        RayTraceResult wall = hit(Material.STONE, new BoundingBox(0, 1, -1.5, 1, 2, -1));
        assertFalse(AfkViewingController.hasLineOfSight(world(true, List.of(glass, wall), new AtomicInteger()), eye, target));
    }

    @Test
    void unloadedChunksCannotBeLoadedByViewingRays() {
        AtomicInteger rays = new AtomicInteger();
        assertFalse(AfkViewingController.hasLineOfSight(world(false, List.of(), rays), eye, target));
        assertEquals(0, rays.get());
    }

    @Test
    void veryLargeScreenCannotTriggerUnboundedRayWork() {
        AtomicInteger rays = new AtomicInteger();
        assertFalse(AfkViewingController.hasLineOfSight(world(true, List.of(), rays), eye, new Point(1_000, 1.5, -0.02)));
        assertEquals(0, rays.get());
    }

    @Test
    void zeroLengthRayIsNotViewingEvidence() {
        assertFalse(AfkViewingController.hasLineOfSight(world(true, List.of(), new AtomicInteger()), eye, eye));
    }

    private static World world(boolean loaded, List<RayTraceResult> hits, AtomicInteger rays) {
        ArrayDeque<RayTraceResult> remaining = new ArrayDeque<>(hits);
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isChunkLoaded" -> loaded;
                    case "rayTraceBlocks" -> {
                        rays.incrementAndGet();
                        yield remaining.poll();
                    }
                    default -> null;
                });
    }

    private static RayTraceResult hit(Material material, BoundingBox bounds) {
        Block block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getType" -> material;
                    case "getBoundingBox" -> bounds;
                    default -> null;
                });
        return new RayTraceResult(new Vector(0.5, 1.5, bounds.getMinZ()), block, BlockFace.NORTH);
    }
}
