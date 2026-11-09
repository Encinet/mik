package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Samples source geometry before attachment and publishes only after the complete frame is valid. */
final class MenuFrameSampling {
    private MenuFrameSampling() { }

    static <T> boolean publish(List<T> nodes, Predicate<T> companion, Consumer<T> sample,
                               BooleanSupplier attach, Consumer<T> publish) {
        for (T node : nodes) if (!companion.test(node)) sample.accept(node);
        if (!attach.getAsBoolean()) return false;
        for (T node : nodes) if (companion.test(node)) sample.accept(node);
        for (T node : nodes) publish.accept(node);
        return true;
    }
}
