package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.ui.*;

public final class PlotMenuLayouts {

    private PlotMenuLayouts() {
    }

    public static MenuScene.Builder screen(String id, FloatingMenuAppearance appearance,
                                                        UiNode layout) {
        return MenuScene.screen(id).appearance(appearance)
                .stableAnchor().framing(FloatingMenuFraming.COMFORTABLE)
                .content(Ui.column().key("plot-screen").child(UiKey.named(id),layout).build());
    }

    public static UiNode browser(UiNode entries) {
        return UiTemplates.sheet(UiTemplates.window("browser", UiTemplates.reading(UiKey.named("cards"),
                        UiTemplates.information("context"), UiTemplates.title("heading"), entries)),
                UiTemplates.toolbar("tools", UiTemplates.menu(
                        UiTemplates.actions("actions", 3), UiTemplates.navigation("navigation"))));
    }

    public static UiNode directory(UiNode entries) {
        return UiTemplates.sheet(UiTemplates.window("directory", UiTemplates.reading(UiKey.named("cards"),
                        UiTemplates.title("heading"), UiTemplates.list("here"),
                        UiTemplates.actions("filters", 2), UiTemplates.navigation("search"),
                        entries)),
                UiTemplates.toolbar("tools", UiTemplates.menu(
                        UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
    }

    public static UiNode workspace(UiReadingPosition position,java.util.function.Consumer<UiReadingPosition> read) {
        return UiTemplates.sheet(UiTemplates.window("plot", UiTemplates.reading(UiKey.named("summary"),
                        UiTemplates.navigation("context").withSummaryLines(1), UiTemplates.title("heading").withSummaryLines(1),
                        UiTemplates.information("summary").document(position,read).readingBody(), UiTemplates.information("notice").withSummaryLines(2),
                        UiTemplates.cards("tasks", 3, 2).withSummaryLines(1))),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation")));
    }

    public static UiNode settings(boolean general,int page,java.util.function.IntConsumer read) {
        var settings = new UiPagedPort(UiTemplates.actions("settings",2).withSummaryLines(1),1,page,read);
        UiNode ownership = general ? Ui.port("ownership",UiLayouts.wrap(UiLength.ui(10),UiLength.ui(8))).withSummaryLines(1)
                : new UiPagedPort(UiTemplates.actions("ownership",2).withSummaryLines(1),1,page,read);
        var context = UiTemplates.actionRow(UiTemplates.information("context").withSummaryLines(1),
                UiTemplates.information("identifier").withSummaryLines(1));
        var owner = UiTemplates.actionRow(UiTemplates.heading("ownership-heading"),UiTemplates.information("owner").withSummaryLines(1));
        return UiTemplates.sheet(UiTemplates.window("settings",UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(general ? settings.key() : ownership.key(),UiLength.ui(12)))
                .child(UiKey.named("context-summary"),context).child(UiTemplates.title("heading").withSummaryLines(1))
                .child(settings).child(UiKey.named("ownership-summary"),owner).child(ownership).build(),general ? settings.key() : ownership.key())),
                UiTemplates.toolbar("tools",UiTemplates.navigation("navigation")));
    }

    public static UiNode members(UiNode members) {
        UiNode directory = UiTemplates.workspace(UiTemplates.menu(UiTemplates.heading("groups-heading").withSummaryLines(1),UiTemplates.list("groups").withSummaryLines(1)),
                UiTemplates.reading(members.key(), UiTemplates.heading("members-heading").withSummaryLines(1),UiTemplates.information("hint").withSummaryLines(1),members,
                        UiTemplates.actions("actions",1)));
        return UiTemplates.sheet(UiTemplates.window("access", UiTemplates.reading(directory.key(),
                        UiTemplates.information("context").withSummaryLines(1), UiTemplates.title("heading").withSummaryLines(1),
                        directory)),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation")));
    }

    public static UiNode destinationList(UiNode rows) {
        return UiTemplates.sheet(UiTemplates.window("destinations", UiTemplates.reading(rows.key(),
                        UiTemplates.information("context"), UiTemplates.title("heading"),rows)),
                UiTemplates.toolbar("tools", UiTemplates.menu(UiTemplates.actions("actions", 1), UiTemplates.navigation("navigation"))));
    }

    public static UiNode destinationTitle(UiNode fields) {
        return UiTemplates.sheet(UiTemplates.window("destination-title", UiTemplates.reading(fields.key(),
                        UiTemplates.information("context"), UiTemplates.title("heading"),
                        UiTemplates.information("hint"), fields)),
                UiTemplates.toolbar("tools", UiTemplates.menu(UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
    }

    public static UiNode choices(int page, java.util.function.IntConsumer read) {
        var choices = new UiPagedPort(UiTemplates.actions("rows", 2).withSummaryLines(2), 1, page, read)
                .withSizing(new UiSizing(UiSize.ZERO, new UiSize(560, 220)));
        return UiTemplates.sheet(UiTemplates.window("choices", UiTemplates.reading(choices.key(),
                        UiTemplates.information("context").withSummaryLines(1), UiTemplates.title("heading").withSummaryLines(1),
                        UiTemplates.information("subject").withSummaryLines(1), choices)),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation")));
    }

    public static UiNode permissionTable(Plot plot, PlotPermission.Category category,int page,java.util.function.IntConsumer read) {
        return PlotPermissionComposition.create(plot, category,page,read);
    }

    public static UiNode help(UiNode instructions) {
        return UiTemplates.sheet(UiTemplates.window("reading", UiTemplates.reading(instructions.key(),
                        UiTemplates.title("heading").withSummaryLines(1), instructions)),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation")));
    }

    public static UiNode noticeBoard(UiNode reading) {
        return UiTemplates.sheet(UiTemplates.window("reading", reading),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation")));
    }

    public static UiNode atmosphere(int page, java.util.function.IntConsumer read) {
        var options = new UiPagedPort(UiTemplates.actions("options", 3).withSummaryLines(2), 1, page, read)
                .withSizing(new UiSizing(UiSize.ZERO, new UiSize(560, 220)));
        return UiTemplates.sheet(UiTemplates.window("atmosphere", UiTemplates.reading(options.key(),
                        UiTemplates.information("context").withSummaryLines(1), UiTemplates.title("heading").withSummaryLines(1),
                        UiTemplates.actions("sections", 2).withSummaryLines(1), options)),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation")));
    }

    public static UiNode member() {
        return member(UiTemplates.list("identity"));
    }

    public static UiNode member(UiNode identity) {
        return UiTemplates.sheet(UiTemplates.window("member", UiTemplates.reading(identity.key(),
                        UiTemplates.information("context"), UiTemplates.title("heading"), identity)),
                UiTemplates.toolbar("tools", UiTemplates.menu(UiTemplates.actions("actions", 2), UiTemplates.navigation("navigation"))));
    }

}
