package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxAudioAudienceTest {
    @Test
    void nestedSilenceLeasesRestoreOnlyAfterTheLastOwnerCloses() {
        JukeboxAudioAudience audience = new JukeboxAudioAudience();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertTrue(audience.canHear(player));
        var first = audience.silence(player);
        var second = audience.silence(player);

        assertFalse(audience.canHear(player));
        assertTrue(audience.canHear(other));
        first.close();
        first.close();
        assertFalse(audience.canHear(player));

        second.close();
        assertTrue(audience.canHear(player));
    }
}
