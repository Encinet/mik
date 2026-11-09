package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.Objects;

/** Transforms an owned subtree after its 2D layout, including its planes, images and input geometry. */
public record UiSpatialGroup(UiKey key, UiNode content, UiStyle style, UiSizing sizing,
                             UiTransform transform, UiPivot pivot) implements UiBranch {
    public UiSpatialGroup {
        Objects.requireNonNull(key); Objects.requireNonNull(content); Objects.requireNonNull(style);
        Objects.requireNonNull(sizing); Objects.requireNonNull(transform); Objects.requireNonNull(pivot);
    }
    @Override public UiLayout layout() {
        return (scope, constraints) -> {
            var result = UiLayouts.stack().layout(scope, constraints);
            return result.focusing(UiKey.named("content"));
        };
    }
    @Override public List<UiChild> children() { return List.of(UiChild.linear(UiKey.named("content"), content, LinearSlot.CONTENT)); }
}
