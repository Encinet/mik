package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.Optional;

public record UiChild(UiKey scope, UiNode node, LinearSlot linear, Optional<GridSlot> grid) {
    public UiChild {
        scope = Objects.requireNonNull(scope, "scope"); node = Objects.requireNonNull(node, "node");
        linear = Objects.requireNonNull(linear, "linear"); grid = Objects.requireNonNull(grid, "grid");
    }

    public static UiChild of(UiNode node) { return new UiChild(node.key(), node, LinearSlot.CONTENT, Optional.empty()); }
    public static UiChild linear(UiKey key, UiNode node, LinearSlot slot) { return new UiChild(key, node, slot, Optional.empty()); }
    public static UiChild grid(UiKey key, UiNode node, GridSlot slot) { return new UiChild(key, node, LinearSlot.CONTENT, Optional.of(slot)); }
    public static UiChild grid(UiNode node, GridSlot slot) { return new UiChild(node.key(), node, LinearSlot.CONTENT, Optional.of(slot)); }
}
