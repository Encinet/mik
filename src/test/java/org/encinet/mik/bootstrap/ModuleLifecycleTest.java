package org.encinet.mik.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleLifecycleTest {
    @Test
    void dependenciesStartFirstAndStopLastWithoutRepeatingCleanup() {
        var lifecycle = lifecycle();
        var calls = new ArrayList<String>();
        lifecycle.startManaged("shared", () -> calls.add("start shared"), () -> calls.add("stop shared"));
        lifecycle.startManaged("feature", () -> calls.add("start feature"), () -> calls.add("stop feature"));

        assertTrue(lifecycle.stopAll(null));
        assertTrue(lifecycle.stopAll(null));
        assertEquals(List.of("start shared", "start feature", "stop feature", "stop shared"), calls);
    }

    @Test
    void partialStartupIsCleanedBeforePreviouslyStartedDependencies() {
        var lifecycle = lifecycle();
        var calls = new ArrayList<String>();
        var startupError = new IllegalStateException("startup failed");
        lifecycle.startManaged("shared", () -> calls.add("start shared"), () -> calls.add("stop shared"));

        assertSame(startupError, assertThrows(IllegalStateException.class, () ->
                lifecycle.startManaged("feature", () -> {
                    calls.add("start feature");
                    throw startupError;
                }, () -> calls.add("stop partial feature"))));
        assertTrue(lifecycle.stopAll(startupError));
        assertEquals(List.of("start shared", "start feature", "stop partial feature", "stop shared"), calls);
    }

    @Test
    void allCleanupFailuresArePreservedWithoutSkippingIndependentResources() {
        var lifecycle = lifecycle();
        var calls = new ArrayList<String>();
        var startupError = new IllegalStateException("startup failed");
        var sharedError = new IllegalStateException("shared cleanup failed");
        var featureError = new NoClassDefFoundError("feature cleanup failed");
        lifecycle.startManaged("shared", () -> { }, () -> { calls.add("shared"); throw sharedError; });
        lifecycle.startManaged("other", () -> { }, () -> calls.add("other"));

        assertSame(startupError, assertThrows(IllegalStateException.class, () ->
                lifecycle.startManaged("feature", () -> { throw startupError; }, () -> { throw featureError; })));
        assertFalse(lifecycle.stopAll(startupError));
        assertEquals(List.of(featureError, sharedError), List.of(startupError.getSuppressed()));
        assertEquals(List.of("other", "shared"), calls);
        assertTrue(lifecycle.stopAll(null));
    }

    @Test
    void missingOptionalClassesDuringStartupStillRunPartialCleanup() {
        var lifecycle = lifecycle();
        var failure = new NoClassDefFoundError("unavailable plugin API");
        var calls = new ArrayList<String>();

        assertSame(failure, assertThrows(NoClassDefFoundError.class, () ->
                lifecycle.startManaged("adapter", () -> { throw failure; }, () -> calls.add("cleanup"))));
        assertEquals(List.of("cleanup"), calls);
        assertTrue(lifecycle.stopAll(failure));
    }

    private static ModuleLifecycle lifecycle() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new ModuleLifecycle(logger);
    }
}
