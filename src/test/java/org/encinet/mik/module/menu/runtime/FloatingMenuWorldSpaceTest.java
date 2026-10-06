package org.encinet.mik.module.menu.runtime;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuWorldSpaceTest {
    @Test
    void checksInteriorDepthInsteadOfOnlyTheFrontFaceOfAModel() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        scene.block(0, 64, 2, new BoundingBox(0, 0, 0, 1, 1, 1));
        var space = new FloatingMenuWorldSpace(scene.world());
        assertTrue(space.clearSurface(new Vector(0.5, 64.5, 1.5),
                new Vector(1, 0, 0), new Vector(0, 1, 0), 0.25, 0.25, 0.05));
        assertFalse(space.clearSurface(new Vector(0.5, 64.5, 1.5),
                new Vector(1, 0, 0), new Vector(0, 1, 0), 0.25, 0.25, 1));
    }
    @Test
    void detectsThinShapesInsideAPanelEvenWhenItsCornersAreClear() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        scene.block(0, 64, 2, new BoundingBox(0.22, 0, 0.40, 0.28, 1, 0.48));
        FloatingMenuWorldSpace space = new FloatingMenuWorldSpace(scene.world());
        assertFalse(space.clearSurface(new Vector(0.5, 64.5, 2.44),
                new Vector(1, 0, 0), new Vector(0, 1, 0), 0.48, 0.48, 0.04));
    }

    @Test
    void usesCollisionShapesRatherThanWholeBlocksAndCachesThemPerFit() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        scene.block(0, 64, 2, new BoundingBox(0, 0, 0, 1, 0.5, 1));
        FloatingMenuWorldSpace space = new FloatingMenuWorldSpace(scene.world());
        assertTrue(space.clearSurface(new Vector(0.5, 64.8, 2.5),
                new Vector(1, 0, 0), new Vector(0, 1, 0), 0.3, 0.15, 0.04));
        int reads = scene.blockReads();
        assertTrue(space.clearSurface(new Vector(0.5, 64.8, 2.5),
                new Vector(1, 0, 0), new Vector(0, 1, 0), 0.3, 0.15, 0.04));
        assertEquals(reads, scene.blockReads());
    }

    @Test
    void distinguishesAnAngledSurfaceFromItsLargerAxisAlignedEnvelope() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        scene.block(0, 64, 0, new BoundingBox(0.82, 0, 0.82, 0.95, 1, 0.95));
        double diagonal = Math.sqrt(0.5);
        assertTrue(new FloatingMenuWorldSpace(scene.world()).clearSurface(new Vector(0.5, 64.5, 0.5),
                new Vector(diagonal, 0, -diagonal), new Vector(0, 1, 0), 0.65, 0.3, 0.04));
    }

    @Test
    void neverReadsBlocksOrTracesIntoUnloadedChunks() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        scene.loaded((chunkX, chunkZ) -> chunkX == 0 && chunkZ == 0);
        var world = scene.world();
        FloatingMenuWorldSpace space = new FloatingMenuWorldSpace(world);
        assertFalse(space.clearSurface(new Vector(16.5, 64.5, 0.5),
                new Vector(1, 0, 0), new Vector(0, 1, 0), 0.2, 0.2, 0.04));
        assertEquals(0, scene.blockReads());
        assertTrue(space.firstHit(new Location(world, 15.5, 64.5, 0.5), new Vector(1, 0, 0), 3) < 0.5);
    }

    @Test
    void anUnloadedStartingChunkNeverInvokesTheNativeRayTracer() {
        FloatingMenuTestWorld scene = new FloatingMenuTestWorld();
        scene.loaded((chunkX, chunkZ) -> false);
        var world = scene.world();
        assertEquals(0, new FloatingMenuWorldSpace(world).firstHit(
                new Location(world, 0.5, 64.5, 0.5), new Vector(0, 0, 1), 3));
        assertEquals(0, scene.rayReads());
    }
}
