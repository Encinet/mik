package org.encinet.mik.module.music.rhythm.calibration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmCuePresentationLedgerTest {

    @Test
    void actualFrameCommitReplacesThePlannedPresentationForTheSameCue() {
        RhythmCuePresentationLedger ledger = new RhythmCuePresentationLedger();
        ledger.add(new RhythmCuePresentation(1L, 0L, 0, 6,
                1_000_000_000L, 1.0));
        ledger.add(new RhythmCuePresentation(1L, 0L, 0, 6,
                1_012_000_000L, 1.0));

        RhythmCuePresentation selected = ledger.closest(1_015_000_000L,
                ignored -> false).orElseThrow();

        assertEquals(1_012_000_000L, selected.presentedAtNanos());
        assertEquals(1, ledger.snapshot().size());
    }
}
