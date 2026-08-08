package org.encinet.mik.module.music.rhythm.calibration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void sampledCuesAreSkippedAndCaptureWindowIsExact() {
        RhythmCuePresentationLedger ledger = new RhythmCuePresentationLedger();
        ledger.add(presentation(1L, 1_000_000_000L));
        ledger.add(presentation(2L, 1_800_000_000L));

        assertEquals(2L, ledger.closestWithin(1_400_000_000L,
                400_000_000L, cueId -> cueId == 1L)
                .orElseThrow().cueId());
        assertTrue(ledger.closestWithin(1_399_999_999L,
                400_000_000L, cueId -> cueId == 1L).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> ledger.closestWithin(0L, -1L, ignored -> false));
    }

    @Test
    void retentionDoesNotAssumePresentationsArriveInTimestampOrder() {
        RhythmCuePresentationLedger ledger = new RhythmCuePresentationLedger();
        ledger.add(presentation(1L, 5_000_000_000L));
        ledger.add(presentation(2L, 1_000_000_000L));
        ledger.add(presentation(3L, 6_000_000_001L));

        assertEquals(java.util.List.of(1L, 3L), ledger.snapshot().stream()
                .map(RhythmCuePresentation::cueId).toList());
    }

    @Test
    void closestCueAndRetentionSurviveNanoTimeSignedWrap() {
        RhythmCuePresentationLedger ledger = new RhythmCuePresentationLedger();
        long beforeWrap = Long.MAX_VALUE - 20L;
        long afterWrap = Long.MIN_VALUE + 30L;
        ledger.add(presentation(1L, beforeWrap));
        ledger.add(presentation(2L, afterWrap + 800_000_000L));

        assertEquals(1L, ledger.closestWithin(afterWrap, 100L,
                ignored -> false).orElseThrow().cueId());
        assertEquals(2, ledger.snapshot().size());
    }

    @Test
    void cueIdentityStartsAtOne() {
        assertThrows(IllegalArgumentException.class,
                () -> presentation(0L, 1L));
    }

    private static RhythmCuePresentation presentation(long cueId,
                                                       long presentedAtNanos) {
        int cueIndex = (int) ((cueId - 1L) % 6L);
        long cycle = (cueId - 1L) / 6L;
        return new RhythmCuePresentation(cueId, cycle, cueIndex, 6,
                presentedAtNanos, 1.0);
    }
}
