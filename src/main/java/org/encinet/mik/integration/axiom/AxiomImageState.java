package org.encinet.mik.integration.axiom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One owner's desired and last sent snapshots, including UUIDs from interrupted transmissions. */
final class AxiomImageState {
    private Map<UUID, AxiomImage> desired = Map.of();
    private Map<UUID, AxiomImage> published = Map.of();
    private final Set<UUID> knownIds = new HashSet<>();
    private boolean replay;

    void replace(Map<UUID, AxiomImage> images) { desired = Map.copyOf(images); }
    void resend() { replay = true; }
    boolean pending() { return replay || !desired.equals(published); }
    boolean empty() { return desired.isEmpty() && knownIds.isEmpty(); }

    void synchronize(Consumer<byte[]> sender) {
        if (!pending()) return;
        Map<UUID, AxiomImage> previous = replay ? Map.of() : published;
        var changes = new ArrayList<AxiomImageProtocol.Action>();
        for (UUID id : knownIds) if (!desired.containsKey(id) && !previous.containsKey(id))
            changes.add(new AxiomImageProtocol.Delete(id));
        changes.addAll(AxiomImageProtocol.changes(previous, desired));
        var packets = AxiomImageProtocol.encode(changes);
        // Remember all possibly created UUIDs before sending. A send may deliver and still throw.
        knownIds.addAll(desired.keySet());
        try {
            packets.forEach(sender);
            published = desired;
            knownIds.retainAll(desired.keySet());
            replay = false;
        } catch (RuntimeException exception) {
            // Even a rollback to the old snapshot must recreate textures that may have changed.
            replay = true;
            throw exception;
        }
    }
}
