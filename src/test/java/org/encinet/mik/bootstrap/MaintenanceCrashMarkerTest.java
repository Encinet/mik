package org.encinet.mik.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceCrashMarkerTest {
    @TempDir Path directory;

    @Test
    void onlyACleanShutdownPreventsRecoveryOnTheNextRun() throws IOException {
        Path file = directory.resolve("plugin").resolve(MaintenanceCrashMarker.FILE_NAME);
        assertFalse(marker(file).begin());
        assertEquals("running", Files.readString(file));
        assertTrue(marker(file).begin(), "an unfinished previous run must be recovered");

        marker(file).clearAfterCleanShutdown();
        assertFalse(Files.exists(file));
        assertFalse(marker(file).begin());
    }

    @Test
    void unavailableMarkerStorageDoesNotAbortUnrelatedGameFeatures() throws IOException {
        Path parent = directory.resolve("file-instead-of-directory");
        Files.writeString(parent, "preserve");

        assertFalse(marker(parent.resolve(MaintenanceCrashMarker.FILE_NAME)).begin());
        assertEquals("preserve", Files.readString(parent));
    }

    private static MaintenanceCrashMarker marker(Path file) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new MaintenanceCrashMarker(file, logger);
    }
}
