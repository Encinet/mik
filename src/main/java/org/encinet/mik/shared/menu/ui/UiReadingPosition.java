package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.Optional;

/** A paragraph and code-point offset, with a nearby ordinal fallback if that paragraph disappears. */
public record UiReadingPosition(Optional<UiKey> block, int offset, int index) {
    public static final UiReadingPosition START = new UiReadingPosition(Optional.empty(), 0, 0);

    public UiReadingPosition {
        Objects.requireNonNull(block);
        if (offset < 0 || index < 0) throw new IllegalArgumentException("Negative reading position");
    }
    public UiReadingPosition(UiKey block, int offset, int index) { this(Optional.of(block), offset, index); }
}
