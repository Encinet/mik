package org.encinet.mik.feature.player.profile.nametag;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.ui.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** One reading workspace, local draft actions and a UUID-keyed three-row style gallery. */
final class NameTagComposition {
    record Row(UUID id, Component effect) { }

    private NameTagComposition() { }

    static UiNode content(UiNode fields, UiNode players, UiNode refresh) {
        var mine = UiTemplates.window("mine", UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(UiKey.named("field-editor"),UiLength.ui(12)))
                .child(UiTemplates.heading("heading").withSummaryLines(1)).child(UiTemplates.information("current").withSummaryLines(1))
                .child(UiKey.named("field-editor"), fields).child(UiTemplates.information("edit-status").withSummaryLines(1)).build(), UiKey.named("field-editor")));
        var gallery = UiTemplates.window("players", Ui.layout(UiLayouts.contentColumn(players.key(),UiLength.ui(12)))
                .child(UiTemplates.titleBar(UiTemplates.heading("players-heading").withSummaryLines(1), refresh)).child(players).child(UiTemplates.information("gallery-status").withSummaryLines(1)).build());
        var body = UiTemplates.focus(Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(16)), UiLayouts.column(UiLength.ui(12))))
                .key("name-tag-workspace").child(mine.key(), mine, new LinearSlot(0,LinearSlot.Alignment.START))
                .child(gallery.key(), gallery,new LinearSlot(0,LinearSlot.Alignment.START)).build(), UiKey.named("mine"));
        return UiTemplates.sheet(body, UiTemplates.toolbar("tools", UiTemplates.menu(
                UiTemplates.navigation("edit-actions"), UiTemplates.navigation("navigation"))));
    }

    static UiNode field(Component effect, UiNode actions) {
        var preview = Ui.text(effect).summaryLines(1).key("effect").textWidth(4096)
                .sizing(UiSizing.atLeast(new UiSize(160, 0)));
        return UiTemplates.focus(UiTemplates.actionRow(preview.build(), actions), UiKey.named("content"));
    }

    static UiNode gallery(List<Row> rows, Component copyHint,
                          Function<UUID, FloatingMenuCommand> copy, int pageIndex,
                          Function<FloatingMenuPage, UiNode> pager, IntConsumer selectPage) {
        var items = new java.util.ArrayList<UiNode>();
        for (Row row : rows) {
            var button = Ui.button(row.effect()).summaryLines(1).key("copy-style")
                    .textWidth(4096)
                    .tooltip(copyHint)
                    .primary(copy.apply(row.id()));
            items.add(Ui.column().key(UiKey.item(row.id())).child(button.build()).build());
        }
        return Ui.pagedFlow(3).key("player-styles")
                .sizing(new UiSizing(UiSize.ZERO, new UiSize(480,220)))
                .items(items).page(pageIndex, selectPage).pager(pager).build();
    }
}
