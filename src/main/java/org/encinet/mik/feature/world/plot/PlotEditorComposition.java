package org.encinet.mik.feature.world.plot;

import java.util.List;
import org.encinet.mik.shared.menu.ui.*;

/** The map owns the reading focus; corner tools and the rectangle inspector stay beside it. */
final class PlotEditorComposition {
    static final UiSize VIEWPORT = new UiSize(960, 2048);

    private PlotEditorComposition() { }

    static UiNode create(UiNode map, UiNode regions) {
        UiNode selection = UiTemplates.sidebar("selection-tools", UiTemplates.reading(regions.key(),
                UiTemplates.list("corners").withSummaryLines(1),
                UiTemplates.actions("points", 2).withSummaryLines(1), UiTemplates.actions("composition", 2).withSummaryLines(1),
                UiTemplates.heading("region-heading").withSummaryLines(1), regions));
        UiNode preview = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(map.key(),UiLength.ui(8))).key("preview")
                .child(map)
                .child(UiTemplates.toolbar("view-controls", UiTemplates.menu(UiTemplates.actions("view", 3).withSummaryLines(1),
                        UiTemplates.actions("pan", 4).withSummaryLines(1))))
                .child(UiTemplates.information("legend").withSummaryLines(1)).build(), map.key());
        UiNode region = UiTemplates.sidebar("region-tools", UiTemplates.menu(
                UiTemplates.information("region-selection").withSummaryLines(1), UiTemplates.list("region-actions").withSummaryLines(1)));
        UiNode workspace = UiTemplates.focus(new UiContainer(UiKey.named("workspace"),
                UiLayouts.firstFit(UiLayouts.row(UiLength.ui(16)), UiLayouts.column(UiLength.ui(12))),
                List.of(UiChild.linear(selection.key(), selection, new LinearSlot(0, LinearSlot.Alignment.START)),
                        UiChild.linear(preview.key(), preview, LinearSlot.CONTENT),
                        UiChild.linear(region.key(), region, new LinearSlot(0, LinearSlot.Alignment.START))),
                UiStyle.PLAIN, UiSizing.CONTENT), preview.key());
        UiNode body = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(workspace.key(),UiLength.ui(12))).key("editor")
                .child(UiTemplates.window("context", Ui.column().gap(UiLength.ui(8))
                        .child(UiKey.named("heading-row"),UiTemplates.actionRow(UiTemplates.title("heading").withSummaryLines(1),UiTemplates.information("context").withSummaryLines(1)))
                        .child(UiKey.named("selection-row"),UiTemplates.actionRow(UiTemplates.information("selection").withSummaryLines(1),UiTemplates.information("hint").withSummaryLines(1))).build()))
                .child(workspace).build(), workspace.key());
        return UiTemplates.sheet(body, UiTemplates.toolbar("commit", UiTemplates.menu(
                UiTemplates.list("commit").withSummaryLines(1), Ui.layout(UiLayouts.wrap(UiLength.ui(12), UiLength.ui(8)))
                        .child(UiTemplates.navigation("history").withSummaryLines(1)).child(UiTemplates.navigation("navigation").withSummaryLines(1)).build())));
    }
}
