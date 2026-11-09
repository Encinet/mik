package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.ui.UiFrame;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Delivers only published windows, outside compilation, and retires stale deliveries after reentry. */
final class MenuWindowObserver {
    private record Stamp(Object identity, FloatingMenuPage page) { }
    private final Map<String,Stamp> delivered = new HashMap<>();

    void publish(UiFrame frame, BooleanSupplier current, Consumer<RuntimeException> failure) {
        var present = new HashSet<String>();
        for (var window : frame.pagination()) {
            if (!current.getAsBoolean()) return;
            if (window.observation().isEmpty()) continue;
            present.add(window.path());
            var observation = window.observation().orElseThrow();
            var stamp = new Stamp(observation.identity(),window.page());
            if (stamp.equals(delivered.put(window.path(),stamp))) continue;
            try { observation.visible().accept(window.page()); }
            catch (RuntimeException error) { failure.accept(error); }
        }
        delivered.keySet().retainAll(present);
    }
    void clear() { delivered.clear(); }
}
