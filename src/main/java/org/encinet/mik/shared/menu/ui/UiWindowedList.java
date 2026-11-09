package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import org.encinet.mik.shared.menu.FloatingMenuPage;

/**
 * An equal-height reading window. A non-published template establishes the row height;
 * only visible items are created and measured.
 * The factory must read an immutable, already loaded snapshot and return stable domain keys;
 * it must not perform I/O, mutate state or depend on how often layout measures the window.
 */
public record UiWindowedList(UiKey key, UiNode header, int itemCount, UiNode rowTemplate,
                             IntFunction<? extends UiNode> item, UiLength gap, int pageIndex,
                             Function<FloatingMenuPage, UiNode> pager, IntConsumer selectPage,
                             UiStyle style, UiSizing sizing, java.util.OptionalInt anchorIndex,
                             java.util.Optional<Observation> observation) implements UiNode {
    /** Identity changes with the data set, independently of ordinary visual refreshes. */
    public record Observation(Object identity, java.util.function.Consumer<FloatingMenuPage> visible) {
        public Observation { Objects.requireNonNull(identity); Objects.requireNonNull(visible); }
    }
    public static final int MAX_VISIBLE_ITEMS = 256;

    public UiWindowedList {
        Objects.requireNonNull(key); Objects.requireNonNull(header); Objects.requireNonNull(rowTemplate);
        Objects.requireNonNull(item); Objects.requireNonNull(gap); Objects.requireNonNull(pager);
        Objects.requireNonNull(selectPage); Objects.requireNonNull(style); Objects.requireNonNull(sizing);
        Objects.requireNonNull(anchorIndex); Objects.requireNonNull(observation);
        if (anchorIndex.isPresent() && anchorIndex.getAsInt() < 0) throw new IllegalArgumentException("Negative window anchor");
        if (itemCount < 0) throw new IllegalArgumentException("Negative window item count");
    }

    /** Declaration traversal must not materialize the collection. Realized rows are checked by the compiler. */
    public List<UiChild> children() {
        return List.of(UiChild.linear(UiKey.named("header"), header, LinearSlot.CONTENT));
    }
}
