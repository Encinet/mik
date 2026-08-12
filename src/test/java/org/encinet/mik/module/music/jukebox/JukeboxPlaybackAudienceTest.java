package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxPlaybackAudienceTest {

    @Test
    void nbsPlaybackRemainsAvailableWithoutPlasmoVoice() {
        assertTrue(JukeboxPlaybackAudience.canHearBackend(
                new TrackTarget.NbsFile(Path.of("song.nbs")), false));
    }

    @Test
    void ordinaryAndOnlineAudioRequireAvailablePlasmoVoice() {
        TrackTarget local = new TrackTarget.LocalFile(Path.of("song.mp3"));
        TrackTarget online = new TrackTarget.Lx("wy", "song", List.of("320k"), "{}");

        assertFalse(JukeboxPlaybackAudience.canHearBackend(local, false));
        assertFalse(JukeboxPlaybackAudience.canHearBackend(online, false));
        assertTrue(JukeboxPlaybackAudience.canHearBackend(local, true));
        assertTrue(JukeboxPlaybackAudience.canHearBackend(online, true));
    }
}
