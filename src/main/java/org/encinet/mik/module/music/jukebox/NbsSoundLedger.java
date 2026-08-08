package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsNoteType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks emitted sound ids so NBS Sound Stopper events can stop their layer range. */
final class NbsSoundLedger {

    private final Map<Integer, Map<String, Set<UUID>>> soundsByLayer = new HashMap<>();

    void record(int layer, String sound, UUID listener) {
        soundsByLayer.computeIfAbsent(layer, ignored -> new HashMap<>())
                .computeIfAbsent(sound, ignored -> new HashSet<>())
                .add(listener);
    }

    Set<StopRequest> stop(NbsNote stopper) {
        if (stopper.type() != NbsNoteType.SOUND_STOP) {
            throw new IllegalArgumentException("note is not a Sound Stopper");
        }
        int firstLayer = stopper.soundStopStartLayer();
        int lastLayer = Math.max(firstLayer, stopper.soundStopEndLayer());
        Set<StopRequest> requests = removeMatchingLayers(firstLayer, lastLayer);
        removeClientStoppedSounds(requests);
        return Set.copyOf(requests);
    }

    Set<StopRequest> stopAll() {
        Set<StopRequest> requests = removeMatchingLayers(0, 0);
        return Set.copyOf(requests);
    }

    private Set<StopRequest> removeMatchingLayers(int firstLayer, int lastLayer) {
        Set<StopRequest> requests = new HashSet<>();
        Iterator<Map.Entry<Integer, Map<String, Set<UUID>>>> layers =
                soundsByLayer.entrySet().iterator();
        while (layers.hasNext()) {
            Map.Entry<Integer, Map<String, Set<UUID>>> layer = layers.next();
            int oneBasedLayer = layer.getKey() + 1;
            if (firstLayer != 0
                    && (oneBasedLayer < firstLayer || oneBasedLayer > lastLayer)) {
                continue;
            }
            addRequests(requests, layer.getValue());
            layers.remove();
        }
        return requests;
    }

    private void removeClientStoppedSounds(Set<StopRequest> requests) {
        if (requests.isEmpty()) {
            return;
        }
        Iterator<Map<String, Set<UUID>>> layers = soundsByLayer.values().iterator();
        while (layers.hasNext()) {
            Map<String, Set<UUID>> sounds = layers.next();
            Iterator<Map.Entry<String, Set<UUID>>> entries = sounds.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, Set<UUID>> sound = entries.next();
                sound.getValue().removeIf(listener -> requests.contains(
                        new StopRequest(listener, sound.getKey())));
                if (sound.getValue().isEmpty()) {
                    entries.remove();
                }
            }
            if (sounds.isEmpty()) {
                layers.remove();
            }
        }
    }

    private static void addRequests(
            Set<StopRequest> output, Map<String, Set<UUID>> sounds) {
        sounds.forEach((sound, listeners) -> listeners.forEach(
                listener -> output.add(new StopRequest(listener, sound))));
    }

    record StopRequest(UUID listener, String sound) {
    }
}
