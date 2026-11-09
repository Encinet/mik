package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.MenuInputLease;
import org.encinet.mik.shared.menu.MenuQuerySession;
import org.encinet.mik.shared.menu.ui.*;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** A controlled query field: the host owns the value and results, this editor owns only chat and feedback. */
final class MenuQueryEditor implements MenuQuerySession {
    record Labels(Component prompt, Component clear, Component cancel, Component singleLine,
                  IntFunction<Component> tooLong) { }

    private final Component placeholder;
    private final int maximumLength;
    private final Labels labels;
    private final BiFunction<Consumer<String>, Runnable, MenuInputLease> listen;
    private final Consumer<String> update;
    private final Runnable redraw;
    private final Runnable cancelled;
    private MenuInputLease input;
    private Component problem;
    private boolean closed;

    MenuQueryEditor(Component placeholder, int maximumLength, Labels labels,
                    BiFunction<Consumer<String>, Runnable, MenuInputLease> listen,
                    Consumer<String> update, Runnable redraw, Runnable cancelled) {
        this.placeholder = Objects.requireNonNull(placeholder);
        if (maximumLength < 1) throw new IllegalArgumentException("Query length must be positive");
        this.maximumLength = maximumLength;
        this.labels = Objects.requireNonNull(labels);
        this.listen = Objects.requireNonNull(listen);
        this.update = Objects.requireNonNull(update);
        this.redraw = Objects.requireNonNull(redraw);
        this.cancelled = Objects.requireNonNull(cancelled);
    }

    @Override public UiNode content(String value) {
        Objects.requireNonNull(value);
        var field = Ui.textField(Component.text(value, NamedTextColor.WHITE)).key("query")
                .placeholder(input == null ? placeholder : labels.prompt())
                .editing(input != null).primary((_, _) -> focus());
        if (input != null) field.actions(Ui.button(UiIcons.CROSS, labels.cancel()).key("cancel")
                .primary((_, _) -> cancelInput()).build());
        else if (!value.isEmpty()) field.actions(Ui.button(UiIcons.CROSS, labels.clear()).key("clear")
                .primary((_, _) -> clear()).build());
        var control = field.build();
        var content = Ui.column().key("query-input").gap(UiLength.ui(4))
                .sizing(UiSizing.atLeast(control.sizing().minimum()))
                .child(control, LinearSlot.stretch());
        if (problem != null) content.child(Ui.text(problem).key("problem").textWidth(4096)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build(),
                LinearSlot.fit(LinearSlot.Alignment.START));
        return content.build();
    }

    boolean editing() { return input != null; }

    void focus() {
        if (closed || input != null) return;
        problem = null;
        input = listen.apply(this::receive, () -> {
            input = null;
            if (!closed) redraw.run();
        });
        redraw.run();
    }

    private void receive(String raw) {
        if (closed || input == null) return;
        String value = raw.strip();
        int length = value.codePointCount(0, value.length());
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) problem = labels.singleLine();
        else problem = length > maximumLength ? labels.tooLong().apply(length) : null;
        // Submit also leaves the field on rejection. Release capture before applying
        // so the host's redraw already has the idle field and no cancellation action.
        if (!input.complete() || closed) return;
        if (problem == null) update.accept(value);
    }

    private void clear() {
        if (closed) return;
        // Clearing returns to browsing and discards any text sent before this click.
        stopInput();
        problem = null;
        update.accept("");
    }

    private void cancelInput() {
        if (closed || input == null) return;
        problem = null;
        stopInput();
        cancelled.run();
    }

    void stopInput() {
        var previous = input;
        input = null;
        if (previous != null) previous.close();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        stopInput();
    }
}
