package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;

import java.util.List;
import java.util.Objects;

/** Resolves overlapping jukebox sound fields into a rejection or shared song clock. */
final class JukeboxPlaybackCoordination {

    private JukeboxPlaybackCoordination() {
    }

    static Decision decide(Location location, String trackId,
                           JukeboxSoundSettings settings,
                           List<ActiveField> activeFields) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(activeFields, "activeFields");
        JukeboxPlaybackGroup playbackGroup = null;
        for (ActiveField active : activeFields) {
            if (!JukeboxAudibleField.overlaps(location, settings,
                    active.location(), active.settings())) {
                continue;
            }
            if (!trackId.equals(active.trackId())) {
                return new Decision(true, null);
            }
            if (playbackGroup == null) {
                playbackGroup = active.playbackGroup();
            }
        }
        return new Decision(false, playbackGroup);
    }

    record ActiveField(
            Location location,
            String trackId,
            JukeboxSoundSettings settings,
            JukeboxPlaybackGroup playbackGroup
    ) {
        ActiveField {
            location = Objects.requireNonNull(location, "location").clone();
            trackId = Objects.requireNonNull(trackId, "trackId");
            settings = Objects.requireNonNull(settings, "settings");
            playbackGroup = Objects.requireNonNull(playbackGroup, "playbackGroup");
        }
    }

    record Decision(
            boolean blocked,
            JukeboxPlaybackGroup playbackGroup
    ) {
    }
}
