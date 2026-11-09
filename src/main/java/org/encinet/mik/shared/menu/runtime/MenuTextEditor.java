package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** One uncommitted field edit; invalid responses stay editable and obsolete dialogs cannot apply text. */
final class MenuTextEditor implements AutoCloseable {
    @FunctionalInterface interface Host {
        /** Shows the complete candidate and returns a dismissal action for this dialog only. */
        Runnable show(String value, Component problem, Consumer<String> accepted, Runnable cancelled);
    }

    private final Host host;
    private final Function<String, Component> validate;
    private final Consumer<String> accepted;
    private final Runnable cancelled;
    private String value;
    private Object token;
    private Runnable dismiss;
    private boolean closed;

    MenuTextEditor(String initial, Host host, Function<String, Component> validate,
                   Consumer<String> accepted, Runnable cancelled) {
        value = Objects.requireNonNull(initial);
        this.host = Objects.requireNonNull(host);
        this.validate = Objects.requireNonNull(validate);
        this.accepted = Objects.requireNonNull(accepted);
        this.cancelled = Objects.requireNonNull(cancelled);
    }

    void open() {
        if (closed || token != null) return;
        show(null);
    }

    private void show(Component problem) {
        Object current = new Object();
        token = current;
        Runnable close = host.show(value, problem, raw -> receive(current, raw), () -> cancel(current));
        if (!closed && token == current) dismiss = close;
        else close.run();
    }

    private void receive(Object current, String raw) {
        if (closed || token != current) return;
        token = null;
        dismiss = null; // The native action already completed and released this dialog.
        value = Objects.requireNonNull(raw).replace("\r\n", "\n").replace('\r', '\n');
        Component problem = validate.apply(value);
        if (problem != null) { show(problem); return; }
        closed = true;
        accepted.accept(value);
    }

    private void cancel(Object current) {
        if (closed || token != current) return;
        close();
        cancelled.run();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        token = null;
        Runnable previous = dismiss;
        dismiss = null;
        if (previous != null) previous.run();
    }
}
