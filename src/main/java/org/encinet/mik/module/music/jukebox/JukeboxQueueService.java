package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;

import java.util.*;

/**
 * Main-thread-confined manager for jukebox queues and playback modes.
 */
public class JukeboxQueueService {

    private final Map<Location, JukeboxState> states = new HashMap<>();
    private final MusicTrackSelector trackSelector;

    public JukeboxQueueService(MusicTrackSelector trackSelector) {
        this.trackSelector = Objects.requireNonNull(trackSelector, "trackSelector");
    }

    /** Returns the mutable state owned by one jukebox, creating it when needed. */
    public JukeboxState state(Location location) {
        return states.computeIfAbsent(blockLocation(location), ignored -> new JukeboxState());
    }

    public JukeboxState findState(Location location) {
        return states.get(blockLocation(location));
    }

    public void removeState(Location location) {
        states.remove(blockLocation(location));
    }

    public void clear() {
        states.clear();
    }

    public void removeWorld(World world) {
        if (world != null) {
            states.keySet().removeIf(location -> world.equals(location.getWorld()));
        }
    }

    /** Selects the next playlist entry for a manual skip or a natural completion. */
    public MusicTrack nextTrack(
            Location location, MusicTrack currentTrack, boolean naturalCompletion) {
        return state(location).nextTrack(currentTrack, naturalCompletion, trackSelector);
    }

    private static Location blockLocation(Location location) {
        Objects.requireNonNull(location, "location");
        return new Location(location.getWorld(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }

    /** Main-thread-confined queue and playback-mode state for one jukebox. */
    public static final class JukeboxState {
        private final List<MusicTrack> queue = new ArrayList<>();
        private JukeboxPlaybackMode playbackMode = JukeboxPlaybackMode.REPEAT_ALL;

        public List<MusicTrack> queue() {
            return List.copyOf(queue);
        }

        public int queueSize() {
            return queue.size();
        }

        public boolean queueEmpty() {
            return queue.isEmpty();
        }

        public JukeboxPlaybackMode playbackMode() {
            return playbackMode;
        }

        public void addToQueue(MusicTrack music) {
            Objects.requireNonNull(music, "music");
            if (!contains(music)) {
                queue.add(music);
            }
        }

        public int addAllToQueue(Collection<MusicTrack> tracks) {
            Objects.requireNonNull(tracks, "tracks");
            Set<String> existingIds = new HashSet<>();
            queue.forEach(track -> existingIds.add(track.id()));
            int added = 0;
            for (MusicTrack track : tracks) {
                if (track != null && existingIds.add(track.id())) {
                    queue.add(track);
                    added++;
                }
            }
            return added;
        }

        public void removeFromQueue(MusicTrack music) {
            queue.removeIf(existing -> sameTrack(existing, music));
        }

        public boolean contains(MusicTrack music) {
            return music != null && queue.stream().anyMatch(existing -> sameTrack(existing, music));
        }

        public int indexOf(MusicTrack music) {
            if (music == null) {
                return -1;
            }
            for (int index = 0; index < queue.size(); index++) {
                if (sameTrack(queue.get(index), music)) {
                    return index;
                }
            }
            return -1;
        }

        public void moveInQueue(int fromIndex, int toIndex) {
            if (fromIndex < 0 || fromIndex >= queue.size() || toIndex < 0 || toIndex >= queue.size()) {
                return;
            }
            MusicTrack music = queue.remove(fromIndex);
            queue.add(toIndex, music);
        }

        public void clearQueue() {
            queue.clear();
        }

        public JukeboxPlaybackMode cyclePlaybackMode() {
            playbackMode = playbackMode.next();
            return playbackMode;
        }

        public void setPlaybackMode(JukeboxPlaybackMode playbackMode) {
            this.playbackMode = Objects.requireNonNull(playbackMode, "playbackMode");
        }

        MusicTrack trackById(String trackId) {
            if (trackId == null) {
                return null;
            }
            return queue.stream()
                    .filter(track -> trackId.equals(track.id()))
                    .findFirst()
                    .orElse(null);
        }

        private MusicTrack nextTrack(
                MusicTrack currentTrack, boolean naturalCompletion,
                MusicTrackSelector trackSelector) {
            if (naturalCompletion && playbackMode == JukeboxPlaybackMode.REPEAT_ONE
                    && currentTrack != null) {
                return currentTrack;
            }
            if (queue.isEmpty()) {
                return null;
            }
            if (playbackMode == JukeboxPlaybackMode.SHUFFLE) {
                return trackSelector.select(queue, candidate -> queue.size() == 1
                        || currentTrack == null || !sameTrack(candidate, currentTrack));
            }

            int currentIndex = indexOf(currentTrack);
            if (currentIndex < 0) {
                return queue.getFirst();
            }
            int nextIndex = currentIndex + 1;
            if (nextIndex < queue.size()) {
                return queue.get(nextIndex);
            }
            return queue.getFirst();
        }

        private static boolean sameTrack(MusicTrack first, MusicTrack second) {
            return first != null && second != null && first.id().equals(second.id());
        }
    }
}
