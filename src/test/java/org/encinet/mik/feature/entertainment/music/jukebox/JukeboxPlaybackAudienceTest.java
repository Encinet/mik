package org.encinet.mik.feature.entertainment.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.encinet.mik.feature.entertainment.music.jukebox.PlaybackSession.OutputKind.*;
import static org.junit.jupiter.api.Assertions.*;

class JukeboxPlaybackAudienceTest {
    @Test void onlyEncodedAudioRequiresVoiceChat() {
        assertTrue(JukeboxPlaybackAudience.canHearBackend(NOTE_EVENTS, false));
        assertTrue(JukeboxPlaybackAudience.canHearBackend(NATIVE_RECORD, false));
        assertFalse(JukeboxPlaybackAudience.canHearBackend(ENCODED_AUDIO, false));
        assertTrue(JukeboxPlaybackAudience.canHearBackend(ENCODED_AUDIO, true));
    }
}
