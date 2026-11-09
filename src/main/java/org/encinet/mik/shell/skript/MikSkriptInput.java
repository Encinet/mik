package org.encinet.mik.shell.skript;

import ch.njol.skript.lang.Expression;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Checks the values crossing Skript's erased expression boundary without casting its generic container. */
record MikSkriptInput<T>(Expression<?> expression, Class<T> type) {
    MikSkriptInput { Objects.requireNonNull(expression); Objects.requireNonNull(type); }
    static <T> @Nullable MikSkriptInput<T> of(@Nullable Expression<?> expression, Class<T> type) {
        return expression == null ? null : new MikSkriptInput<>(expression, type);
    }

    @Nullable T single(Event event) { return type.cast(expression.getSingle(event)); }
    List<T> values(Event event) { return Arrays.stream(expression.getArray(event)).map(type::cast).toList(); }
    boolean isSingle() { return expression.isSingle(); }
    boolean check(Event event, Predicate<T> condition, boolean negated) {
        return expression.check(event, value -> condition.test(type.cast(value)), negated);
    }
    String describe(@Nullable Event event, boolean debug) { return expression.toString(event, debug); }
}
