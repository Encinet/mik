package org.encinet.mik.feature.entertainment.music.command;

import java.util.UUID;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicCommandRegistrarTest {
    @Test void invalidPlaylistArgumentsRemainInputErrors() {
        for (String value : new String[] {"", "not-a-playlist", "1234"}) {
            var error = assertThrows(MusicProblem.class, () -> MusicCommandRegistrar.playlistId(value));
            assertEquals(MusicProblem.Code.INVALID, MusicProblem.code(error));
        }
        UUID id = UUID.randomUUID();
        assertEquals(id, MusicCommandRegistrar.playlistId(id.toString()));
    }
}
