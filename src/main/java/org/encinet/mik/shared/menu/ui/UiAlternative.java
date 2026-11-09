package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** An authored size fallback. Only the first fitting representation publishes content and actions. */
public record UiAlternative(UiNode preferred, UiNode fallback) implements UiNode {
    public UiAlternative {
        Objects.requireNonNull(preferred); Objects.requireNonNull(fallback);
        if (!preferred.key().equals(fallback.key())) throw new IllegalArgumentException("Alternative roots need the same object key");
    }
    @Override public UiKey key() { return preferred.key(); }
    @Override public UiStyle style() { return UiStyle.PLAIN; }
    @Override public UiSizing sizing() { return UiSizing.CONTENT; }
}
