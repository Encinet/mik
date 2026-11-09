package org.encinet.mik.feature.entertainment.music.playback;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
class PlaybackIntentsTest {
    @Test
    void stopAfterSubmissionInvalidatesTheDelayedCommitAndPreparedCallback() {
        PlaybackIntents intents = new PlaybackIntents();
        UUID id = UUID.randomUUID();
        long submitted = intents.begin(id);
        intents.cancel(id);
        assertFalse(intents.current(id, submitted));
        long resumed = intents.begin(id);
        assertTrue(intents.current(id, resumed));
        assertFalse(intents.current(id, submitted));
    }
    @Test
    void stoppingOneDeviceDoesNotInvalidateAnotherTarget() {
        PlaybackIntents intents = new PlaybackIntents();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        long first = intents.begin(a), second = intents.begin(b);
        intents.cancel(a);
        assertFalse(intents.current(a, first));
        assertTrue(intents.current(b, second));
    }
}
