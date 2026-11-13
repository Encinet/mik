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
        MonitorCamera.Frame frame = camera.update(UUID.randomUUID(), targetEye, EYE_HEIGHT, targetEye, 0.9);
        assertNotNull(frame);
        Location feet = frame.feet();
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
    void fastThreeDimensionalFlightDoesNotAccumulateLagAndKeepsTheSubjectCentered() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        Vector offset = eyes(observer).toVector().subtract(eye.toVector());
        for (int index = 0; index < 100; index++) {
            eye.add(index < 50 ? 3 : -3, index < 50 ? 2 : -2, index < 50 ? 4 : -4);
            observer = view(camera, target, eye, eyes(observer));
            Vector relative = eyes(observer).toVector().subtract(eye.toVector());
            assertTrue(relative.length() < 9.3, "speed adaptation must stay bounded instead of accumulating lag");
            assertTrue(relative.dot(offset) > 0, "ascent, descent and reversals must preserve the observation side");
            Vector focus = eye.toVector().subtract(new Vector(0, 0.4, 0));
            assertEquals(1, observer.getDirection().dot(focus.subtract(eyes(observer).toVector()).normalize()), 1.0e-6,
                    "the view must aim at the subject immediately instead of converging from an old rotation");
        }
    }

    @Test
    void lowCeilingsSelectAViewBelowTheRoof() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(-100, 66.2, -100, 100, 67, 100));
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertTrue(eyes(observer).getY() < 66.2 - 0.25, () -> "camera eye must stay below the roof: " + eyes(observer));
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
        assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT));
        scene.solids.clear();
        for (int index = 0; index < 19; index++) assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT));
        assertNotNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT));
        scene.enclose(8, 65.62, 8);
        assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT));
        scene.solids.clear();
        assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT), "one clear frame must not flicker back");
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
        assertNull(scene.camera().update(UUID.randomUUID(), eye, EYE_HEIGHT, eye, EYE_HEIGHT));
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

    @Test
    void veryFastFlightKeepsItsSideEvenWhenThePlayerTurnsAndReverses() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        Vector offset = eyes(observer).toVector().subtract(eye.toVector());
        for (int tick = 0; tick < 12; tick++) {
            eye.add(tick < 6 ? 20 : -20, 0, tick < 6 ? 20 : -20);
            eye.setYaw(tick % 2 == 0 ? 180 : 0);
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT);
            assertNotNull(frame);
            assertFalse(frame.cut(), "speed alone must not cut or reset the observation heading");
            observer = frame.feet();
            Vector relative = eyes(observer).toVector().subtract(eye.toVector());
            assertTrue(relative.length() < 9.3);
            assertTrue(relative.dot(offset) > 0, "turning the player's head at high speed must not change observation side");
        }
    }

    @Test
    void aBriefCrampedViewDoesNotSwitchSidesButASustainedOneDoes() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        BoundingBox obstruction = new BoundingBox(10, 60, -100, 10.2, 80, 100);
        for (int tick = 0; tick < 20; tick++) {
            scene.solids.clear();
            if (tick % 2 == 0) scene.solids.add(obstruction);
            observer = view(camera, target, eye, eyes(observer));
            assertTrue(observer.getX() > eye.getX(), "intermittent obstructions must not make the view alternate sides");
        }
        scene.solids.add(obstruction);
        for (int tick = 0; tick < 4; tick++) {
            observer = view(camera, target, eye, eyes(observer));
            assertTrue(observer.getX() > eye.getX(), "a usable current view should wait for a stable improvement");
        }
        for (int tick = 0; tick < 12; tick++) observer = view(camera, target, eye, eyes(observer));
        assertTrue(eyes(observer).distance(eye) > 3.5, "a sustained better view must eventually be selected");
    }

    @Test
    void losingTheCurrentViewSwitchesImmediatelyWithoutWaitingForTheStabilityWindow() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        scene.solids.add(new BoundingBox(8.5, 60, -100, 8.7, 80, 100));
        MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT);
        assertNotNull(frame);
        assertTrue(frame.feet().getX() < 8.5);
        assertClear(scene, eye.clone().subtract(0, 0.4, 0), eyes(frame.feet()));
        assertTrue(frame.cut(), "an occluded old view must not interpolate across the obstruction");
    }

    @Test
    void checksTheBodyFocusEvenWhenTheHeadCanSeeOverAnObstruction() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(8.3, 60, -100, 8.5, 65.55, 100));
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(scene.camera(), UUID.randomUUID(), eye, eye);
        assertClear(scene, eye.clone().subtract(0, 0.4, 0), eyes(observer));
        assertTrue(observer.getX() <= eye.getX());
    }

    @Test
    void swimmingFocusStaysInsideTheBodyInsteadOfBelowThePlayer() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.4, 8);
        MonitorCamera.Frame frame = scene.camera().update(UUID.randomUUID(), eye, 0.4, eye, EYE_HEIGHT);
        assertNotNull(frame);
        Vector focus = eye.toVector().subtract(new Vector(0, 0.1, 0));
        assertEquals(1, frame.feet().getDirection().dot(focus.subtract(eyes(frame.feet()).toVector()).normalize()), 1.0e-6);
    }

    @Test
    void followingInsideAContinuousRoofDoesNotCutAtInternalBlockBoundaries() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(-100, 66, -100, 100, 68, 100));
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        for (int tick = 0; tick < 40; tick++) {
            eye.add(0.15, 0, 0);
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT);
            assertNotNull(frame);
            assertFalse(frame.cut(), "adjoining solid blocks must share one continuous wall interior");
            observer = frame.feet();
            assertTrue(scene.solidAt(eyes(observer)));
        }
    }

    @Test
    void aClearDestinationCutsWhenTheIntermediateViewWouldBeOccluded() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        view(camera, target, eye, eye);
        scene.solids.add(new BoundingBox(10, 60, 5, 10.2, 80, 6.3));
        Location current = new Location(scene.world, 8, 66.82, 4);
        boolean cut = false;
        for (int tick = 0; tick < 20; tick++) {
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, current, EYE_HEIGHT);
            assertNotNull(frame);
            Location next = eyes(frame.feet());
            if (frame.cut()) cut = true;
            else {
                for (int sample = 0; sample <= 20; sample++) {
                    Location between = current.clone().add(next.toVector().subtract(current.toVector()).multiply(sample / 20.0));
                    assertClear(scene, eye.clone().subtract(0, 0.4, 0), between);
                }
            }
            current = next;
        }
        assertTrue(cut, "unsafe intermediate views require a real camera cut");
        assertTrue(current.getX() > 10.2);
    }

    @Test
    void continuousFramesKeepTheInterpolatedAimOnTheSubjectDuringALargeReframe() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location initial = eyes(view(camera, target, eye, eye));
        Location focus = eye.clone().subtract(0, 0.4, 0);
        Location current = eye.clone().subtract(initial.toVector().subtract(eye.toVector()));
        current.setY(initial.getY());
        current.setDirection(focus.toVector().subtract(current.toVector()));
        boolean cut = false;
        for (int tick = 0; tick < 20; tick++) {
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, current, EYE_HEIGHT);
            assertNotNull(frame);
            Location next = eyes(frame.feet());
            if (frame.cut()) cut = true;
            else {
                for (int sample = 0; sample <= 20; sample++) {
                    double fraction = sample / 20.0;
                    Location between = current.clone().add(next.toVector().subtract(current.toVector()).multiply(fraction));
                    between.setYaw((float) (current.getYaw() + Math.IEEEremainder(next.getYaw() - current.getYaw(), 360) * fraction));
                    between.setPitch((float) (current.getPitch() + (next.getPitch() - current.getPitch()) * fraction));
                    double aim = between.getDirection().dot(focus.toVector().subtract(between.toVector()).normalize());
                    assertTrue(aim >= Math.cos(Math.toRadians(12)), "interpolating the camera must keep the player near the center");
                }
            }
            current = next;
        }
        assertTrue(cut, "a large reframe over the subject cannot safely interpolate every rotation");
    }

    @Test
    void sustainedSpeedGraduallyWidensTheViewAndStoppingQuicklyRestoresNormalFraming() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        Vector normalOffset = eyes(observer).toVector().subtract(eye.toVector());
        Vector previousOffset = normalOffset.clone();
        for (int tick = 0; tick < 35; tick++) {
            eye.add(3, 0, 0);
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT);
            assertNotNull(frame);
            assertFalse(frame.cut(), "accelerating in an open area should widen the view continuously");
            observer = frame.feet();
            Vector offset = eyes(observer).toVector().subtract(eye.toVector());
            assertTrue(offset.distance(previousOffset) < 0.8, "zoom changes must not jump the relative camera position");
            assertTrue(offset.length() < 9.3);
            previousOffset = offset;
        }
        assertTrue(previousOffset.length() > normalOffset.length() + 2, "sustained fast travel needs a wider view");
        for (int tick = 0; tick < 10; tick++) observer = view(camera, target, eye, eyes(observer));
        Vector stopped = eyes(observer).toVector().subtract(eye.toVector());
        assertTrue(stopped.distance(normalOffset) < 0.15, "stopping must retract both zoom and predictive offset promptly");
    }

    @Test
    void compensationWorksInBothDirectionsWhileKeepingThePlayerCentered() {
        Vector[] offsets = new Vector[2];
        for (int direction = 0; direction < 2; direction++) {
            Scene scene = new Scene();
            MonitorCamera camera = scene.camera();
            UUID target = UUID.randomUUID();
            Location eye = new Location(scene.world, 8, 65.62, 8);
            Location observer = view(camera, target, eye, eye);
            for (int tick = 0; tick < 40; tick++) {
                eye.add(direction == 0 ? 3 : -3, 0, 0);
                observer = view(camera, target, eye, eyes(observer));
                Vector focus = eye.toVector().subtract(new Vector(0, 0.4, 0));
                assertEquals(1, observer.getDirection().dot(focus.subtract(eyes(observer).toVector()).normalize()), 1.0e-6);
            }
            offsets[direction] = eyes(observer).toVector().subtract(eye.toVector());
        }
        assertTrue(offsets[0].getX() - offsets[1].getX() > 1.8,
                "compensation must lead motion toward and away from the fixed observation side");
        assertEquals(offsets[0].getZ(), offsets[1].getZ(), 1.0e-6);
    }

    @Test
    void speedExpansionAndCompensationRespectNarrowPassages() {
        Scene scene = new Scene();
        scene.solids.add(new BoundingBox(-100, 60, 5, 100, 80, 5.2));
        scene.solids.add(new BoundingBox(-100, 60, 10.8, 100, 80, 11));
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        for (int tick = 0; tick < 25; tick++) {
            eye.add(2, 0, 0);
            observer = view(camera, target, eye, eyes(observer));
            assertTrue(eyes(observer).getZ() > 5.2 && eyes(observer).getZ() < 10.8);
            assertTrue(eyes(observer).distance(eye) < 9.3);
            assertClear(scene, eye.clone().subtract(0, 0.4, 0), eyes(observer));
        }
    }

    @Test
    void switchingSubjectsAndExplicitResetDiscardTheOldTargetsSpeed() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        for (int tick = 0; tick < 25; tick++) {
            eye.add(3, 1, 0);
            observer = view(camera, target, eye, eyes(observer));
        }
        assertTrue(eyes(observer).distance(eye) > 6);
        MonitorCamera.Frame switched = camera.update(UUID.randomUUID(), eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT);
        assertNotNull(switched);
        assertTrue(switched.cut());
        assertTrue(eyes(switched.feet()).distance(eye) < 5);
        for (int tick = 0; tick < 25; tick++) {
            eye.add(3, 1, 0);
            observer = view(camera, target, eye, eyes(observer));
        }
        camera.reset();
        MonitorCamera.Frame reset = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT);
        assertNotNull(reset);
        assertTrue(reset.cut());
        assertTrue(eyes(reset.feet()).distance(eye) < 5, "teleport reset must not reuse pre-teleport prediction");
    }

    @Test
    void blockedPredictionUsesTheUncompensatedWideView() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        for (int tick = 0; tick < 40; tick++) {
            eye.add(4, 0, 0);
            observer = view(camera, target, eye, eyes(observer));
        }
        eye.add(4, 0, 0);
        double nominalX = eye.getX() + Math.sin(Math.toRadians(70)) * 8;
        scene.solids.add(new BoundingBox(nominalX + 0.2, 60, -100, nominalX + 0.4, 80, 100));
        observer = view(camera, target, eye, eyes(observer));
        assertTrue(eyes(observer).getX() <= nominalX + 0.01, "prediction must not place the view across the approaching wall");
        Vector offset = eyes(observer).toVector().subtract(eye.toVector());
        assertTrue(Math.hypot(offset.getX(), offset.getZ()) > 7.99,
                "blocked prediction should preserve the available wide base view instead of shortening it");
        assertClear(scene, eye.clone().subtract(0, 0.4, 0), eyes(observer));
    }

    @Test
    void expandingNearAnUnloadedChunkNeverReadsOrTracesIntoIt() {
        Scene scene = new Scene();
        for (int z = -1; z <= 4; z++) scene.unloaded.add(new Chunk(1, z));
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8.5, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        for (int tick = 0; tick < 15; tick++) {
            eye.add(0, 0, 3);
            observer = view(camera, target, eye, eyes(observer));
            assertTrue(eyes(observer).getX() + 0.35 < 16, "expansion must leave a loaded margin around the camera");
            assertClear(scene, eye.clone().subtract(0, 0.4, 0), eyes(observer));
        }
        verify(scene.world, never()).getChunkAt(anyInt(), anyInt());
    }

    private static Location view(MonitorCamera camera, UUID target, Location eye, Location observerEye) {
        MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, observerEye, EYE_HEIGHT);
        assertNotNull(frame, "a clear nearby third-person view should be available");
        return frame.feet();
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
