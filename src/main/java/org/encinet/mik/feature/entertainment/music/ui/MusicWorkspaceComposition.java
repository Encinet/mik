package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;

/** One library or active task surface, with navigation and a persistent transport. */
final class MusicWorkspaceComposition {
    static final UiSize VIEWPORT = new UiSize(1280, 2048);

    private MusicWorkspaceComposition() { }

    /** A task owns its input namespace independently of the workbench navigation identity. */
    static UiNode task(java.util.UUID scope, UiNode content) {
        return Ui.column().child(UiKey.named("task:" + scope), content).build();
    }

    static UiNode deviceLocation(Component position, Component coordinates, UiNode visit) {
        var row = Ui.row().key("device-location").gap(UiLength.ui(8))
                .child(Ui.text(position).key("position").noWrap().tooltip(coordinates)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).build());
        if (visit != null) row.child(visit);
        return row.build();
    }

    static UiNode panelHeading(Component title, UiNode close, UiNode... actions) {
        var header = Ui.column().key("panel-heading").gap(UiLength.ui(6))
                .child(UiTemplates.titleBar(Ui.heading(title).key("title").summaryLines(1).textWidth(320).build(),
                        Ui.row().child(close).build()),
                        LinearSlot.stretch());
        if (actions.length > 0) header.child(MusicWorkspace.tools("panel-actions").children(List.of(actions)).build());
        return header.build();
    }

    /** The query and its local actions stay compact and centered independently of the results' width. */
    static Ui.LinearBuilder collection(UiNode heading, UiNode description, UiNode search) {
        var body = Ui.column().key("collection").gap(UiLength.ui(8)).child(heading);
        if (description != null) body.child(description);
        return body.child(UiKey.named("search"), search, LinearSlot.fit());
    }

    static UiNode content(UiNode navigation, UiNode browser, UiNode auxiliary, UiNode transport, UiNode windowTools) {
        UiNode reading = auxiliary == null ? UiTemplates.window("browser",browser)
                : UiTemplates.window("auxiliary",auxiliary);
        var workbench = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(UiKey.named("reading"),UiLength.ui(12))).key("workbench")
                        .child(UiKey.named("reading"), reading)
                        .child(UiTemplates.toolbar("transport", transport)).child(windowTools).build(), UiKey.named("reading"));
        var workspace = navigation == null ? workbench : (UiContainer) UiTemplates.split(UiTemplates.sidebar("collections", navigation),workbench);
        return new UiContainer(UiKey.named("music-workspace"),workspace.layout(),workspace.children(),workspace.style(),workspace.sizing());
    }
}
