package org.encinet.mik.module.plot.board;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotBoardAlertLedgerTest {
    @TempDir Path directory;

    @Test
    void firstStartSkipsOldRecordsButNoticesAfterRestartAreDeliveredOnce() throws Exception {
        Path file = directory.resolve("alerts.yml");
        long start = 1_800_000_000_000L;
        PlotBoardAlertLedger ledger = PlotBoardAlertLedger.open(file, start);
        assertFalse(ledger.shouldAlert("posted:old", start - 1, start + 10));
        assertTrue(ledger.shouldAlert("posted:new", start + 1, start + 10));
        ledger.mark("posted:new");
        assertTrue(ledger.shouldDeliverToPlayer("posted:new", "player", start + 1, start + 10));
        ledger.markDelivered("posted:new", "player");
        ledger.save();

        PlotBoardAlertLedger reloaded = PlotBoardAlertLedger.open(file, start + 20);
        assertFalse(reloaded.shouldAlert("posted:new", start + 1, start + 20));
        assertFalse(reloaded.shouldDeliverToPlayer("posted:new", "player", start + 1, start + 20));
        assertTrue(reloaded.shouldDeliverToPlayer("posted:new", "another", start + 1, start + 20));
        assertTrue(reloaded.shouldAlert("ruled:new", start + 15, start + 20));
        assertFalse(reloaded.shouldAlert("posted:stale", start + 1,
                start + 73L * 60 * 60 * 1000));
    }
}
