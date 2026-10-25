package org.encinet.mik.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/** Records an unfinished plugin run so admission can resume in maintenance mode. */
final class MaintenanceCrashMarker {
    static final String FILE_NAME = "maintenance-unclean-shutdown.marker";

    private final Path file;
    private final Logger logger;

    MaintenanceCrashMarker(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    boolean begin() {
        boolean resumeFromCrash = false;
        try {
            Files.createDirectories(file.getParent());
            resumeFromCrash = Files.exists(file);
            Files.writeString(file, "running", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException error) {
            logger.warning("Unable to initialize maintenance crash marker: " + error.getMessage());
        }
        return resumeFromCrash;
    }

    void clearAfterCleanShutdown() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException error) {
            logger.warning("Unable to clear maintenance crash marker: " + error.getMessage());
        }
    }
}
