package org.encinet.mik.shared.menu.ui;

import org.encinet.mik.shared.menu.FloatingMenuPage;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** A bounded collection whose ordinary layout is reused for page measurement and realization. */
public record UiPagedLayout(UiContainer content, UiLength gap, int pageIndex,
                            Function<FloatingMenuPage, UiNode> pager, IntConsumer selectPage) implements UiNode {
    public UiPagedLayout {
        Objects.requireNonNull(content); Objects.requireNonNull(gap);
        Objects.requireNonNull(pager); Objects.requireNonNull(selectPage);
    }
    @Override public UiKey key() { return content.key(); }
    @Override public UiStyle style() { return content.style(); }
    @Override public UiSizing sizing() { return content.sizing(); }
}
