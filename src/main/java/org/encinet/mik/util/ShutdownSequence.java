package org.encinet.mik.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runs independent shutdown steps and reports every failure to the composition root. */
public final class ShutdownSequence {
    private final List<IllegalStateException> failures = new ArrayList<>();

    public void attempt(String step, Action action) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(action, "action");
        try {
            action.run();
        } catch (Exception | LinkageError error) {
            failures.add(new IllegalStateException("Could not stop " + step, error));
        }
    }

    public void finish(String owner) {
        if (failures.isEmpty()) return;
        IllegalStateException failure = new IllegalStateException(
                "Could not fully stop " + owner);
        failures.forEach(failure::addSuppressed);
        throw failure;
    }

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }
}
