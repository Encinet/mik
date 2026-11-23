package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.protocol.music.NoteStop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks emitted sound ids so note stop events can stop their voice range. */
final class NoteSoundLedger {

    private final Map<Integer, Map<String, Set<UUID>>> soundsByLayer = new HashMap<>();

    void record(int layer, String sound, UUID listener) {
        soundsByLayer.computeIfAbsent(layer, _ -> new HashMap<>())
                .computeIfAbsent(sound, _ -> new HashSet<>())
                .add(listener);
    }

    Set<StopRequest> stop(NoteStop stopper) {
        int firstLayer = stopper.hasFirstVoice() ? stopper.getFirstVoice() : -1;
        int lastLayer = stopper.getLastVoice();
        Set<StopRequest> requests = removeMatchingLayers(firstLayer, lastLayer);
        removeClientStoppedSounds(requests);
        return Set.copyOf(requests);
    }

    Set<StopRequest> stopAll() {
        Set<StopRequest> requests = removeMatchingLayers(-1, 0);
        return Set.copyOf(requests);
    }

    private Set<StopRequest> removeMatchingLayers(int firstLayer, int lastLayer) {
        Set<StopRequest> requests = new HashSet<>();
        Iterator<Map.Entry<Integer, Map<String, Set<UUID>>>> layers =
                soundsByLayer.entrySet().iterator();
        while (layers.hasNext()) {
            Map.Entry<Integer, Map<String, Set<UUID>>> layer = layers.next();
            int voice = layer.getKey();
            if (firstLayer >= 0
                    && (voice < firstLayer || voice > lastLayer)) {
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
