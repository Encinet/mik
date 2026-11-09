package org.encinet.mik.feature.player.profile.nametag;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.ui.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** A field workspace above a UUID-keyed style gallery; the gallery uses the remaining reading height. */
final class NameTagComposition {
    record Row(UUID id, Component effect) { }

    private NameTagComposition() { }

    static UiNode content(UiNode fields, UiNode players, UiNode refresh) {
        var details = Ui.column().key("details").gap(UiLength.ui(12))
                .child(Ui.port("current", UiLayouts.wrap(UiLength.ui(8), UiLength.ui(6))).withSummaryLines(1))
                .child(UiTemplates.information("edit-status").withSummaryLines(1)).build();
        var editor = UiTemplates.focus(Ui.layout(UiLayouts.firstFit(
                        UiLayouts.row(UiLength.ui(12)), UiLayouts.column(UiLength.ui(12))))
                .child(UiKey.named("fields"), fields, new LinearSlot(0, LinearSlot.Alignment.START))
                .child(details, new LinearSlot(0, LinearSlot.Alignment.START)).build(), UiKey.named("fields"));
        var mine = UiTemplates.window("mine", UiTemplates.focus(
                Ui.layout(UiLayouts.contentColumn(UiKey.named("field-editor"), UiLength.ui(12)))
                .child(UiTemplates.heading("heading").withSummaryLines(1))
                .child(UiKey.named("field-editor"), editor, LinearSlot.stretch()).build(), UiKey.named("field-editor")));
        var gallery = UiTemplates.window("players", Ui.layout(UiLayouts.contentColumn(players.key(), UiLength.ui(12)))
                .child(UiTemplates.titleBar(UiTemplates.heading("players-heading").withSummaryLines(1), refresh))
                .child(players).child(UiTemplates.information("gallery-status").withSummaryLines(1)).build());
        var body = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(gallery.key(), UiLength.ui(12)))
                .key("name-tag-workspace").child(mine.key(), mine, LinearSlot.stretch())
                .child(gallery.key(), gallery, LinearSlot.stretch()).build(), UiKey.named("mine"));
        var tools = Ui.layout(UiLayouts.wrap(UiLength.ui(12), UiLength.ui(8)))
                .child(UiTemplates.navigation("edit-actions")).child(UiTemplates.navigation("navigation")).build();
        return UiTemplates.sheet(body, UiTemplates.toolbar("tools", tools));
    }

    static UiNode gallery(List<Row> rows, Component copyHint,
                          Function<UUID, FloatingMenuCommand> copy, int pageIndex,
                          Function<FloatingMenuPage, UiNode> pager, IntConsumer selectPage) {
        var items = new java.util.ArrayList<UiNode>();
        for (Row row : rows) {
            var button = Ui.button(row.effect()).summaryLines(1).key("copy-style")
                    .textWidth(FloatingMenuTextWidth.CONTENT)
                    .tooltip(copyHint)
                    .primary(copy.apply(row.id()));
            items.add(Ui.column().key(UiKey.item(row.id())).child(button.build()).build());
        }
        return Ui.pagedFlow(3).key("player-styles")
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(480,220)))
                .items(items).page(pageIndex, selectPage).pager(pager).build();
    }
}
