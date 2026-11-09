package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.function.Consumer;

/** Construction-time adapter from declared source blocks to the shared document reader. */
public record UiDocumentPort(UiPort content, UiReadingPosition position, Consumer<UiReadingPosition> selectPosition,
                             boolean bodyReading) implements UiNode {
    public UiDocumentPort {
        Objects.requireNonNull(content); Objects.requireNonNull(position); Objects.requireNonNull(selectPosition);
    }
    public UiKey key() { return content.key(); }
    public UiStyle style() { return content.style(); }
    public UiSizing sizing() { return content.sizing(); }
    public UiDocumentPort readingBody() { return new UiDocumentPort(content,position,selectPosition,true); }
}
