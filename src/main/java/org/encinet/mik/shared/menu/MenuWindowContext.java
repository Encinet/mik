package org.encinet.mik.shared.menu;

/** The exact window exists before content construction, so callbacks never need a mutable handle holder. */
public record MenuWindowContext<T>(MenuWindowHandle<T> handle, Runnable redraw) {
    public MenuWindowContext { java.util.Objects.requireNonNull(handle); java.util.Objects.requireNonNull(redraw); }
}
