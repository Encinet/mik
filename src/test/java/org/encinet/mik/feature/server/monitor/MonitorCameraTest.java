package org.encinet.mik.feature.server.monitor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.encinet.mik.shared.util.PaperTestRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonitorCameraTest {
    private static final double EYE_HEIGHT = 1.62;
    private static PaperTestRegistries registries;
    @BeforeAll static void openRegistries() { registries = new PaperTestRegistries(); }
    @AfterAll static void closeRegistries() { registries.close(); }

    @Test
    void actualInteractionSurfacesAndWholeObjectsStayVisible() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        Block block = scene.block(new BoundingBox(7.5, 65, 12, 8.5, 66, 13));
        MonitorOperation.Focus operation = new MonitorOperation().sample(player);
        assertNotNull(operation);
        assertEquals(block.getBoundingBox(), operation.bounds());
        assertEquals(11.96, operation.point().getZ(), 1.0e-6, "focus must stay just outside the actual worked face");
        MonitorCamera.Frame frame = scene.camera().update(player, eye, EYE_HEIGHT);
        assertNotNull(frame);
        assertJointView(scene, eyes(frame.feet()), operation);
    }

    @Test
    void aWallThatOnlyHidesTheOperationInvalidatesTheOldViewAndCutsToAClearOne() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        scene.block(new BoundingBox(7.5, 65, 12, 8.5, 66, 13));
        MonitorOperation.Focus operation = new MonitorOperation().sample(player);
        assertNotNull(operation);
        MonitorCamera camera = scene.camera();
        MonitorCamera.Frame initial = camera.update(player, eye, EYE_HEIGHT);
        assertNotNull(initial);
        Location oldEye = eyes(initial.feet());
        scene.solids.add(new BoundingBox(9.5, 60, 9.8, 9.7, 70, 11.8));
        assertClear(scene, oldEye, eye.clone().subtract(0, 0.4, 0));
        Vector delta = operation.point().toVector().subtract(oldEye.toVector());
        assertNotNull(scene.trace(oldEye.toVector(), delta.clone().normalize(), delta.length()));
        MonitorCamera.Frame frame = camera.update(player, oldEye, EYE_HEIGHT);
        assertNotNull(frame);
        assertTrue(frame.cut(), "the spring must not coast through a view that hides the interaction surface");
        assertJointView(scene, eyes(frame.feet()), operation);
    }

    @Test
    void thePlayersBodyCannotHideTheWorkedSurface() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8, -70, 25);
        Player player = scene.player(eye);
        Block block = scene.block(new BoundingBox(7.5, 63, 10.5, 8.5, 64, 11.5));
        MonitorOperation operations = new MonitorOperation();
        operations.block(player, block, new Location(scene.world, 8, 64, 11), BlockFace.UP);
        MonitorOperation.Focus operation = operations.sample(player);
        assertNotNull(operation);
        MonitorCamera.Frame frame = scene.camera().update(player.getUniqueId(), eye, EYE_HEIGHT, eye, EYE_HEIGHT, operation);
        assertNotNull(frame);
        assertTrue(Math.abs(frame.feet().getX() - eye.getX()) > 1,
                "a rear view that puts the player in front of the work must give way to a side view");
        assertJointView(scene, eyes(frame.feet()), operation);
    }

    @Test
    void extendedReachFramesTheRealObjectInsteadOfTheOldThreeBlockEstimate() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        AttributeInstance reach = mock(AttributeInstance.class);
        when(reach.getValue()).thenReturn(16.0);
        when(player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)).thenReturn(reach);
        scene.block(new BoundingBox(7.5, 65, 20, 8.5, 66, 21));
        MonitorOperation.Focus operation = new MonitorOperation().sample(player);
        assertNotNull(operation);
        assertEquals(19.96, operation.point().getZ(), 1.0e-6);
        MonitorCamera.Frame frame = scene.camera().update(player, eye, EYE_HEIGHT);
        assertNotNull(frame);
        assertJointView(scene, eyes(frame.feet()), operation);
    }

    @Test
    void entitySensingRespectsBlockingWallsAndItsOwnInteractionRange() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        AttributeInstance reach = mock(AttributeInstance.class);
        when(reach.getValue()).thenReturn(8.0);
        when(player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)).thenReturn(reach);
        Entity entity = scene.entity(new BoundingBox(7.7, 64, 14, 8.3, 66, 14.6));
        MonitorOperation operations = new MonitorOperation();
        MonitorOperation.Focus target = operations.sample(player);
        assertNotNull(target);
        assertEquals(entity.getBoundingBox(), target.bounds());
        Block wall = scene.block(new BoundingBox(7.5, 64, 10, 8.5, 67, 11));
        target = operations.sample(player);
        assertNotNull(target);
        assertEquals(wall.getBoundingBox(), target.bounds(), "an entity beyond the blocking wall is not the operation target");
    }

    @Test
    void recentInteractionsFollowMovingEntitiesThenExpire() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        Entity entity = scene.entity(new BoundingBox(8.5, 64, 10, 9.1, 66, 10.6));
        MonitorOperation operations = new MonitorOperation();
        operations.entity(player, entity);
        when(entity.getBoundingBox()).thenReturn(new BoundingBox(9, 64, 10, 9.6, 66, 10.6));
        for (int tick = 0; tick < 12; tick++) {
            MonitorOperation.Focus focus = operations.sample(player);
            assertNotNull(focus);
            assertEquals(entity.getBoundingBox(), focus.bounds());
            assertTrue(focus.point().getX() >= 9 && focus.point().getX() <= 9.6);
        }
        assertNull(operations.sample(player), "old interactions must not pin the camera after the player looks elsewhere");
        operations.entity(player, entity);
        operations.reset();
        assertNull(operations.sample(player), "teleporting or changing subjects must discard the old work");
    }

    @Test
    void operationSensingStopsAtUnloadedBoundaries() {
        Scene scene = new Scene();
        scene.unloaded.add(new Chunk(1, 0));
        Location eye = new Location(scene.world, 15.5, 65.62, 8, -90, 0);
        Player player = scene.player(eye);
        assertNull(new MonitorOperation().sample(player));
        verify(scene.world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void realSurfacesAboveAndBelowThePlayerStayInFrame() {
        for (boolean above : new boolean[]{false, true}) {
            Scene scene = new Scene();
            Location eye = new Location(scene.world, 8, 65.62, 8, 0, above ? -80 : 80);
            Player player = scene.player(eye);
            scene.block(new BoundingBox(7.5, above ? 69 : 61, 8, 8.5, above ? 70 : 62, 9));
            MonitorOperation.Focus operation = new MonitorOperation().sample(player);
            assertNotNull(operation);
            MonitorCamera.Frame frame = scene.camera().update(player, eye, EYE_HEIGHT);
            assertNotNull(frame);
            assertJointView(scene, eyes(frame.feet()), operation);
        }
    }

    @Test
    void movingEntitiesAndPlayersRetainAContinuousJointView() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        Entity entity = scene.entity(new BoundingBox(7.6, 64, 10, 8.4, 65.9, 10.8));
        MonitorCamera camera = scene.camera();
        MonitorCamera.Frame frame = camera.update(player, eye, EYE_HEIGHT);
        assertNotNull(frame);
        Location observerEye = eyes(frame.feet());
        for (int tick = 0; tick < 30; tick++) {
            eye.add(0.2, 0, 0);
            BoundingBox moved = entity.getBoundingBox().clone().shift(0.2, 0, 0);
            when(entity.getBoundingBox()).thenReturn(moved);
            frame = camera.update(player, observerEye, EYE_HEIGHT);
            assertNotNull(frame);
            assertFalse(frame.cut(), "moving the player and their interaction target together must retain inertia and interpolation");
            observerEye = eyes(frame.feet());
            MonitorOperation.Focus operation = new MonitorOperation().sample(player);
            assertNotNull(operation);
            assertJointView(scene, observerEye, operation);
        }
    }

    @Test
    void changingTheWorkedObjectsPositionEasesTheAimWhileKeepingBothSubjectsVisible() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        Entity entity = scene.entity(new BoundingBox(7.6, 64, 10, 8.4, 65.9, 10.8));
        MonitorCamera camera = scene.camera();
        MonitorCamera.Frame initial = camera.update(player, eye, EYE_HEIGHT);
        assertNotNull(initial);
        Location current = eyes(initial.feet());
        BoundingBox moved = entity.getBoundingBox().clone().shift(0.6, 0, 0);
        when(entity.getBoundingBox()).thenReturn(moved);
        Location body = eye.clone().subtract(0, EYE_HEIGHT * 0.25, 0);
        Vector ideal = body.toVector().subtract(current.toVector()).normalize().multiply(0.55)
                .add(entity.getBoundingBox().getCenter().subtract(current.toVector()).normalize().multiply(0.45)).normalize();
        double fullTurn = current.getDirection().angle(ideal);
        for (int tick = 0; tick < 40; tick++) {
            camera.operations().entity(player, entity);
            MonitorOperation.Focus operation = camera.operations().sample(player);
            assertNotNull(operation);
            MonitorCamera.Frame frame = camera.update(player, current, EYE_HEIGHT);
            assertNotNull(frame);
            assertFalse(frame.cut(), "a small change of work position must not recreate the camera");
            Location next = eyes(frame.feet());
            if (tick == 0) {
                double turn = current.getDirection().angle(next.getDirection());
                assertTrue(turn > 0 && turn < fullTurn * 0.75, "aim must accelerate toward the new composition");
            }
            for (int sample = 0; sample <= 10; sample++) {
                double fraction = sample / 10.0;
                Location between = current.clone().add(next.toVector().subtract(current.toVector()).multiply(fraction));
                between.setYaw((float) (current.getYaw() + Math.IEEEremainder(next.getYaw() - current.getYaw(), 360) * fraction));
                between.setPitch((float) (current.getPitch() + (next.getPitch() - current.getPitch()) * fraction));
                assertJointView(scene, between, operation);
            }
            current = next;
        }
        assertTrue(current.getDirection().dot(ideal) > 0.99999, "the eased aim must settle on the new composition");
    }

    @Test
    void narrowFieldOfViewPullsBackToKeepThePlayerAndObjectInsideItsMargin() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        scene.block(new BoundingBox(7.5, 65, 10, 8.5, 66, 11));
        MonitorOperation.Focus operation = new MonitorOperation().sample(player);
        assertNotNull(operation);
        MonitorCamera camera = scene.camera();
        camera.fieldOfView(40);
        MonitorCamera.Frame frame = camera.update(player, eye, EYE_HEIGHT);
        assertNotNull(frame);
        assertJointView(scene, eyes(frame.feet()), operation, 15);
    }

    @Test
    void switchingSubjectsDiscardsRecentInteractions() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player first = scene.player(eye);
        Player second = scene.player(eye);
        Block block = scene.block(new BoundingBox(10, 64, 10, 11, 65, 11));
        MonitorCamera camera = scene.camera();
        assertNotNull(camera.update(first, eye, EYE_HEIGHT));
        camera.operations().block(first, block, null, null);
        assertNotNull(camera.operations().sample(first));
        assertNotNull(camera.update(second, eye, EYE_HEIGHT));
        assertNull(camera.operations().sample(second));
    }

    @Test
    void movingTheAimPointDoesNotTranslateAStationaryObjectsBoundsDuringInterpolation() {
        Scene scene = new Scene();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Player player = scene.player(eye);
        Entity entity = scene.entity(new BoundingBox(7.5, 64, 10, 8.5, 66, 10.6));
        MonitorOperation operations = new MonitorOperation();
        MonitorOperation.Focus previous = operations.sample(player);
        eye.add(0.2, 0, 0);
        MonitorOperation.Focus current = operations.sample(player);
        assertNotNull(previous);
        assertNotNull(current);
        assertNotEquals(previous.point(), current.point());
        MonitorOperation.Focus during = current.during(previous, new Vector(), 0.5);
        assertEquals(entity.getBoundingBox(), during.bounds(), "an aim change is not object motion");
    }

    @Test
    void followingAcceleratesAndCoastsToAStopInsteadOfCopyingEveryStep() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        Location initial = observer.clone();
        double firstStep = 0;
        double lastStep = 0;
        for (int tick = 0; tick < 20; tick++) {
            eye.add(0.2, 0, 0);
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
            assertNotNull(frame);
            assertFalse(frame.cut(), "starting to walk must keep one continuous camera");
            double step = frame.feet().getX() - observer.getX();
            if (tick == 0) firstStep = step;
            lastStep = step;
            observer = frame.feet();
            assertFramed(eye, eyes(observer));
        }
        assertTrue(firstStep > 0 && firstStep < 0.05, "the rig should ease into movement");
        assertTrue(lastStep > firstStep * 3 && lastStep <= 0.21, "the rig should accelerate to the walking speed");
        double previousStep = lastStep;
        for (int tick = 0; tick < 40; tick++) {
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
            assertNotNull(frame);
            assertFalse(frame.cut(), "braking must retain client interpolation");
            double step = frame.feet().getX() - observer.getX();
            if (tick == 0) assertTrue(step > 0.05 && step < lastStep, "the camera should keep moving as it brakes");
            assertTrue(step >= -1.0e-6 && step <= previousStep + 1.0e-6, "braking must settle without oscillation");
            previousStep = step;
            observer = frame.feet();
        }
        assertEquals(initial.getX() + 4, observer.getX(), 1.0e-5);
    }

    @Test
    void sustainedTurnsGraduallyReframeThePlayersOperatingDirection() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        eye.setYaw(180);
        for (int tick = 0; tick < 80; tick++) {
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
            assertNotNull(frame);
            assertFalse(frame.cut(), "a sustained head turn should orbit continuously in an open area, tick " + tick);
            assertTrue(frame.feet().distance(observer) < 0.6, "reframing must retain angular inertia");
            observer = frame.feet();
        }
        Vector offset = eyes(observer).toVector().subtract(eye.toVector());
        assertTrue(offset.dot(eye.getDirection()) < 0, "the rig must settle behind the new operating direction");
        assertFramed(eye, eyes(observer));
    }

    @Test
    void sustainedLookingUpAndDownKeepsTheOperationInFrame() {
        for (float pitch : new float[]{-80, 80}) {
            Scene scene = new Scene();
            MonitorCamera camera = scene.camera();
            UUID target = UUID.randomUUID();
            Location eye = new Location(scene.world, 8, 65.62, 8);
            Location observer = view(camera, target, eye, eye);
            eye.setPitch(pitch);
            for (int tick = 0; tick < 80; tick++) observer = view(camera, target, eye, eyes(observer));
            assertFramed(eye, eyes(observer));
        }
    }

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
    void framesTheBodyAndOperationFromTheObserversActualEyeHeight() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        Location targetEye = new Location(scene.world, 8, 65.27, 8, 45, 50);
        MonitorCamera.Frame frame = camera.update(UUID.randomUUID(), targetEye, EYE_HEIGHT, targetEye, 0.9, null);
        assertNotNull(frame);
        Location feet = frame.feet();
        assertNotNull(feet);
        assertFramed(targetEye, feet.clone().add(0, 0.9, 0));
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
        Vector followed = eyes(observer).toVector().subtract(eye.toVector());
        assertTrue(offset.distance(followed) < 0.75, "walking may trail the subject within the inertia limit");
        assertTrue(followed.dot(offset) > 0, "inertia must retain the observation side");
    }

    @Test
    void rapidStrafingAndHeadTurnsDoNotOrbitOrShakeTheView() {
        Scene scene = new Scene();
        MonitorCamera camera = scene.camera();
        UUID target = UUID.randomUUID();
        Location eye = new Location(scene.world, 8, 65.62, 8);
        Location observer = view(camera, target, eye, eye);
        Vector offset = eyes(observer).toVector().subtract(eye.toVector());
        Location initial = observer.clone();
        for (int index = 0; index < 100; index++) {
            eye.add(index % 2 == 0 ? 0.25 : -0.25, 0, 0);
            eye.setYaw(index % 2 == 0 ? 90 : -90);
            eye.setPitch(index % 2 == 0 ? 80 : -80);
            Location next = view(camera, target, eye, eyes(observer));
            assertTrue(next.distance(observer) < 0.1, "alternating steps must be absorbed by the follow inertia");
            assertTrue(next.distance(initial) < 0.25);
            assertTrue(offset.dot(eyes(next).toVector().subtract(eye.toVector())) > 0);
            observer = next;
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
            assertFramed(eye, eyes(observer));
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
        assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT, null));
        scene.solids.clear();
        for (int index = 0; index < 19; index++) assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT, null));
        assertNotNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT, null));
        scene.enclose(8, 65.62, 8);
        assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT, null));
        scene.solids.clear();
        assertNull(camera.update(target, eye, EYE_HEIGHT, eye, EYE_HEIGHT, null), "one clear frame must not flicker back");
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
        assertNull(scene.camera().update(UUID.randomUUID(), eye, EYE_HEIGHT, eye, EYE_HEIGHT, null));
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
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
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
        MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
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
        MonitorCamera.Frame frame = scene.camera().update(UUID.randomUUID(), eye, 0.4, eye, EYE_HEIGHT, null);
        assertNotNull(frame);
        Vector head = eye.toVector().subtract(eyes(frame.feet()).toVector()).normalize();
        assertTrue(frame.feet().getDirection().dot(head) > Math.cos(Math.toRadians(20)),
                "a swimming player's head must stay close to the center while framing the operation");
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
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
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
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, current, EYE_HEIGHT, null);
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
        Location operation = eye.clone().add(eye.getDirection().multiply(3));
        Location aim = focus.clone().add(operation.toVector().subtract(focus.toVector()).multiply(0.25));
        current.setDirection(aim.toVector().subtract(current.toVector()));
        for (int tick = 0; tick < 100; tick++) {
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, current, EYE_HEIGHT, null);
            assertNotNull(frame);
            Location next = eyes(frame.feet());
            assertFalse(frame.cut(), "an open reframe must orbit around the subject instead of cutting across it, tick " + tick);
            assertTrue(next.distance(current) < 0.55, "a distant viewpoint must not bypass the transition spring");
            if (tick == 0) assertTrue(next.distance(current) < 0.2, "the orbit must ease into motion");
            for (int sample = 0; sample <= 20; sample++) {
                double fraction = sample / 20.0;
                Location between = current.clone().add(next.toVector().subtract(current.toVector()).multiply(fraction));
                between.setYaw((float) (current.getYaw() + Math.IEEEremainder(next.getYaw() - current.getYaw(), 360) * fraction));
                between.setPitch((float) (current.getPitch() + (next.getPitch() - current.getPitch()) * fraction));
                double alignment = between.getDirection().dot(focus.toVector().subtract(between.toVector()).normalize());
                assertTrue(alignment >= Math.cos(Math.toRadians(12)), "interpolating the camera must keep the player near the center");
                assertTrue(between.distance(focus) > 4, "the transition must not fly through the player's body");
                assertFramed(eye, between);
            }
            current = next;
        }
        assertTrue(current.distance(initial) < 0.01, "the orbit must settle at the selected viewpoint");
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
            MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
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
                assertFramed(eye, eyes(observer));
            }
            offsets[direction] = eyes(observer).toVector().subtract(eye.toVector());
        }
        assertTrue(offsets[0].getX() - offsets[1].getX() > 0.6,
                "prediction must still lead in both directions after allowing for follow inertia");
        assertEquals(offsets[0].getZ(), offsets[1].getZ(), 1.0e-3);
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
        MonitorCamera.Frame switched = camera.update(UUID.randomUUID(), eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
        assertNotNull(switched);
        assertTrue(switched.cut());
        assertTrue(eyes(switched.feet()).distance(eye) < 5);
        for (int tick = 0; tick < 25; tick++) {
            eye.add(3, 1, 0);
            observer = view(camera, target, eye, eyes(observer));
        }
        camera.reset();
        MonitorCamera.Frame reset = camera.update(target, eye, EYE_HEIGHT, eyes(observer), EYE_HEIGHT, null);
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
        assertTrue(Math.hypot(offset.getX(), offset.getZ()) > 7,
                "blocked prediction should retain a wide view while allowing for follow inertia");
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
        MonitorCamera.Frame frame = camera.update(target, eye, EYE_HEIGHT, observerEye, EYE_HEIGHT, null);
        assertNotNull(frame, "a clear nearby third-person view should be available");
        return frame.feet();
    }

    private static Location eyes(Location feet) { return feet.clone().add(0, EYE_HEIGHT, 0); }

    private static void assertJointView(Scene scene, Location eye, MonitorOperation.Focus operation) {
        assertJointView(scene, eye, operation, 30);
    }

    private static void assertJointView(Scene scene, Location eye, MonitorOperation.Focus operation, double halfAngle) {
        Vector delta = operation.point().toVector().subtract(eye.toVector());
        assertNull(scene.trace(eye.toVector(), delta.clone().normalize(), delta.length()), "the actual worked face must be unobstructed");
        assertNull(operation.subjectBounds().rayTrace(eye.toVector(), delta.clone().normalize(), delta.length() - 0.04),
                "the player must not hide the interaction point");
        for (BoundingBox bounds : List.of(operation.bounds(), operation.subjectBounds())) {
            for (double x : new double[]{bounds.getMinX(), bounds.getMaxX()}) {
                for (double y : new double[]{bounds.getMinY(), bounds.getMaxY()}) {
                    for (double z : new double[]{bounds.getMinZ(), bounds.getMaxZ()}) {
                        Vector direction = new Vector(x, y, z).subtract(eye.toVector()).normalize();
                        assertTrue(eye.getDirection().dot(direction) >= Math.cos(Math.toRadians(halfAngle)),
                                "the whole player and operation object must fit inside the viewing area");
                    }
                }
            }
        }
    }

    private static void assertFramed(Location targetEye, Location observerEye) {
        Vector body = targetEye.toVector().subtract(new Vector(0, 0.4, 0));
        Vector operation = targetEye.toVector().add(targetEye.getDirection().multiply(3));
        Vector direction = observerEye.getDirection();
        assertTrue(direction.dot(body.subtract(observerEye.toVector()).normalize()) >= Math.cos(Math.toRadians(30)),
                "the player's body must remain in the central viewing area");
        assertTrue(direction.dot(operation.subtract(observerEye.toVector()).normalize()) >= Math.cos(Math.toRadians(30)),
                "the area in the player's operating direction must also fit in the view");
    }

    private static void assertClear(Scene scene, Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        assertNull(scene.trace(from.toVector(), delta.clone().normalize(), delta.length()));
    }

    private record Chunk(int x, int z) { }

    private static final class Scene {
        final World world = mock(World.class);
        final List<BoundingBox> solids = new ArrayList<>();
        final Map<BoundingBox, Block> blocks = new HashMap<>();
        final List<Entity> entities = new ArrayList<>();
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
                    eq(FluidCollisionMode.NEVER), anyBoolean())).thenAnswer(call -> {
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
            when(world.rayTraceEntities(any(Location.class), any(Vector.class), anyDouble(), eq(0.1), any())).thenAnswer(call -> {
                Location from = call.getArgument(0);
                Vector direction = call.getArgument(1);
                double reach = call.getArgument(2);
                Predicate<Entity> filter = call.getArgument(4);
                RayTraceResult nearest = null;
                double distance = Double.POSITIVE_INFINITY;
                for (Entity entity : entities) {
                    if (!filter.test(entity)) continue;
                    RayTraceResult hit = entity.getBoundingBox().clone().expand(0.1).rayTrace(from.toVector(), direction, reach);
                    if (hit != null && hit.getHitPosition().distanceSquared(from.toVector()) < distance) {
                        nearest = new RayTraceResult(hit.getHitPosition(), entity);
                        distance = hit.getHitPosition().distanceSquared(from.toVector());
                    }
                }
                return nearest;
            });
        }

        Player player(Location eye) {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getEyeLocation()).thenAnswer(_ -> eye.clone());
            when(player.getEyeHeight()).thenReturn(EYE_HEIGHT);
            when(player.getWorld()).thenAnswer(_ -> eye.getWorld());
            when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
            when(player.getBoundingBox()).thenAnswer(_ -> new BoundingBox(eye.getX() - 0.3, eye.getY() - EYE_HEIGHT,
                    eye.getZ() - 0.3, eye.getX() + 0.3, eye.getY() + 0.18, eye.getZ() + 0.3));
            when(player.canSee(any(Entity.class))).thenReturn(true);
            return player;
        }

        Block block(BoundingBox bounds) {
            Block block = mock(Block.class);
            when(block.getBoundingBox()).thenReturn(bounds);
            when(block.getWorld()).thenReturn(world);
            when(block.getLocation()).thenReturn(bounds.getMin().toLocation(world));
            blocks.put(bounds, block);
            solids.add(bounds);
            return block;
        }

        Entity entity(BoundingBox bounds) {
            Entity entity = mock(Entity.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.getBoundingBox()).thenReturn(bounds);
            when(entity.getWorld()).thenReturn(world);
            when(entity.isValid()).thenReturn(true);
            entities.add(entity);
            return entity;
        }

        RayTraceResult trace(Vector start, Vector direction, double length) {
            RayTraceResult nearest = null;
            double distance = Double.POSITIVE_INFINITY;
            for (BoundingBox solid : solids) {
                RayTraceResult hit = solid.rayTrace(start, direction, length);
                if (hit != null && hit.getHitPosition().distanceSquared(start) < distance) {
                    Block block = blocks.get(solid);
                    nearest = block == null ? hit : new RayTraceResult(hit.getHitPosition(), block, hit.getHitBlockFace());
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
