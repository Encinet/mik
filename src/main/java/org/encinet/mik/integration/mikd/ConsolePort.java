package org.encinet.mik.integration.mikd;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Registers plugin-owned console providers with mikd. */
public interface ConsolePort {
    /** A null handler leaves that capability disabled. Each handler runs on its own background worker. */
    Registration register(String id, String label, CommandInput input, LogHistory logs);

    interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    @FunctionalInterface
    interface CommandInput {
        CommandOutcome submit(String line, Work work) throws InterruptedException;
    }

    @FunctionalInterface
    interface LogHistory {
        LogPage read(LogQuery query, Work work) throws IOException, InterruptedException;
    }

    enum CommandOutcome { SUBMITTED, EXPIRED, UNCONFIRMED }

    record Work(long expiresAt, BooleanSupplier connected) {
        public long remainingNanos() {
            return Math.max(0, expiresAt - System.nanoTime());
        }

        public boolean active() {
            return connected.getAsBoolean() && remainingNanos() > 0;
        }
    }

    enum Position { LATEST, BEFORE, AFTER }

    record LogQuery(Position position, String cursor) {}

    /** Up to 256 lines and 64 KiB of UTF-8 text, oldest first. Empty cursors omit a paging action. */
    record LogPage(List<String> lines, String before, String after, String notice) {}
}
