package org.encinet.mik.shared.menu;

/** Completion and cancellation are delivered once to the still-owned caller scope. */
public record MenuWindowResult<T>(boolean completed, T value, FloatingMenuCloseReason reason) {
    public MenuWindowResult {
        if (completed ? reason != null : reason == null || value != null)
            throw new IllegalArgumentException("A result must contain either a completed value or a cancellation reason");
    }

    public static <T> MenuWindowResult<T> completed(T value) { return new MenuWindowResult<>(true, value, null); }
    public static <T> MenuWindowResult<T> cancelled(FloatingMenuCloseReason reason) {
        return new MenuWindowResult<>(false, null, java.util.Objects.requireNonNull(reason));
    }
}
