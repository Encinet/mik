package org.encinet.mik.integration.axiom;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AxiomImageProtocolTest {
    private static final URI URL = URI.create("https://example.org/封面.png?size=1024");
    private static AxiomImage image() {
        return new AxiomImage(URL, new Vector3f(12.5f, 70, -8), new Quaternionf().rotationYXZ(.2f, .3f, .4f), 2.5f, .75f);
    }

    @Test void encodesTheClientsExactImageAnnotationFormat() {
        UUID id = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");
        var image = image();
        var packet = ByteBuffer.wrap(AxiomImageProtocol.encode(List.of(new AxiomImageProtocol.Create(id, image))).getFirst());
        assertEquals(1, varInt(packet));
        assertEquals(0, packet.get());
        assertEquals(id, uuid(packet));
        assertEquals(2, packet.get());
        byte[] url = new byte[varInt(packet)]; packet.get(url);
        assertEquals(URL.toASCIIString(), new String(url, StandardCharsets.UTF_8));
        assertEquals(image.position(), vector(packet));
        assertEquals(image.rotation(), quaternion(packet));
        assertEquals(3, packet.get()); // SOUTH: identity base plane
        assertEquals(0, packet.getFloat()); // fallback yaw
        assertEquals(2.5f, packet.getFloat());
        assertEquals(.75f, packet.getFloat());
        assertEquals(0, packet.get()); // Fixed plane, no automatic billboard
        assertFalse(packet.hasRemaining());
    }

    @Test void movementAndRotationDoNotRecreateOrReloadTheTexture() {
        UUID id = UUID.randomUUID();
        var before = image();
        var after = new AxiomImage(URL, new Vector3f(13, 71, -9), new Quaternionf().rotationY(.9f), before.width(), before.opacity());
        assertTrue(AxiomImageProtocol.changes(Map.of(id, before), Map.of(id, before)).isEmpty());
        var changes = AxiomImageProtocol.changes(Map.of(id, before), Map.of(id, after));
        assertEquals(2, changes.size());
        assertInstanceOf(AxiomImageProtocol.Move.class, changes.getFirst());
        assertInstanceOf(AxiomImageProtocol.Rotate.class, changes.getLast());
        var packet = ByteBuffer.wrap(AxiomImageProtocol.encode(changes).getFirst());
        assertEquals(2, varInt(packet));
        assertEquals(2, packet.get()); assertEquals(id, uuid(packet)); assertEquals(after.position(), vector(packet));
        assertEquals(4, packet.get()); assertEquals(id, uuid(packet)); assertEquals(after.rotation(), quaternion(packet));
        assertFalse(packet.hasRemaining());
        for (var changed : List.of(new AxiomImage(URI.create("https://example.org/new.png"), after.position(), after.rotation(), 2.5f, .75f),
                new AxiomImage(URL, after.position(), after.rotation(), 3, .75f),
                new AxiomImage(URL, after.position(), after.rotation(), 2.5f, .5f))) {
            var update = AxiomImageProtocol.changes(Map.of(id, after), Map.of(id, changed));
            assertEquals(1, update.size());
            assertInstanceOf(AxiomImageProtocol.Create.class, update.getFirst());
        }
    }

    @Test void menuRemovalDeletesOnlyItsOwnAnnotationIds() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var changes = AxiomImageProtocol.changes(Map.of(first, image(), second, image()), Map.of(second, image()));
        assertEquals(List.of(new AxiomImageProtocol.Delete(first)), changes);
        var packet = ByteBuffer.wrap(AxiomImageProtocol.encode(changes).getFirst());
        assertEquals(1, varInt(packet)); assertEquals(1, packet.get()); assertEquals(first, uuid(packet));
        assertFalse(packet.hasRemaining());
        assertTrue(AxiomImageProtocol.encode(List.of()).isEmpty());
    }

    @Test void largeImageCollectionsAreSplitAtActionBoundariesWithinPluginMessageLimits() {
        Map<UUID, AxiomImage> images = new LinkedHashMap<>();
        for (int i = 0; i < 600; i++) images.put(new UUID(0, i), image());
        List<byte[]> packets = AxiomImageProtocol.encode(AxiomImageProtocol.changes(Map.of(), images));
        assertTrue(packets.size() > 1);
        List<UUID> decoded = new ArrayList<>();
        for (byte[] bytes : packets) {
            assertTrue(bytes.length <= AxiomImageProtocol.MAX_PAYLOAD_BYTES);
            var packet = ByteBuffer.wrap(bytes);
            int count = varInt(packet);
            for (int i = 0; i < count; i++) {
                assertEquals(0, packet.get()); decoded.add(uuid(packet)); assertEquals(2, packet.get());
                int urlBytes = varInt(packet); packet.position(packet.position() + urlBytes + 42);
            }
            assertFalse(packet.hasRemaining());
        }
        assertEquals(List.copyOf(images.keySet()), decoded);
    }

    @Test void publishedSnapshotsCannotBeMutatedThroughJomlVectors() {
        var position = new Vector3f(1, 2, 3); var rotation = new Quaternionf();
        var image = new AxiomImage(URL, position, rotation, 2, 1);
        position.x = 500; rotation.rotateY(1);
        ((Vector3f) image.position()).x = 600;
        ((Quaternionf) image.rotation()).rotateX(2);
        assertEquals(new Vector3f(1, 2, 3), image.position());
        assertEquals(new Quaternionf(), image.rotation());
        assertThrows(IllegalArgumentException.class, () -> new AxiomImage(URL, new Vector3f(Float.NaN, 0, 0), new Quaternionf(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AxiomImage(URL, new Vector3f(), new Quaternionf(0, 0, 0, 0), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AxiomImage(URL, new Vector3f(), new Quaternionf(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new AxiomImage(URL, new Vector3f(), new Quaternionf(), 1, 2));
    }

    @Test void aPartiallySentInitialSnapshotCanStillBeFullyRemoved() {
        var state = new AxiomImageState();
        var images = new LinkedHashMap<UUID, AxiomImage>();
        for (int i = 0; i < 600; i++) images.put(new UUID(0, i), image());
        state.replace(images);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IllegalStateException.class, () -> state.synchronize(_ -> {
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("Connection interrupted after first batch");
        }));
        assertTrue(state.pending());
        state.replace(Map.of());
        var removed = new java.util.HashSet<UUID>();
        state.synchronize(bytes -> {
            var packet = ByteBuffer.wrap(bytes);
            int count = varInt(packet);
            for (int i = 0; i < count; i++) { assertEquals(1, packet.get()); removed.add(uuid(packet)); }
            assertFalse(packet.hasRemaining());
        });
        assertEquals(images.keySet(), removed);
        assertTrue(state.empty());
        assertFalse(state.pending());
    }

    @Test void rollbackAfterAnUncertainTextureReplacementRecreatesTheOriginal() {
        UUID id = UUID.randomUUID();
        var state = new AxiomImageState();
        state.replace(Map.of(id, image()));
        state.synchronize(_ -> { });
        state.replace(Map.of(id, new AxiomImage(URI.create("https://example.org/replacement.png"),
                image().position(), image().rotation(), 2.5f, .75f)));
        assertThrows(IllegalStateException.class, () -> state.synchronize(_ -> { throw new IllegalStateException("Uncertain send"); }));
        state.replace(Map.of(id, image()));
        var packets = new ArrayList<byte[]>(); state.synchronize(packets::add);
        assertEquals(1, packets.size());
        var packet = ByteBuffer.wrap(packets.getFirst());
        assertEquals(1, varInt(packet)); assertEquals(0, packet.get()); assertEquals(id, uuid(packet));
        assertEquals(2, packet.get()); byte[] url = new byte[varInt(packet)]; packet.get(url);
        assertEquals(URL.toASCIIString(), new String(url, StandardCharsets.UTF_8));
        assertFalse(state.pending());
    }

    @Test void closingOneOwnerLeavesOtherOwnersAndAvoidsRedundantUpdates() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var firstOwner = new AxiomImageState(); var secondOwner = new AxiomImageState();
        firstOwner.replace(Map.of(first, image())); secondOwner.replace(Map.of(second, image()));
        firstOwner.synchronize(_ -> { }); secondOwner.synchronize(_ -> { });
        firstOwner.synchronize(_ -> fail("Unchanged images must not send packets"));
        firstOwner.replace(Map.of());
        var packets = new ArrayList<byte[]>(); firstOwner.synchronize(packets::add);
        assertEquals(1, packets.size());
        var packet = ByteBuffer.wrap(packets.getFirst());
        assertEquals(1, varInt(packet)); assertEquals(1, packet.get()); assertEquals(first, uuid(packet));
        assertFalse(packet.hasRemaining());
        assertTrue(firstOwner.empty()); assertFalse(secondOwner.empty());
        secondOwner.synchronize(_ -> fail("Another owner's cleanup must not affect this snapshot"));
        secondOwner.resend();
        assertTrue(secondOwner.pending());
        secondOwner.synchronize(bytes -> {
            var resend = ByteBuffer.wrap(bytes);
            assertEquals(1, varInt(resend)); assertEquals(0, resend.get()); assertEquals(second, uuid(resend));
        });
        assertFalse(secondOwner.pending());
    }

    private static int varInt(ByteBuffer packet) {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int value = Byte.toUnsignedInt(packet.get()); result |= (value & 127) << shift;
            if ((value & 128) == 0) return result;
        }
        throw new AssertionError("Malformed VarInt");
    }
    private static UUID uuid(ByteBuffer packet) { return new UUID(packet.getLong(), packet.getLong()); }
    private static Vector3f vector(ByteBuffer packet) { return new Vector3f(packet.getFloat(), packet.getFloat(), packet.getFloat()); }
    private static Quaternionf quaternion(ByteBuffer packet) {
        return new Quaternionf(packet.getFloat(), packet.getFloat(), packet.getFloat(), packet.getFloat());
    }
}
