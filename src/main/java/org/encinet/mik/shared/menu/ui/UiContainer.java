package org.encinet.mik.shared.menu.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record UiContainer(UiKey key, UiLayout layout, List<UiChild> children,
                          UiStyle style, UiSizing sizing) implements UiBranch {
    public UiContainer {
        key = Objects.requireNonNull(key, "key"); layout = Objects.requireNonNull(layout, "layout");
        children = List.copyOf(children); style = Objects.requireNonNull(style, "style");
        sizing = Objects.requireNonNull(sizing, "sizing");
        HashSet<UiKey> used = new HashSet<>();
        for (UiChild child : children) if (!used.add(child.scope())) {
            throw new IllegalArgumentException("Duplicate child key '" + child.scope().value() + "' in " + key.value());
        }
    }
}
