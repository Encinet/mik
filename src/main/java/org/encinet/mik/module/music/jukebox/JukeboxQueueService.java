package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Main-thread-confined manager for jukebox queues and playback modes.
 */
public class JukeboxQueueService {

    private final Map<Location, JukeboxState> states = new HashMap<>();
    private final MusicTrackSelector trackSelector;
    private final Supplier<List<MusicTrack>> libraryTracks;
    private volatile Consumer<Location> stateChangedListener = ignored -> { };

    public JukeboxQueueService(MusicTrackSelector trackSelector,
                               Supplier<List<MusicTrack>> libraryTracks) {
        this.trackSelector = Objects.requireNonNull(trackSelector, "trackSelector");
        this.libraryTracks = Objects.requireNonNull(libraryTracks, "libraryTracks");
    }

    /** Returns the mutable state owned by one jukebox, creating it when needed. */
    public JukeboxState state(Location location) {
        Location target = blockLocation(location);
        return states.computeIfAbsent(target,
                ignored -> new JukeboxState(() -> stateChanged(target)));
    }

    public void setStateChangedListener(Consumer<Location> listener) {
        stateChangedListener = Objects.requireNonNull(listener, "listener");
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
        return state(location).nextTrack(
                currentTrack, naturalCompletion, trackSelector, currentLibraryTracks());
    }

    MusicTrack trackById(Location location, String trackId) {
        if (trackId == null) {
            return null;
        }
        JukeboxState data = findState(location);
        MusicTrack queuedTrack = data == null ? null : data.trackById(trackId);
        if (queuedTrack != null) {
            return queuedTrack;
        }
        return currentLibraryTracks().stream()
                .filter(track -> trackId.equals(track.id()))
                .findFirst()
                .orElse(null);
    }

    private List<MusicTrack> currentLibraryTracks() {
        List<MusicTrack> tracks = libraryTracks.get();
        return tracks == null ? List.of() : tracks;
    }

    private void stateChanged(Location location) {
        stateChangedListener.accept(location.clone());
    }

    private static Location blockLocation(Location location) {
        Objects.requireNonNull(location, "location");
        return new Location(location.getWorld(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }

    /** Main-thread-confined queue and playback-mode state for one jukebox. */
    public static final class JukeboxState {
        private final List<MusicTrack> queue = new ArrayList<>();
        private final Runnable stateChanged;
        private JukeboxPlaybackMode playbackMode = JukeboxPlaybackMode.REPEAT_ALL;

        JukeboxState() {
            this(() -> { });
        }

        private JukeboxState(Runnable stateChanged) {
            this.stateChanged = Objects.requireNonNull(stateChanged, "stateChanged");
        }

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
                stateChanged.run();
            }
        }

        public void removeFromQueue(MusicTrack music) {
            if (queue.removeIf(existing -> sameTrack(existing, music))) {
                stateChanged.run();
            }
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
            if (fromIndex < 0 || fromIndex >= queue.size()
                    || toIndex < 0 || toIndex >= queue.size()
                    || fromIndex == toIndex) {
                return;
            }
            MusicTrack music = queue.remove(fromIndex);
            queue.add(toIndex, music);
            stateChanged.run();
        }

        public void clearQueue() {
            if (queue.isEmpty()) return;
            queue.clear();
            stateChanged.run();
        }

        public JukeboxPlaybackMode cyclePlaybackMode() {
            playbackMode = playbackMode.next();
            stateChanged.run();
            return playbackMode;
        }

        public void setPlaybackMode(JukeboxPlaybackMode playbackMode) {
            JukeboxPlaybackMode next = Objects.requireNonNull(playbackMode, "playbackMode");
            if (this.playbackMode == next) return;
            this.playbackMode = next;
            stateChanged.run();
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
                MusicTrackSelector trackSelector, List<MusicTrack> libraryTracks) {
            if (naturalCompletion && playbackMode == JukeboxPlaybackMode.REPEAT_ONE
                    && currentTrack != null) {
                return currentTrack;
            }
            if (playbackMode == JukeboxPlaybackMode.LIBRARY_SHUFFLE) {
                return trackSelector.select(libraryTracks, candidate -> currentTrack == null
                        || !sameTrack(candidate, currentTrack));
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
