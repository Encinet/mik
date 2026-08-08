package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsNoteType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NbsSoundLedgerTest {

    private static final UUID LISTENER = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    @Test
    void soundStopperUsesItsInclusiveOneBasedLayerRange() {
        NbsSoundLedger ledger = new NbsSoundLedger();
        ledger.record(0, "minecraft:block.note_block.harp", LISTENER);
        ledger.record(1, "minecraft:block.note_block.bass", LISTENER);
        ledger.record(2, "minecraft:block.note_block.snare", LISTENER);

        Set<NbsSoundLedger.StopRequest> requests = ledger.stop(stopper(2, 2));

        assertEquals(Set.of(new NbsSoundLedger.StopRequest(
                LISTENER, "minecraft:block.note_block.bass")), requests);
        assertEquals(Set.of(
                new NbsSoundLedger.StopRequest(
                        LISTENER, "minecraft:block.note_block.harp"),
                new NbsSoundLedger.StopRequest(
                        LISTENER, "minecraft:block.note_block.snare")),
                ledger.stopAll());
    }

    @Test
    void zeroStartLayerStopsEveryRecordedLayer() {
        NbsSoundLedger ledger = new NbsSoundLedger();
        ledger.record(0, "harp", LISTENER);
        ledger.record(20, "bass", LISTENER);

        assertEquals(Set.of(
                new NbsSoundLedger.StopRequest(LISTENER, "harp"),
                new NbsSoundLedger.StopRequest(LISTENER, "bass")),
                ledger.stop(stopper(0, 0)));
        assertEquals(Set.of(), ledger.stopAll());
    }

    private static NbsNote stopper(int firstLayer, int lastLayer) {
        int encodedPanning = Math.floorMod(lastLayer, 256) + 100;
        int encodedVelocity = Math.floorDiv(lastLayer, 256) + 100;
        return new NbsNote(0, 0, 45, encodedVelocity, 100, 0,
                firstLayer, 0, 20, encodedPanning - 100, 45,
                NbsNoteType.SOUND_STOP);
    }
}
