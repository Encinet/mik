package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.function.Consumer;
import org.encinet.mik.shared.menu.FloatingMenuPage;

/** Observe an actual published page. The identity changes with its dataset, not a visual refresh. */
public record UiPageObservation(Object identity, Consumer<FloatingMenuPage> visible) {
    public UiPageObservation {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(visible);
    }
}
