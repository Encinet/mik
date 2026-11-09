package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.menu.ui.*;

/** Feature-owned permission content arranged by the shared, measured table layout. */
final class PlotPermissionComposition {
    private PlotPermissionComposition() { }

    static UiNode create(Plot plot, PlotPermission.Category category,int page,java.util.function.IntConsumer read) {
        UiNode table = table(plot,category).paged(page,read);
        return UiTemplates.workspace(UiTemplates.menu(UiTemplates.information("context").withSummaryLines(1), UiTemplates.title("heading").withSummaryLines(1),
                        UiTemplates.information("subject").withSummaryLines(1), UiTemplates.list("categories").withSummaryLines(1)),
                UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(UiKey.named("permissions"),UiLength.ui(12)))
                        .child(UiTemplates.heading("section")).child(table).child(UiTemplates.information("hint")).build(),UiKey.named("permissions")),
                UiTemplates.navigation("navigation"));
    }

    private static UiTable<PlotPermission> table(Plot plot,PlotPermission.Category category) {
        var table = Ui.<PlotPermission>table().key(UiKey.named("permissions"))
                .column(new UiTable.Column<>(UiKey.named("operation"), UiTemplates.heading("operation-heading"),
                        UiTrack.content(UiLength.ui(60), UiLength.ui(144)), permission ->
                        UiTemplates.information("permission-" + permission.key() + "-group-heading").withSummaryLines(1)))
                .column(new UiTable.Column<>(UiKey.named("result"), UiTemplates.heading("result-heading"),
                        UiTrack.fraction(1), permission -> UiTemplates.information("permission-" + permission.key()).withSummaryLines(1)))
                .rows(category.permissions().stream().filter(permission -> permission.appliesTo(plot)).toList(),
                        permission -> UiKey.named(permission.key()))
                .gaps(UiLength.ui(16), UiLength.ui(8));
        return table;
    }
}
