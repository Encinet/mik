package org.encinet.mik.feature.entertainment.music.gateway;

import java.time.Duration;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.protocol.music.MusicTrackData;

final class MusicProto {
    private MusicProto() { }

    static MusicTrack decodeTrack(MusicTrackData track) {
        var target = new TrackTarget.Managed(track.getTrackId());
        var availability = track.getState() == MusicTrackData.State.RETIRED ? MusicTrack.Availability.UNAVAILABLE
                : switch (track.getAvailability()) {
                    case READY -> MusicTrack.Availability.READY;
                    case NEEDS_PREPARATION -> MusicTrack.Availability.NEEDS_PREPARATION;
                    case UNAVAILABLE -> MusicTrack.Availability.UNAVAILABLE;
                    default -> MusicTrack.Availability.UNKNOWN;
                };
        var duration = track.hasDurationMs() && track.getDurationMs() > 0 ? Duration.ofMillis(track.getDurationMs()) : null;
        String title = track.getTitle().isBlank() ? target.trackId() : track.getTitle();
        return new MusicTrack(target.trackId(), new TrackDetails(title, track.getArtist(),
                track.getOriginalAuthor(), track.getAlbum(), duration), target, availability);
    }
}
