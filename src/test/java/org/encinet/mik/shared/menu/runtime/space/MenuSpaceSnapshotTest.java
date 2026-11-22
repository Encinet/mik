package org.encinet.mik.shared.menu.runtime.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuSpaceSnapshotTest {
    private static final Location ORIGIN = new Location(null, 0, 0, 0);

    @Test void indexedQueriesMatchTheLinearGeometryWithRotatedModelsAndReusedTerrain() {
        var random = new Random(484818);
        var offset = new Vector(-.3, .4, .25);
        var blocks = new ArrayList<BoundingBox>();
        var models = new ArrayList<MenuOcclusionGeometry.Box>();
        for (int shape = 0; shape < 70; shape++) {
            double right = random.nextDouble(-5, 5), up = random.nextDouble(-3, 3), forward = random.nextDouble(1, 9);
            blocks.add(new BoundingBox(right, up, forward, right + .45, up + .7, forward + .25));
            models.add(MenuModelGeometry.transform(new BoundingBox(-.25, -.12, -.09, .25, .12, .09),
                    new Matrix4d().translation(right, up, forward).rotateY(random.nextDouble(0, Math.PI)).rotateZ(.4), 20));
        }
        var linear = new ArrayList<>(models);
        blocks.forEach(block -> linear.add(MenuOcclusionGeometry.box(block, offset)));
        var indexed = new MenuSpaceSnapshot(new BoundingBox(-16, -16, -16, 16, 16, 16), blocks).at(offset, models);
        int hits = 0, clipped = 0;
        for (int ray = 0; ray < 120; ray++) {
            var center = new Vector(random.nextDouble(-4, 4), random.nextDouble(-2, 2), 8);
            var direction = center.clone().normalize();
            double expected = MenuOcclusionGeometry.firstHit(new Vector(), direction, 10, linear);
            assertEquals(expected, indexed.firstHit(ORIGIN, direction, 10), 1e-8);
            if (Double.isFinite(expected)) hits++;
            var right = new Vector(1, 0, 0).rotateAroundY(.35);
            var up = new Vector(0, 1, 0);
            double linearLimit = MenuOcclusionGeometry.limit(center, right, up, .36, .23, .08, linear);
            assertEquals(linearLimit, indexed.limitSurface(ORIGIN, center, right, up, .36, .23, .08), 1e-8);
            if (linearLimit < 1) clipped++;
        }
        assertTrue(hits > 0 && clipped > 0);
    }

    @Test void lookingAlongOrAwayFromATerrainBoundaryDoesNotLookInsideTheObstacle() {
        var space = new MenuSpaceSnapshot(new BoundingBox(-4, -4, -4, 4, 4, 4),
                List.of(new BoundingBox(-1, -1, -1, 0, 1, 1)));
        assertEquals(Double.POSITIVE_INFINITY, space.firstHit(ORIGIN, new Vector(1, 0, 0), 3));
        assertEquals(Double.POSITIVE_INFINITY, space.firstHit(ORIGIN, new Vector(0, 0, 1), 3));
        assertEquals(0, space.firstHit(ORIGIN, new Vector(-1, 0, 0), 3));
    }

    @Test void sampledRayEndpointsAcceptTheBoundaryButNotUnsampledSpace() {
        var region = new BoundingBox(-4, -4, -4, 4, 4, 4);
        var space = new MenuSpaceSnapshot(region, List.of(new BoundingBox(-1, -1, 2, 1, 1, 3)));
        var forward = new Vector(0, 0, 1);
        assertEquals(2, space.firstHit(ORIGIN, forward, 4));
        assertEquals(2, space.firstHit(ORIGIN, forward, Math.nextUp(4.0)));
        assertEquals(0, space.firstHit(ORIGIN, forward, 4.0001));
        assertEquals(Double.POSITIVE_INFINITY, space.firstHit(ORIGIN, new Vector(0, 0, -1), 4));
    }
}
