package org.encinet.mik.shared.menu;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiLength;

/** A query produces candidates; only an explicit result click selects a domain object. */
public record MenuSearch<T>(Component title, Component placeholder, int maximumLength,
                            Function<String, CompletionStage<List<T>>> find,
                            Function<T, UiKey> key, Function<T, Component> label,
                            BiConsumer<Player, T> selected, Optional<FlowResults> flowResults) {
    /** A bounded collection of single-line results, packed into measured rows. */
    public record FlowResults(int rows, UiLength maximumWidth) {
        public FlowResults {
            Objects.requireNonNull(maximumWidth);
            if (rows < 1 || maximumWidth.value() <= 0) throw new IllegalArgumentException("Search flow needs rows and a positive width");
        }
    }

    public MenuSearch {
        Objects.requireNonNull(title); Objects.requireNonNull(placeholder); Objects.requireNonNull(find);
        Objects.requireNonNull(key); Objects.requireNonNull(label); Objects.requireNonNull(selected);
        Objects.requireNonNull(flowResults);
        if (maximumLength < 1) throw new IllegalArgumentException("Search length must be positive");
    }

    public static <T> Builder<T> builder(Component title, Component placeholder) { return new Builder<>(title, placeholder); }

    public static final class Builder<T> {
        private final Component title, placeholder;
        private int maximumLength = 64;
        private Function<String, CompletionStage<List<T>>> find;
        private Function<T, UiKey> key;
        private Function<T, Component> label;
        private BiConsumer<Player, T> selected;
        private Optional<FlowResults> flowResults = Optional.empty();
        private Builder(Component title, Component placeholder) { this.title = title; this.placeholder = placeholder; }
        public Builder<T> maximumLength(int value) { maximumLength = value; return this; }
        public Builder<T> find(Function<String, CompletionStage<List<T>>> value) { find = value; return this; }
        public Builder<T> key(Function<T, UiKey> value) { key = value; return this; }
        public Builder<T> label(Function<T, Component> value) { label = value; return this; }
        public Builder<T> selected(BiConsumer<Player, T> value) { selected = value; return this; }
        public Builder<T> flowResults(int rows, UiLength maximumWidth) {
            flowResults = Optional.of(new FlowResults(rows, maximumWidth));
            return this;
        }
        public MenuSearch<T> build() { return new MenuSearch<>(title, placeholder, maximumLength, find, key, label, selected, flowResults); }
    }
}
