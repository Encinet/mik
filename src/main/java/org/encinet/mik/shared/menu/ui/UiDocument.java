package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Feature-owned reading position; literal, read-only paragraphs may split while other blocks stay together. */
public record UiDocument(UiKey key, UiNode header, List<UiChild> blocks, UiLength gap,
                         UiReadingPosition position, Consumer<UiReadingPosition> selectPosition,
                         UiStyle style, UiSizing sizing, UiPageControls pager, boolean bodyReading) implements UiNode {
    public static final int MAX_BLOCKS = 256, MAX_TEXT_CODE_POINTS = 65536;

    public UiDocument(UiKey key, UiNode header, List<UiChild> blocks, UiLength gap,
                      UiReadingPosition position, Consumer<UiReadingPosition> selectPosition, UiStyle style, UiSizing sizing) {
        this(key, header, blocks, gap, position, selectPosition, style, sizing, UiTemplates::pager, false);
    }

    public UiDocument {
        Objects.requireNonNull(key); Objects.requireNonNull(header); Objects.requireNonNull(gap);
        Objects.requireNonNull(position); Objects.requireNonNull(selectPosition);
        Objects.requireNonNull(style); Objects.requireNonNull(sizing); Objects.requireNonNull(pager);
        blocks = List.copyOf(blocks);
        if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("Document block budget exceeded");
        if (blocks.stream().anyMatch(block -> block.scope().equals(UiKey.AUTO)))
            throw new IllegalArgumentException("Document blocks need stable paragraph keys");
        new UiContainer(key, UiLayouts.column(gap), blocks, UiStyle.PLAIN, UiSizing.CONTENT);
    }

    public List<UiChild> children() {
        return List.of(UiChild.linear(UiKey.named("header"), header, LinearSlot.CONTENT), UiChild.of(
                new UiContainer(UiKey.named("items"), UiLayouts.column(gap), blocks, UiStyle.PLAIN, UiSizing.CONTENT)));
    }
}
