package org.encinet.mik.integration.axiom;

import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Axiom annotation_update wire format. Only owned image UUIDs are touched; no clear-all action. */
final class AxiomImageProtocol {
    static final int MAX_PAYLOAD_BYTES = 32_000;

    private AxiomImageProtocol() { }

    sealed interface Action permits Create, Delete, Move, Rotate { UUID id(); }
    record Create(UUID id, AxiomImage image) implements Action { }
    record Delete(UUID id) implements Action { }
    record Move(UUID id, Vector3fc position) implements Action { }
    record Rotate(UUID id, Quaternionfc rotation) implements Action { }

    static List<Action> changes(Map<UUID, AxiomImage> previous, Map<UUID, AxiomImage> next) {
        List<Action> result = new ArrayList<>();
        for (UUID id : previous.keySet()) if (!next.containsKey(id)) result.add(new Delete(id));
        next.forEach((id, image) -> {
            AxiomImage old = previous.get(id);
            if (old == null || !old.sameTexture(image)) {
                result.add(new Create(id, image));
            } else {
                if (!old.position().equals(image.position())) result.add(new Move(id, image.position()));
                if (!old.rotation().equals(image.rotation())) result.add(new Rotate(id, image.rotation()));
            }
        });
        return List.copyOf(result);
    }

    static List<byte[]> encode(List<Action> actions) {
        List<byte[]> packets = new ArrayList<>();
        List<byte[]> batch = new ArrayList<>();
        int length = 5; // Reserve the largest possible VarInt count before filling the batch.
        for (Action action : actions) {
            byte[] encoded = encodeAction(action);
            if (encoded.length + 5 > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Axiom image update is too large");
            if (length + encoded.length > MAX_PAYLOAD_BYTES) {
                packets.add(packet(batch));
                batch.clear();
                length = 5;
            }
            batch.add(encoded);
            length += encoded.length;
        }
        if (!batch.isEmpty()) packets.add(packet(batch));
        return List.copyOf(packets);
    }

    private static byte[] packet(List<byte[]> actions) {
        int size = actions.stream().mapToInt(value -> value.length).sum();
        ByteBuffer out = ByteBuffer.allocate(5 + size);
        varInt(out, actions.size());
        actions.forEach(out::put);
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    private static byte[] encodeAction(Action action) {
        int size = switch (action) {
            case Create create -> 65 + create.image().url().toASCIIString().length();
            case Delete _ -> 17;
            case Move _ -> 29;
            case Rotate _ -> 33;
        };
        ByteBuffer out = ByteBuffer.allocate(size);
        out.put((byte) switch (action) { case Create _ -> 0; case Delete _ -> 1;
            case Move _ -> 2; case Rotate _ -> 4; });
        out.putLong(action.id().getMostSignificantBits()).putLong(action.id().getLeastSignificantBits());
        switch (action) {
            case Create create -> {
                AxiomImage image = create.image();
                out.put((byte) 2); // IMAGE
                byte[] url = image.url().toASCIIString().getBytes(StandardCharsets.UTF_8);
                varInt(out, url.length);
                out.put(url);
                vector(out, image.position());
                quaternion(out, image.rotation());
                // SOUTH has base yaw 0; the complete world rotation is in the quaternion.
                out.put((byte) 3).putFloat(0).putFloat(image.width()).putFloat(image.opacity()).put((byte) 0);
            }
            case Delete _ -> { }
            case Move move -> vector(out, move.position());
            case Rotate rotate -> quaternion(out, rotate.rotation());
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    private static void vector(ByteBuffer out, Vector3fc value) { out.putFloat(value.x()).putFloat(value.y()).putFloat(value.z()); }
    private static void quaternion(ByteBuffer out, Quaternionfc value) {
        out.putFloat(value.x()).putFloat(value.y()).putFloat(value.z()).putFloat(value.w());
    }
    private static void varInt(ByteBuffer out, int value) {
        while ((value & ~0x7f) != 0) { out.put((byte) (value & 0x7f | 0x80)); value >>>= 7; }
        out.put((byte) value);
    }
}
