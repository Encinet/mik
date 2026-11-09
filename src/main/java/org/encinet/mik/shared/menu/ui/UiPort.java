package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.List;

/** A named construction-time content port. It is resolved before the scene becomes immutable. */
public record UiPort(UiKey key, UiLayout layout, UiStyle style, UiSizing sizing,
                     List<String> sources, UiRole textRole, LinearSlot itemSlot,int summaryLines) implements UiNode {
    public UiPort {
        key = Objects.requireNonNull(key, "key"); layout = Objects.requireNonNull(layout, "layout");
        style = Objects.requireNonNull(style, "style"); sizing = Objects.requireNonNull(sizing, "sizing");
        sources = List.copyOf(sources); textRole = Objects.requireNonNull(textRole, "textRole");
        itemSlot = Objects.requireNonNull(itemSlot, "itemSlot");
        if (sources.isEmpty()) throw new IllegalArgumentException("Content port must name its source");
        if (summaryLines < 1) throw new IllegalArgumentException("Text ports need a positive line limit");
    }

    public UiPort(UiKey key,UiLayout layout,UiStyle style,UiSizing sizing,List<String> sources,UiRole textRole,LinearSlot itemSlot) {
        this(key,layout,style,sizing,sources,textRole,itemSlot,Integer.MAX_VALUE);
    }

    public UiPort(UiKey key, UiLayout layout, UiStyle style, UiSizing sizing) {
        this(key, layout, style, sizing, List.of(key.value()), UiRole.TEXT);
    }

    public UiPort(UiKey key, UiLayout layout, UiStyle style, UiSizing sizing, List<String> sources, UiRole textRole) {
        this(key, layout, style, sizing, sources, textRole, LinearSlot.CONTENT);
    }

    public UiPort withItemSlot(LinearSlot slot) { return new UiPort(key, layout, style, sizing, sources, textRole, slot,summaryLines); }
    public UiPort withSummaryLines(int lines) { return new UiPort(key,layout,style,sizing,sources,textRole,itemSlot,lines); }
    public UiDocumentPort document(UiReadingPosition position,java.util.function.Consumer<UiReadingPosition> read) {
        return new UiDocumentPort(this,position,read,false);
    }
}
