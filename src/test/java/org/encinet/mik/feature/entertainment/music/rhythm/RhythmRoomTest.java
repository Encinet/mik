package org.encinet.mik.feature.entertainment.music.rhythm;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class RhythmRoomTest {
    @Test
    void preferencesAndReadyDoNotStartAnyone() {
        UUID owner = UUID.randomUUID(), guest = UUID.randomUUID();
        RhythmRoom room = new RhythmRoom(UUID.randomUUID(), owner);
        room.join(guest);
        room.preference(guest, RhythmGameMode.RADIAL, RhythmDifficulty.NORMAL);
        room.ready(guest, true);
        assertFalse(room.member(guest).playing());
        assertFalse(room.member(owner).ready());
        assertThrows(SecurityException.class, () -> room.start(guest));
        assertEquals(List.of(guest), room.start(owner).stream().map(RhythmRoom.Member::player).toList());
        assertTrue(room.member(guest).playing());
        assertFalse(room.member(owner).playing());
    }
    @Test
    void resultOrTrackChangeRequiresNewReadyAndLeavesOthersAlone() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        RhythmRoom r = new RhythmRoom(UUID.randomUUID(), a);
        r.join(b);
        r.ready(a, true);
        r.ready(b, true);
        r.start(a);
        r.finish(a);
        assertFalse(r.member(a).ready());
        assertTrue(r.member(b).playing());
        r.leave(a);
        assertEquals(1, r.members().size());
        r.changedTrack();
        assertFalse(r.member(b).playing());
    }
}
