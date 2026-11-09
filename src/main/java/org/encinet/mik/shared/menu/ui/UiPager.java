package org.encinet.mik.shared.menu.ui;

import org.encinet.mik.shared.menu.FloatingMenuPage;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Paging controls also declare wheel navigation for their immediate collection container. */
public record UiPager(UiContainer controls, FloatingMenuPage page, IntConsumer selectPage) implements UiBranch {
    public UiPager {
        Objects.requireNonNull(controls);
        Objects.requireNonNull(page);
        Objects.requireNonNull(selectPage);
    }

    @Override public UiKey key() { return controls.key(); }
    @Override public UiStyle style() { return controls.style(); }
    @Override public UiSizing sizing() { return controls.sizing(); }
    @Override public UiLayout layout() { return controls.layout(); }
    @Override public List<UiChild> children() { return controls.children(); }
}
