package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.Objects;

/** Reading text and its expansion action share one measured truncation decision. */
public record UiExcerpt(UiKey key, UiElement text, UiElement expansion, UiLength gap,
                        UiStyle style, UiSizing sizing) implements UiNode {
    public UiExcerpt {
        Objects.requireNonNull(key); Objects.requireNonNull(text); Objects.requireNonNull(expansion);
        Objects.requireNonNull(gap); Objects.requireNonNull(style); Objects.requireNonNull(sizing);
        if (!(text.content() instanceof UiContent.Text content) || content.maximumLines() == Integer.MAX_VALUE
                || text.input().interactive() || !expansion.input().interactive())
            throw new IllegalArgumentException("An excerpt needs bounded reading text and an expansion control");
    }

    public List<UiChild> children() {
        return List.of(UiChild.linear(UiKey.named("text"), text, LinearSlot.stretch()),
                UiChild.linear(UiKey.named("expansion"), expansion, LinearSlot.CONTENT));
    }
}
