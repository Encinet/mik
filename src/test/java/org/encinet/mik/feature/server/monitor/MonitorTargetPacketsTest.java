package org.encinet.mik.feature.server.monitor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.vector.positionpath.LinearPositionPath;
import com.github.retrooper.packetevents.protocol.vector.positionpath.SteppedPositionPath;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.LinearVecDelta;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.SteppedVecDelta;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitorTargetPacketsTest {
    private PacketEventsAPI<?> previous;
    private final MonitorTargetPackets packets = new MonitorTargetPackets();
    private final UUID viewer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        previous = PacketEvents.getAPI();
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        ServerManager server = mock(ServerManager.class);
        when(api.getServerManager()).thenReturn(server);
        when(server.getVersion()).thenReturn(ServerVersion.V_26_3);
        PacketEvents.setAPI(api);
        packets.begin(viewer, 42, new Vector3d(100.125, 64.0625, -25.25), 30, 15);
    }

    @AfterEach
    void tearDown() { PacketEvents.setAPI(previous); }

    @Test
    void flattensHighSpeedStepsWithoutOverflowOrChangingTheNextRelativeBase() {
        var steps = new SteppedVecDelta(List.of(
                new SteppedVecDelta.DeltaStep(1, (short) 24576, (short) 1024, (short) 0),
                new SteppedVecDelta.DeltaStep(1, (short) 16384, (short) -512, (short) -8192)));
        var original = new WrapperPlayServerEntityRelativeMoveAndRotation(42, steps, 90, -20, true);
        var rewritten = sync(packets.rewrite(viewer, original));
        assertEquals(new Vector3d(110.125, 64.1875, -27.25), rewritten.getPosition().getEndPosition());
        assertEquals(90, rewritten.getYRot());
        assertEquals(-20, rewritten.getXRot());
        assertTrue(rewritten.isOnGround());
        assertSame(steps, original.getDelta(), "other recipients must retain the original path");

        var next = new WrapperPlayServerEntityRelativeMove(42,
                new LinearVecDelta((short) -2048, (short) 0, (short) 4096), false);
        var following = sync(packets.rewrite(viewer, next));
        assertEquals(new Vector3d(109.625, 64.1875, -26.25), following.getPosition().getEndPosition());
        assertEquals(90, following.getYRot());
        assertFalse(following.isOnGround());
    }

    @Test
    void absoluteResyncAndRotationReplaceTheDecoderStateBeforeFurtherRelativeMovement() {
        var path = new SteppedPositionPath(List.of(
                new SteppedPositionPath.Step(1000, 200, -300, 1),
                new SteppedPositionPath.Step(1004.25, 202.5, -302, 1)));
        var original = new WrapperPlayServerEntityPositionSync(42, path, -45, 10, false);
        var rewritten = sync(packets.rewrite(viewer, original));
        assertEquals(new Vector3d(1004.25, 202.5, -302), rewritten.getPosition().getEndPosition());
        assertSame(path, original.getPosition());
        var rotation = new WrapperPlayServerEntityRotation(42, 75, -30, true);
        assertSame(rotation, packets.rewrite(viewer, rotation));
        var next = new WrapperPlayServerEntityRelativeMove(42, 1, 0, -2, true);
        var following = sync(packets.rewrite(viewer, next));
        assertEquals(new Vector3d(1005.25, 202.5, -304), following.getPosition().getEndPosition());
        assertEquals(75, following.getYRot());
        assertEquals(-30, following.getXRot());

        var teleport = new WrapperPlayServerEntityPositionSync(42,
                new LinearPositionPath(new Vector3d(-5000, 80, 50)), 0, 0, false);
        assertSame(teleport, packets.rewrite(viewer, teleport), "linear teleport semantics must remain intact");
        assertEquals(new Vector3d(-4999, 80, 48), sync(packets.rewrite(viewer, next)).getPosition().getEndPosition());
    }

    @Test
    void scopesDecodingToTheCurrentViewerAndTargetAndClearsItOnExit() {
        var movement = new WrapperPlayServerEntityRelativeMove(42, 1, 0, 0, false);
        assertSame(movement, packets.rewrite(UUID.randomUUID(), movement));
        var other = new WrapperPlayServerEntityRelativeMove(50, 7, 7, 7, false);
        assertSame(other, packets.rewrite(viewer, other));
        assertEquals(new Vector3d(101.125, 64.0625, -25.25), sync(packets.rewrite(viewer, movement)).getPosition().getEndPosition());
        packets.begin(viewer, 50, new Vector3d(10, 20, 30), 0, 0);
        assertSame(movement, packets.rewrite(viewer, movement));
        assertEquals(new Vector3d(17, 27, 37), sync(packets.rewrite(viewer, other)).getPosition().getEndPosition());
        packets.end(viewer);
        assertSame(other, packets.rewrite(viewer, other));
        packets.begin(viewer, 42, new Vector3d(1, 2, 3), 0, 0);
        packets.clear();
        assertSame(movement, packets.rewrite(viewer, movement));
        assertFalse(packets.active(viewer));
    }

    @Test
    void reenteringTrackingRangeResetsTheBaseToTheSpawnPacket() {
        var spawn = mock(WrapperPlayServerSpawnEntity.class);
        when(spawn.getEntityId()).thenReturn(42);
        when(spawn.getPosition()).thenReturn(new Vector3d(500.25, 70, -800.5));
        when(spawn.getYaw()).thenReturn(70f);
        when(spawn.getPitch()).thenReturn(-15f);
        assertSame(spawn, packets.rewrite(viewer, spawn));
        var next = new WrapperPlayServerEntityRelativeMove(42, 2, 0, -1, false);
        var rewritten = sync(packets.rewrite(viewer, next));
        assertEquals(new Vector3d(502.25, 70, -801.5), rewritten.getPosition().getEndPosition());
        assertEquals(70, rewritten.getYRot());
        assertEquals(-15, rewritten.getXRot());
    }

    private static WrapperPlayServerEntityPositionSync sync(PacketWrapper<?> packet) {
        var sync = assertInstanceOf(WrapperPlayServerEntityPositionSync.class, packet);
        assertInstanceOf(LinearPositionPath.class, sync.getPosition());
        return sync;
    }
}
