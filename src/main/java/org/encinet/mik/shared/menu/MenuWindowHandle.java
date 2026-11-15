package org.encinet.mik.shared.menu;

import java.util.function.BooleanSupplier;

/** Exact identity of one retained window, independent of which window currently has focus. */
public interface MenuWindowHandle<T> extends FloatingMenuHandle {
    boolean focused();
    void activate();
    void minimize();
    void restore();
    void expanded(boolean value);
    void position(MenuWindowSpec.Position value);
    void complete(T value);
    /** Captures this page instance; late callbacks cannot affect a successor page or reopened window. */
    BooleanSupplier current();
}
