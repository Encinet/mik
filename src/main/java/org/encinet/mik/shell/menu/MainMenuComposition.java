package org.encinet.mik.shell.menu;

import org.encinet.mik.shared.menu.ui.*;

/** The launcher owns its reading focus; both companion columns supply independent panels. */
final class MainMenuComposition {
    private MainMenuComposition() { }

    static UiNode content(UiNode left, UiNode right, int page, java.util.function.IntConsumer select) {
        var center = Ui.column().key("launcher-content").gap(UiLength.ui(12))
                .child(UiTemplates.window("hub", UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(UiKey.named("destinations"), UiLength.ui(12)))
                        .child(UiTemplates.title("heading"))
                        .child(UiTemplates.pagedActions("destinations",
                                UiLayouts.balancedGrid(3, 1, UiLayouts.GridBias.TALLER, UiLength.ui(10), UiLength.ui(8)), page, select)
                                .withSizing(UiSizing.width(UiLength.ZERO, UiLength.ui(480))).withCompactModels()).build(), UiKey.named("destinations"))));
        UiNode launcher = UiTemplates.sheet(
                UiTemplates.focus(center.build(), UiKey.named("hub")),
                UiTemplates.toolbar("launcher-tools", UiTemplates.menu(
                        Ui.port("quick", UiLayouts.wrap(UiLength.ui(10), UiLength.ui(8))),
                        UiTemplates.navigation("footer"))));
        return UiTemplates.focus(Ui.layout(new MainMenuLayout()).key("launcher")
                .child(left.key(), left).child(launcher.key(), launcher).child(right.key(), right).build(), launcher.key());
    }
}
