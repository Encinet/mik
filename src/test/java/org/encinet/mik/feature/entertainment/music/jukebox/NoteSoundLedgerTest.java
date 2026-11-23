package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.protocol.music.NoteStop;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteSoundLedgerTest {

    private static final UUID LISTENER = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    @Test
    void soundStopUsesItsInclusiveVoiceRange() {
        NoteSoundLedger ledger = new NoteSoundLedger();
        ledger.record(0, "minecraft:block.note_block.harp", LISTENER);
        ledger.record(1, "minecraft:block.note_block.bass", LISTENER);
        ledger.record(2, "minecraft:block.note_block.snare", LISTENER);

        Set<NoteSoundLedger.StopRequest> requests = ledger.stop(NoteStop.newBuilder().setFirstVoice(1).setLastVoice(1).build());

        assertEquals(Set.of(new NoteSoundLedger.StopRequest(
                LISTENER, "minecraft:block.note_block.bass")), requests);
        assertEquals(Set.of(
                new NoteSoundLedger.StopRequest(
                        LISTENER, "minecraft:block.note_block.harp"),
                new NoteSoundLedger.StopRequest(
                        LISTENER, "minecraft:block.note_block.snare")),
                ledger.stopAll());
    }

    @Test
    void absentRangeStopsEveryRecordedVoice() {
        NoteSoundLedger ledger = new NoteSoundLedger();
        ledger.record(0, "harp", LISTENER);
        ledger.record(20, "bass", LISTENER);

        assertEquals(Set.of(
                new NoteSoundLedger.StopRequest(LISTENER, "harp"),
                new NoteSoundLedger.StopRequest(LISTENER, "bass")),
                ledger.stop(NoteStop.getDefaultInstance()));
        assertEquals(Set.of(), ledger.stopAll());
    }

}
