package org.encinet.mik.shared.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** A spatial form with native dialog field editing. Draft values are applied only by its confirmation action. */
public record MenuInputForm(Component title, Component description, List<Field> fields,
                            Component submit, Component cancel, BiFunction<Player, Values, Component> validation,
                            BiConsumer<Player, Values> onSubmit) {
    public MenuInputForm {
        Objects.requireNonNull(title); Objects.requireNonNull(description);
        fields = List.copyOf(fields);
        if (fields.isEmpty()) throw new IllegalArgumentException("An input form needs fields");
        var keys = new HashSet<String>();
        for (var field : fields) if (!keys.add(field.key())) throw new IllegalArgumentException("Duplicate input field: " + field.key());
        Objects.requireNonNull(submit); Objects.requireNonNull(cancel);
        Objects.requireNonNull(validation); Objects.requireNonNull(onSubmit);
    }

    public record Field(String key, Component label, String initial, int maximumLength,
                        boolean multiline, Function<String, Component> preview) {
        public Field {
            if (key == null || !key.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("Invalid field key");
            Objects.requireNonNull(label); Objects.requireNonNull(initial); Objects.requireNonNull(preview);
            if (maximumLength < 1) throw new IllegalArgumentException("Field length must be positive");
        }
        public static Field text(String key, Component label, String initial, int maximumLength) {
            return new Field(key, label, initial, maximumLength, false, Component::text);
        }
        public Field multiline(boolean value) { return new Field(key, label, initial, maximumLength, value, preview); }
        public Field preview(Function<String, Component> renderer) {
            return new Field(key, label, initial, maximumLength, multiline, renderer);
        }
    }

    public record Values(Map<String, String> fields) {
        public Values { fields = Map.copyOf(fields); }
        public String text(String key) {
            String value = fields.get(key);
            if (value == null) throw new IllegalArgumentException("Unknown input field: " + key);
            return value;
        }
    }

    public static Builder builder(Component title) { return new Builder(title); }

    public static final class Builder {
        private final Component title;
        private Component description = Component.empty();
        private final List<Field> fields = new ArrayList<>();
        private Component submit, cancel;
        private BiFunction<Player, Values, Component> validation = (_, _) -> null;
        private BiConsumer<Player, Values> onSubmit;
        private Builder(Component title) { this.title = title; }
        public Builder description(Component value) { description = value; return this; }
        public Builder field(Field value) { fields.add(Objects.requireNonNull(value)); return this; }
        /** A validation problem stays beside the draft; null accepts the values. Runs on the server thread. */
        public Builder validate(BiFunction<Player, Values, Component> value) { validation = value; return this; }
        public Builder submit(Component label, BiConsumer<Player, Values> action) { submit = label; onSubmit = action; return this; }
        public Builder cancel(Component label) { cancel = label; return this; }
        public MenuInputForm build() { return new MenuInputForm(title, description, fields, submit, cancel, validation, onSubmit); }
    }
}
