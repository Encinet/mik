package org.encinet.mik.feature.world.plot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** The plot owns both its compact excerpt and the complete reading content. */
final class PlotNoticeContent {
    private PlotNoticeContent() { }

    static UiKey source(java.util.UUID plotId) { return UiKey.named("notice-read:" + plotId); }

    static UiNode preview(PlotNoticeBoard notice, Component readFull, UiKey source, FloatingMenuCommand open) {
        if (notice == null || notice.body().isBlank()) return Ui.column().build();
        return Ui.column().gap(UiLength.ui(6))
                .child(heading(notice.name(), true), LinearSlot.stretch())
                .child(Ui.excerpt(body(notice.body(), 112, 8), Ui.button(readFull).key(source)
                        .role(UiRole.NAVIGATION).primary(open).build()), LinearSlot.stretch()).build();
    }

    static UiNode reading(PlotNoticeBoard notice, UiReadingPosition position, java.util.function.Consumer<UiReadingPosition> select) {
        var document = Ui.document().key(UiKey.item("notice:" + notice.plotId()))
                .blocks(java.util.List.of(body(notice.body(), FloatingMenuTextWidth.WIDE.resolve(160), Integer.MAX_VALUE)))
                .position(position, select).build();
        return UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(document.key(),UiLength.ui(6))).key("notice-reading")
                .child(heading(notice.name(), false))
                .child(document).build(), document.key());
    }

    private static UiElement heading(String name, boolean compact) {
        var heading = Ui.heading(Component.text(name, NamedTextColor.GOLD)).key("plot-name")
                .alignment(FloatingMenuDecoration.Alignment.LEFT);
        return (compact ? heading.singleLine() : heading).build();
    }

    private static UiElement body(String text, int width, int lines) {
        return Ui.text(Component.text(text, NamedTextColor.WHITE)).key("notice-body").textWidth(width).summaryLines(lines)
                .alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
}
