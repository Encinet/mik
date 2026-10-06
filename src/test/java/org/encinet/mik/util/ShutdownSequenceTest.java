package org.encinet.mik.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShutdownSequenceTest {

    @Test
    void continuesIndependentCleanupAndReportsAllFailures() {
        ShutdownSequence shutdown = new ShutdownSequence();
        List<String> attempted = new ArrayList<>();
        shutdown.attempt("database", () -> {
            attempted.add("database");
            throw new IOException("write failed");
        });
        shutdown.attempt("listener", () -> attempted.add("listener"));
        shutdown.attempt("external hook", () -> {
            attempted.add("external hook");
            throw new LinkageError("unavailable");
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> shutdown.finish("test module"));
        assertEquals(List.of("database", "listener", "external hook"), attempted);
        assertEquals(2, failure.getSuppressed().length);
        assertInstanceOf(IOException.class, failure.getSuppressed()[0].getCause());
        assertInstanceOf(LinkageError.class, failure.getSuppressed()[1].getCause());
    }
}
