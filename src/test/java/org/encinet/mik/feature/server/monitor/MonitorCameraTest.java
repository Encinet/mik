package org.encinet.mik.feature.server.monitor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonitorCameraTest {
    private static final double EYE_HEIGHT = 1.62;

    @Test
    void standingHeadTurnsDoNotOrbitTheCamera() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location first = view(camera, target, eye, eye);
        eye.setYaw(180);
        eye.setPitch(75);
        Location second = view(camera, target, eye, eyes(first));
        assertEquals(first.toVector(), second.toVector());
        assertTrue(Math.abs(second.getYaw() - first.getYaw()) < 20);
        assertTrue(Math.abs(second.getPitch() - first.getPitch()) < 20);
    }

    @Test
    void aimsFromTheObserversActualEyeHeightAtThePlayersTorso() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        Location targetEye = new Location(scene.world, 8, 65.27, 8, 45, 50);
        Location feet = camera.update(UUID.randomUUID(), targetEye, targetEye, 0.9);
        assertNotNull(feet);
        Vector focus = targetEye.toVector().subtract(new Vector(0, 0.4, 0));
        Vector expected = focus.subtract(feet.toVector().add(new Vector(0, 0.9, 0))).normalize();
        assertEquals(1, feet.getDirection().dot(expected), 1.0e-6);
    }

    @Test
    void movingPlayersKeepTheSameObservationSideWithBoundedSteps() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        Vector offset = eyes(observer).toVector().subtract(eye.toVector());
        for (int index = 0; index < 30; index++) {
            eye.add(0.2, 0, 0);
            Location next = view(camera, target, eye, eyes(observer));
            assertTrue(next.distance(observer) < 1, "ordinary movement must not jump the camera");
            assertTrue(eyes(next).distance(eye) < 6);
            observer = next;
        }
        assertEquals(0, offset.distance(eyes(observer).toVector().subtract(eye.toVector())), 1.0e-8,
                "walking must translate the view without changing the observation side");
    }

    @Test
    void rapidStrafingAndHeadTurnsDoNotOrbitOrShakeTheView() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        Vector offset = eyes(observer).toVector().subtract(eye.toVector());
        float yaw = observer.getYaw();
        float pitch = observer.getPitch();
        for (int index = 0; index < 100; index++) {
            eye.add(index % 2 == 0 ? 0.25 : -0.25, 0, 0);
            eye.setYaw(index % 2 == 0 ? 90 : -90);
            eye.setPitch(index % 2 == 0 ? 80 : -80);
            observer = view(camera, target, eye, eyes(observer));
            assertEquals(0, offset.distance(eyes(observer).toVector().subtract(eye.toVector())), 1.0e-8);
            assertEquals(yaw, observer.getYaw(), 1.0e-4);
            assertEquals(pitch, observer.getPitch(), 1.0e-4);
        }
    }

    @Test
    void lowCeilingsSelectAViewBelowTheRoof() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(-100, 66.2, -100, 100, 67, 100));
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(eyes(observer).getY() < 66.2 - 0.25);
        assertClear(scene, eye, eyes(observer));
    }

    @Test
    void aFullBlockRoofCanContainTheCameraEyeWhileItsFeetRemainOutside() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(-100, 66, -100, 100, 68, 100));
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(scene.solidAt(eyes(observer)));
        assertFalse(scene.solidAt(observer), "visibility depends on the camera eye, not the feet");
        assertNotNull(scene.trace(eye.toVector(), eyes(observer).toVector().subtract(eye.toVector()).normalize(), 6));
    }

    @Test
    void aWallBehindThePlayerMovesTheCameraToAClearSide() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(-100, 60, 5.5, 100, 80, 6.5));
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(eyes(observer).getZ() > 6.5 + 0.25);
        assertClear(scene, eye, eyes(observer));
    }

    @Test
    void relocatingMayCrossAWallWhileTheNewViewKeepsThePlayerVisible() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        view(camera, target, eye, eye);
        scene.solids.add(new BoundingBox(10, 60, 5, 10.2, 80, 6.3));
        Location observerEye = new Location(scene.world, 8, 66.82, 4);
        boolean crossedWall = false;
        for (int index = 0; index < 20; index++) {
            Location nextEye = eyes(view(camera, target, eye, observerEye));
            Vector travel = nextEye.toVector().subtract(observerEye.toVector());
            if (travel.lengthSquared() > 1.0e-8 && scene.trace(observerEye.toVector(),
                    travel.clone().normalize(), travel.length()) != null) crossedWall = true;
            assertClear(scene, eye, nextEye);
            observerEye = nextEye;
        }
        assertTrue(crossedWall, "a wall on the travel path must not trap the spectator on the old side");
        assertTrue(observerEye.getX() > 10.2);
    }

    @Test
    void confinedSpacesKeepThirdPersonInsideTheWallRatherThanInAirBeyondIt() {
        Scene scene = new Scene();
        scene.encloseWithBlocks();
        Location eye = new Location(scene.world, 8.5, 65.62, 8.5);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(scene.solidAt(eyes(observer)), "a blocked view must put the actual camera eye inside the wall");
        assertNotNull(scene.trace(eye.toVector(), eyes(observer).toVector().subtract(eye.toVector()).normalize(), 6));
    }

    @Test
    void followingKeepsTheCameraEyeInTheWallWhenTheAirBeyondItIsOccluded() {
        Scene scene = new Scene();
        scene.encloseWithBlocks();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8.5, 65.62, 8.5);
        Location observer = view(camera, target, eye, eye);
        for (int index = 0; index < 20; index++) {
            eye.add(0.015, 0, 0);
            observer = view(camera, target, eye, eyes(observer));
            assertTrue(scene.solidAt(eyes(observer)), "smoothing must not drift into an occluded air pocket");
        }
    }

    @Test
    void aSeparateWallBeyondAnAirPocketDoesNotCountAsSeeingThePlayer() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(9, 60, -100, 10, 80, 100));
        scene.solids.add(new BoundingBox(11, 60, -100, 14, 80, 100));
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(eyes(observer).getX() < 11, "being inside a distant wall does not hide a separate intervening wall");
    }

    @Test
    void nonFullRenderObstructionsUseFirstPersonAndRequireSustainedRecovery() {
        Scene scene = new Scene();
        scene.occluding = false;
        scene.enclose(8, 65.62, 8);
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        assertNull(camera.update(target, eye, eye, EYE_HEIGHT));
        scene.solids.clear();
        for (int index = 0; index < 19; index++) assertNull(camera.update(target, eye, eye, EYE_HEIGHT));
        assertNotNull(camera.update(target, eye, eye, EYE_HEIGHT));
        scene.enclose(8, 65.62, 8);
        assertNull(camera.update(target, eye, eye, EYE_HEIGHT));
        scene.solids.clear();
        assertNull(camera.update(target, eye, eye, EYE_HEIGHT), "one clear frame must not flicker back");
    }

    @Test
    void unloadedChunksAlongDiagonalRaysAreExcludedBeforeTracing() {
        Scene scene = new Scene();
        scene.unloaded.add(new Chunk(1, 0));
        Location eye = new Location(scene.world, 15.6, 65.62, 15.6);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(scene.rays > 0);
        assertClear(scene, eye, eyes(observer));
        verify(scene.world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void unloadedMarginAtTheTargetUsesNativeViewWithoutRayTracing() {
        Scene scene = new Scene();
        scene.unloaded.add(new Chunk(1, 0));
        scene.unloaded.add(new Chunk(0, 1));
        Location eye = new Location(scene.world, 15.8, 65.62, 15.8);
        assertNull(scene.camera().update(UUID.randomUUID(), eye, eye, EYE_HEIGHT));
        assertEquals(0, scene.rays);
    }

    @Test
    void cameraPositionsStayInsideTheWorldBorder() {
        Scene scene = new Scene();
        scene.borderMaxX = 10;
        Location eye = new Location(scene.world, 9.5, 65.62, 8);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(eyes(observer).getX() <= 10);
    }

    @Test
    void teleportsAndNewTargetsCutDirectlyToTheNewSubject() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        eye = new Location(scene.world, 200, 100, 200);
        observer = view(camera, target, eye, eyes(observer));
        assertTrue(eyes(observer).distance(eye) < 6);
        eye = new Location(scene.world, 205, 100, 200, 180, 0);
        observer = view(camera, UUID.randomUUID(), eye, eyes(observer));
        assertTrue(observer.getZ() > eye.getZ(), "a new target starts with its own heading");
        assertTrue(eyes(observer).distance(eye) < 6);
    }

    @Test
    void rotationUsesTheShortArcAcrossTheYawBoundary() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        observer.setYaw(observer.getYaw() + 359);
        Location next = view(camera, target, eye, eyes(observer));
        assertTrue(Math.abs(next.getYaw() - observer.getYaw()) < 2);
    }

    private static Location view(MonitorCamera camera, UUID target, Location eye, Location observerEye) {
        Location feet = camera.update(target, eye, observerEye, EYE_HEIGHT);
        assertNotNull(feet, "a clear nearby third-person view should be available");
        return feet;
    }

    private static Location eyes(Location feet) { return feet.clone().add(0, EYE_HEIGHT, 0); }

    private static void assertClear(Scene scene, Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        assertNull(scene.trace(from.toVector(), delta.clone().normalize(), delta.length()));
    }

    private record Chunk(int x, int z) { }

    private static final class Scene {
        final World world = mock(World.class);
        final List<BoundingBox> solids = new ArrayList<>();
        final Set<Chunk> unloaded = new HashSet<>();
        double borderMaxX = 1000;
        int rays;

        Scene() {
            WorldBorder border = mock(WorldBorder.class);
            when(world.getWorldBorder()).thenReturn(border);
            when(border.isInside(any(Location.class))).thenAnswer(call -> ((Location) call.getArgument(0)).getX() <= borderMaxX);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(call ->
                    !unloaded.contains(new Chunk(call.getArgument(0), call.getArgument(1))));
            when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
                    eq(FluidCollisionMode.NEVER), eq(true))).thenAnswer(call -> {
                Location from = call.getArgument(0);
                Vector direction = call.getArgument(1);
                double length = call.getArgument(2);
                Vector to = from.toVector().add(direction.clone().multiply(length));
                for (int x = Math.min(from.getBlockX() >> 4, (int) Math.floor(to.getX() / 16));
                     x <= Math.max(from.getBlockX() >> 4, (int) Math.floor(to.getX() / 16)); x++) {
                    for (int z = Math.min(from.getBlockZ() >> 4, (int) Math.floor(to.getZ() / 16));
                         z <= Math.max(from.getBlockZ() >> 4, (int) Math.floor(to.getZ() / 16)); z++) {
                        assertFalse(unloaded.contains(new Chunk(x, z)), "ray must not reach an unloaded chunk");
                    }
                }
                rays++;
                return trace(from.toVector(), direction, length);
            });
        }

        RayTraceResult trace(Vector start, Vector direction, double length) {
            RayTraceResult nearest = null;
            double distance = Double.POSITIVE_INFINITY;
            for (BoundingBox solid : solids) {
                RayTraceResult hit = solid.rayTrace(start, direction, length);
                if (hit != null && hit.getHitPosition().distanceSquared(start) < distance) {
                    nearest = hit;
                    distance = hit.getHitPosition().distanceSquared(start);
                }
            }
            return nearest;
        }

        void enclose(double x, double y, double z) {
            solids.add(new BoundingBox(x - 2, y - 2, z - 2, x - 0.8, y + 3, z + 2));
            solids.add(new BoundingBox(x + 0.8, y - 2, z - 2, x + 2, y + 3, z + 2));
            solids.add(new BoundingBox(x - 2, y - 2, z - 2, x + 2, y + 3, z - 0.8));
            solids.add(new BoundingBox(x - 2, y - 2, z + 0.8, x + 2, y + 3, z + 2));
        }

        boolean occluding = true;

        MonitorCamera camera() { return new MonitorCamera(this::solidAt); }

        boolean solidAt(Location eye) {
            assertFalse(unloaded.contains(new Chunk(eye.getBlockX() >> 4, eye.getBlockZ() >> 4)),
                    "visibility queries must not read unloaded blocks");
            if (!occluding) return false;
            int x = eye.getBlockX();
            int y = eye.getBlockY();
            int z = eye.getBlockZ();
            return solids.stream().anyMatch(box -> box.getMinX() <= x && box.getMaxX() >= x + 1
                    && box.getMinY() <= y && box.getMaxY() >= y + 1
                    && box.getMinZ() <= z && box.getMaxZ() >= z + 1);
        }

        void encloseWithBlocks() {
            solids.add(new BoundingBox(7, 60, 7, 8, 70, 10));
            solids.add(new BoundingBox(9, 60, 7, 10, 70, 10));
            solids.add(new BoundingBox(7, 60, 7, 10, 70, 8));
            solids.add(new BoundingBox(7, 60, 9, 10, 70, 10));
        }
    }
}
