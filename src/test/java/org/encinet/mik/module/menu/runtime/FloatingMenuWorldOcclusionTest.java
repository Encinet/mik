package org.encinet.mik.module.menu.runtime;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuWorldOcclusionTest {
    @Test
    void solidBlockBeforeRingCardRejectsTheClick() {
        AtomicReference<Double> blockDistance = new AtomicReference<>(1.0);
        Location eye = new Location(world(blockDistance), 0, 64, 0);

        assertFalse(FloatingMenuWorldOcclusion.clearToSurface(eye, 2.0));

        blockDistance.set(2.2);
        assertTrue(FloatingMenuWorldOcclusion.clearToSurface(eye, 2.0));
    }

    @Test
    void blockAtTheSurfaceDoesNotMaskTheMenu() {
        Location eye = new Location(world(new AtomicReference<>(2.0)), 0, 64, 0);

        assertTrue(FloatingMenuWorldOcclusion.clearToSurface(eye, 2.0));
    }

    private static World world(AtomicReference<Double> firstHitDistance) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "rayTraceBlocks" -> {
                        assertEquals(FluidCollisionMode.NEVER, args[3]);
                        assertEquals(false, args[4]);
                        double distance = firstHitDistance.get();
                        if (distance > (double) args[2]) yield null;
                        Location start = (Location) args[0];
                        Vector direction = ((Vector) args[1]).clone().normalize();
                        yield new RayTraceResult(start.toVector().add(
                                direction.multiply(distance)));
                    }
                    case "isChunkLoaded" -> true;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "OcclusionTestWorld";
                    default -> null;
                });
    }
}
